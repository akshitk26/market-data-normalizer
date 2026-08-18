# Market Data Normalizer

Market Data Normalizer is a Java-first project that accepts different exchange feed formats and turns them into one protobuf Level 2 event model.

It currently supports:

- Coinbase public WebSocket JSON for live L2 capture
- Gemini FIX market-data examples and a provisioned live FIX session
- Nasdaq TotalView-ITCH 5.0 binary sample windows from Nasdaq's public sample directory
- ZeroMQ transport scaffolding for publishing normalized protobuf events
- C++20 native scaffolding for future measured latency bottlenecks

The project is intentionally built in small, testable slices. The replay ring buffer and replay recovery are present as isolated core components, but are not connected to the feed ingestion paths yet.

## What It Does

Each source adapter follows the same flow:

```text
raw exchange message -> source parser -> MarketDataEnvelope protobuf
                    -> shared processing and verification -> optional ZeroMQ publication
```

The source-specific details stay at the edge. Coinbase JSON, Gemini FIX, and Nasdaq ITCH all become the same `MarketDataEnvelope` type before downstream code sees them.

## Repository Layout

| Path | Purpose |
| --- | --- |
| `proto` | Shared protobuf schema and generated Java classes |
| `java/normalizer-core` | Parser contracts, gap detector, replay-buffer components |
| `java/feed-sources` | Coinbase, Gemini FIX, and Nasdaq ITCH adapters |
| `java/ingestion-service` | Runnable capture and processing commands |
| `java/transport-zmq` | JeroMQ publisher/subscriber boundary |
| `java/book-verifier` | Downstream L2 book verification components |
| `java/benchmark` | JMH benchmark module |
| `native` | Optional C++20 native acceleration area |
| `docs` | Architecture, source decisions, and operating guide |
| `config` | Safe configuration templates only; no secrets |

## Requirements

- Java 11
- Gradle Wrapper, included in the repository
- CMake and a C++20 compiler for the optional native module
- Network access for Coinbase, Gemini documentation, or Nasdaq public samples

All project dependencies are free/open-source. No paid service is required for the Coinbase path, Gemini official-example path, or Nasdaq public sample-window path.

## Build And Test

From the repository root:

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-11.jdk/Contents/Home \
GRADLE_USER_HOME=.gradle-home ./gradlew --no-daemon test
```

The command runs all Java module tests, including parser, binary framing, FIX codec, ITCH lifecycle, and core replay-buffer tests.

## Coinbase Live JSON

Coinbase's public Exchange WebSocket does not require an API key for this market-data channel.

```bash
./gradlew --no-daemon :ingestion-service:captureCoinbaseLevel2 \
  --args="BTC-USD,ETH-USD,SOL-USD,LTC-USD 12 data/websocket/coinbase-level2-live-sample.jsonl"

./gradlew --no-daemon :ingestion-service:processCoinbaseLevel2File \
  --args="data/websocket/coinbase-level2-live-sample.jsonl"
```

## Gemini FIX

The parser accepts standard SOH-delimited FIX and the pipe-delimited representation used in Gemini documentation. It supports `35=W` snapshots, `35=X` incremental updates, bid/offer/trade entries, add/modify/delete actions, sequence numbers, timestamps, and maker-side trade fields.

Download official Gemini-published examples:

```bash
./gradlew --no-daemon :ingestion-service:captureGeminiFixExamples \
  --args="data/fix/gemini-official-market-data.jsonl"

./gradlew --no-daemon :ingestion-service:processGeminiFixFile \
  --args="data/fix/gemini-official-market-data.jsonl"
```

For a provisioned live Gemini FIX session:

```bash
set -a
source /path/to/your/local/gemini-fix.env
set +a
./gradlew --no-daemon :ingestion-service:captureGeminiFixLive
```

Use [config/gemini-fix.env.example](config/gemini-fix.env.example) and [docs/gemini-fix-live-setup.md](docs/gemini-fix-live-setup.md). Gemini must provide the private host, port, CompIDs, network access, and session rules. Do not commit the local environment file, sequence state, certificates, or captures.

The live adapter is market-data-only. It does not send order-entry messages. The capture is line-oriented raw FIX with pipe delimiters despite the historical `.jsonl` filename; it is not an API-key-authenticated REST flow.

## Local FIX Bridge With Real Coinbase Data

The local bridge is a development-only FIX acceptor. It listens on localhost, accepts the same Logon and Market Data Request flow as the FIX client, then reads real Coinbase public WebSocket messages and translates them into Gemini-shaped FIX `W` and `X` messages. No prices or sizes are generated synthetically, but this is not an actual Gemini FIX connection.

Run the bridge in one terminal:

```bash
./gradlew --no-daemon :ingestion-service:runLocalFixBridge \
  --args="9876 BTC-USD,ETH-USD 100"
```

Run the existing FIX client in a second terminal:

```bash
GEMINI_FIX_HOST=127.0.0.1 \
GEMINI_FIX_PORT=9876 \
GEMINI_FIX_SENDER_COMP_ID=LOCAL-CLIENT \
GEMINI_FIX_TARGET_COMP_ID=GEMINI \
GEMINI_FIX_TRANSPORT_TLS=false \
GEMINI_FIX_RESET_SEQUENCE_ON_LOGON=true \
GEMINI_FIX_SYMBOLS=BTCUSD,ETHUSD \
GEMINI_FIX_MARKET_DEPTH=1 \
GEMINI_FIX_ENTRY_TYPES=0,1 \
GEMINI_FIX_OUTPUT=data/fix/local-bridge-market-data.jsonl \
GEMINI_FIX_SEQUENCE_FILE=data/fix/local-bridge-sequence.properties \
GEMINI_FIX_MAX_MESSAGES=100 \
./gradlew --no-daemon :ingestion-service:captureGeminiFixLive
```

The client receives local FIX messages, runs the existing FIX parser, and writes the capture. The bridge exits after its configured message count or when the client disconnects.

## Nasdaq ITCH 5.0

The ITCH adapter uses the official Nasdaq binary-file framing: a two-byte big-endian message length followed by the message payload. It parses stock directory metadata, add orders, attributed adds, executions, execute-with-price, cancels, deletes, replaces, and trade messages. Quantities are normalized to the schema's integer nanos convention, just like the decimal-sized Coinbase and Gemini feeds.

The capture command discovers official Nasdaq v5.0 files, chooses one using a seed, streams the gzip from the beginning, skips the administrative preamble, selects a real message window, and writes only the small length-prefixed capture locally:

```bash
./gradlew --no-daemon :ingestion-service:captureNasdaqItchWindow \
  --args="data/itch/nasdaq-itch-window.bin 500 1000 12345"

./gradlew --no-daemon :ingestion-service:processNasdaqItchFile \
  --args="data/itch/nasdaq-itch-window.bin 2021-07-13"
```

Arguments are output path, message count, maximum random skip, and optional seed. Omit the seed for a different run each time. The window contains real Nasdaq messages, is not synthetic, and is not committed to Git. An arbitrary window is suitable for decoding and lifecycle tests; full book reconstruction requires replaying from session start.

Source references: [Nasdaq ITCH FAQ](https://www.nasdaqtrader.com/Content/TechnicalSupport/FAQs/ITCH_FAQ.pdf), [public ITCH sample directory](https://emi.nasdaq.com/ITCH/Nasdaq%20ITCH/), [TotalView-ITCH specification](https://www.nasdaqtrader.com/content/technicalsupport/specifications/dataproducts/NQTVITCHSpecification.pdf), and [BinaryFILE specification](https://nasdaqtrader.com/content/technicalSupport/specifications/dataproducts/binaryfile.pdf).

## Performance

```bash
./gradlew --no-daemon :benchmark:jmh
```

To measure the single-worker CPU pipeline using a real Coinbase capture, see the benchmark commands in the [operator guide](docs/operator-guide.md). A result such as `280K+ messages/sec on one core` means one processing worker completed more than 280,000 raw-message parse/translation operations per second under the documented benchmark conditions; it does not mean the public WebSocket delivered 280,000 messages per second.

The benchmark is a measurement tool, not a claim that the final target has already been reached. Record the machine, Java version, input, settings, throughput, and whether parsing, protobuf creation, transport, and verification were included.

## Security And Public Repository Rules

- Never commit `.env` files, API keys, FIX certificates, session passwords, sequence state, packet captures, or market-data archives.
- Only placeholder values belong in `config/*.example` files.
- Large source data stays local under ignored `data/` paths.
- The live Gemini FIX adapter reads configuration from environment variables and never requires secrets in source code.

## More Documentation

- [Architecture](docs/architecture.md)
- [Data-source decisions](docs/data-sources.md)
- [Gemini FIX live setup](docs/gemini-fix-live-setup.md)
- [Gemini FIX onboarding request](docs/gemini-fix-onboarding-request.md)
- [Operator and developer guide](docs/operator-guide.md)
