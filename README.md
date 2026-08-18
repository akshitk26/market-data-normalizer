# Market Data Normalizer

Java-first market data ingestion and normalization project.

The project reads market data from different feed formats and converts it into one protobuf Level 2 event model.

## Current Capabilities

- Coinbase public WebSocket JSON capture for BTC, ETH, SOL, and LTC pairs
- Gemini FIX 4.4 market-data parser and official-example downloader
- Gemini FIX session client for a provisioned endpoint
- Local FIX bridge backed by real Coinbase WebSocket data
- Nasdaq TotalView-ITCH 5.0 binary parser
- Real Nasdaq sample-window downloader
- Sequence-gap detection and first-version replay coordinator
- ZeroMQ publisher/subscriber classes
- Optional C++20 native modules

Replay recovery is not connected to all feed commands yet. ZeroMQ publication is also not connected to the capture commands yet.

## Flow

```text
source data -> source parser -> MarketDataEnvelope protobuf
            -> replay/gap handling -> optional ZeroMQ transport
```

Coinbase JSON, Gemini FIX, and Nasdaq ITCH are converted to `MarketDataEnvelope` before shared processing.

## Modules

| Path | Purpose |
| --- | --- |
| `proto` | Protobuf schema |
| `java/normalizer-core` | Parser contracts, gap detection, replay buffer |
| `java/feed-sources` | Coinbase, Gemini FIX, and Nasdaq ITCH adapters |
| `java/ingestion-service` | Capture, processing, bridge, and benchmark commands |
| `java/transport-zmq` | ZeroMQ publisher and subscriber |
| `java/book-verifier` | Book verification placeholder |
| `java/benchmark` | JMH benchmarks |
| `native` | Optional C++20 modules |
| `docs` | Architecture and operating instructions |
| `config` | Safe configuration examples |

## Requirements

- Java 11
- Gradle Wrapper
- CMake and a C++20 compiler for native builds
- Network access for live or public sample sources

All project dependencies are free and open source.

## Build And Test

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-11.jdk/Contents/Home \
GRADLE_USER_HOME=.gradle-home ./gradlew --no-daemon test

cmake -S native -B native/build
cmake --build native/build
```

## Coinbase WebSocket

Capture live public data:

```bash
./gradlew --no-daemon :ingestion-service:captureCoinbaseLevel2 \
  --args="BTC-USD,ETH-USD,SOL-USD,LTC-USD 12 data/websocket/coinbase-live.jsonl"
```

Process a saved capture:

```bash
./gradlew --no-daemon :ingestion-service:processCoinbaseLevel2File \
  --args="data/websocket/coinbase-live.jsonl"
```

## Local FIX Bridge

The local bridge listens on localhost and accepts the existing FIX client. It reads real Coinbase WebSocket data and sends it as Gemini-shaped FIX `W` and `X` messages.

It is useful for testing the FIX session and parser without a Gemini provisioned session. It is not a Gemini connection.

Terminal 1:

```bash
./gradlew --no-daemon :ingestion-service:runLocalFixBridge \
  --args="9876 BTC-USD,ETH-USD 100"
```

Terminal 2:

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

The client receives local FIX messages, runs the FIX parser, and writes the capture.

## Gemini FIX

Download official FIX examples:

```bash
./gradlew --no-daemon :ingestion-service:captureGeminiFixExamples \
  --args="data/fix/gemini-official-market-data.jsonl"

./gradlew --no-daemon :ingestion-service:processGeminiFixFile \
  --args="data/fix/gemini-official-market-data.jsonl"
```

For a real Gemini FIX session, Gemini must provide the host, port, CompIDs, network access, and sequence rules. Use the [onboarding request](docs/gemini-fix-onboarding-request.md) and [live setup guide](docs/gemini-fix-live-setup.md).

Keep local FIX settings in an ignored environment file. Do not commit credentials, certificates, sequence state, or captures.

## Nasdaq ITCH

Capture a seeded window from a real public Nasdaq sample file:

```bash
./gradlew --no-daemon :ingestion-service:captureNasdaqItchWindow \
  --args="data/itch/nasdaq-itch-window.bin 500 1000 12345"
```

Process the captured binary window:

```bash
./gradlew --no-daemon :ingestion-service:processNasdaqItchFile \
  --args="data/itch/nasdaq-itch-window.bin 2021-07-13"
```

The downloader reads real Nasdaq TotalView-ITCH v5.0 files and stores only a small local window. It does not generate ITCH messages.

## Replay

`RingReplayBuffer` stores recent normalized events. `ReplayCoordinator` tracks sequences by source, detects gaps, filters replay requests by instrument, and reports whether the requested range is complete.

The coordinator is tested but is not yet connected to every live capture path or the ZeroMQ transport.

## Performance

Coming soon.

## Documentation

- [Architecture](docs/architecture.md)
- [Data sources](docs/data-sources.md)
- [Operator guide](docs/operator-guide.md)
- [Gemini FIX setup](docs/gemini-fix-live-setup.md)
- [Gemini FIX onboarding request](docs/gemini-fix-onboarding-request.md)
