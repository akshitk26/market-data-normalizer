# Operator And Developer Guide

## 1. Build Model

The project is Java-first. Java owns orchestration, source adapters, parsing, protobuf normalization, tests, verification, and benchmarks. C++ is kept for future hot paths only after measurement proves Java is insufficient.

The modules build from the bottom up:

1. `proto` generates the shared Java protobuf classes.
2. `normalizer-core` defines parser contracts and core recovery primitives.
3. `feed-sources` parses Coinbase, Gemini FIX, and Nasdaq ITCH inputs.
4. `transport-zmq` provides the normalized-event transport boundary.
5. `ingestion-service` exposes runnable commands.
6. `book-verifier` and `benchmark` consume the shared model.

## 2. Protobuf Model

`proto/src/main/proto/marketdata.proto` defines `MarketDataEnvelope`.

Every normalized event includes source feed, instrument, sequence number when available or assigned by the adapter, event time when supplied, receive time, and one payload. `L2Update` contains side, action, price, quantity, level, and optional order ID. Prices and quantities are integer nanos to avoid floating-point parsing downstream.

## 3. Build And Tests

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-11.jdk/Contents/Home \
GRADLE_USER_HOME=.gradle-home ./gradlew --no-daemon test

./gradlew --no-daemon :feed-sources:test
./gradlew --no-daemon :normalizer-core:test
```

Native build:

```bash
cmake -S native -B native/build
cmake --build native/build
```

## 4. Coinbase Workflow

Purpose: verify a live public WebSocket capture, JSON parsing, and protobuf normalization.

```bash
./gradlew --no-daemon :ingestion-service:captureCoinbaseLevel2 \
  --args="BTC-USD,ETH-USD,SOL-USD,LTC-USD 12 data/websocket/coinbase-level2-live-sample.jsonl"

./gradlew --no-daemon :ingestion-service:processCoinbaseLevel2File \
  --args="data/websocket/coinbase-level2-live-sample.jsonl"
```

The first command connects to Coinbase, writes raw JSON lines, and immediately processes them. The second verifies that the saved capture is independently replayable through the parser.

## 5. Gemini FIX Workflow

### Official examples

This path downloads raw messages published by Gemini’s documentation. It works without credentials and exercises the parser against real FIX examples.

```bash
./gradlew --no-daemon :ingestion-service:captureGeminiFixExamples \
  --args="data/fix/gemini-official-market-data.jsonl"

./gradlew --no-daemon :ingestion-service:processGeminiFixFile \
  --args="data/fix/gemini-official-market-data.jsonl"
```

### Live session

Gemini must provision a FIX market-data session first. The local environment should contain values like:

```bash
GEMINI_FIX_HOST=provided-by-gemini
GEMINI_FIX_PORT=provided-by-gemini
GEMINI_FIX_SENDER_COMP_ID=provided-by-gemini
GEMINI_FIX_TARGET_COMP_ID=GEMINI
GEMINI_FIX_TRANSPORT_TLS=true
GEMINI_FIX_RESET_SEQUENCE_ON_LOGON=false
GEMINI_FIX_SYMBOLS=BTCUSD,ETHUSD,ETHBTC
GEMINI_FIX_MARKET_DEPTH=1
GEMINI_FIX_ENTRY_TYPES=0,1,2
GEMINI_FIX_OUTPUT=data/fix/gemini-live-market-data.jsonl
GEMINI_FIX_SEQUENCE_FILE=data/fix/gemini-fix-sequence.properties
GEMINI_FIX_MAX_MESSAGES=1000
```

Run:

```bash
set -a
source /path/to/your/local/gemini-fix.env
set +a
./gradlew --no-daemon :ingestion-service:captureGeminiFixLive
```

The session sends Logon, waits for acknowledgment, sends a market-data request, handles heartbeats and test requests, requests missing FIX sequence ranges, captures `W` and `X` messages, and processes the capture through the existing parser. It never sends order-entry messages. TLS is the default; use plaintext only when Gemini explicitly provisions it.

## 6. Local FIX Bridge

The local bridge is a development-only FIX acceptor. It behaves like the remote session boundary, but its prices and quantities come from the real Coinbase public WebSocket. It does not fabricate market events and it does not represent a real Gemini network connection.

Start the bridge:

```bash
./gradlew --no-daemon :ingestion-service:runLocalFixBridge \
  --args="9876 BTC-USD,ETH-USD 100"
```

In another terminal, point the existing FIX initiator at localhost:

```bash
GEMINI_FIX_HOST=127.0.0.1 GEMINI_FIX_PORT=9876 \
GEMINI_FIX_SENDER_COMP_ID=LOCAL-CLIENT GEMINI_FIX_TARGET_COMP_ID=GEMINI \
GEMINI_FIX_TRANSPORT_TLS=false GEMINI_FIX_RESET_SEQUENCE_ON_LOGON=true \
GEMINI_FIX_SYMBOLS=BTCUSD,ETHUSD GEMINI_FIX_MARKET_DEPTH=1 \
GEMINI_FIX_ENTRY_TYPES=0,1 GEMINI_FIX_MAX_MESSAGES=100 \
GEMINI_FIX_OUTPUT=data/fix/local-bridge-market-data.jsonl \
GEMINI_FIX_SEQUENCE_FILE=data/fix/local-bridge-sequence.properties \
./gradlew --no-daemon :ingestion-service:captureGeminiFixLive
```

The bridge accepts Logon, sends a Logon acknowledgment, waits for `35=V`, opens Coinbase, and emits translated `35=W` snapshots followed by `35=X` updates. The existing client then parses and normalizes those messages exactly as it would for the Gemini adapter.

## 7. Nasdaq ITCH Workflow

Purpose: exercise a real binary feed handler without paying for a live TotalView connection or committing multi-gigabyte files.

```bash
./gradlew --no-daemon :ingestion-service:captureNasdaqItchWindow \
  --args="data/itch/nasdaq-itch-window.bin 500 1000 12345"

./gradlew --no-daemon :ingestion-service:processNasdaqItchFile \
  --args="data/itch/nasdaq-itch-window.bin 2021-07-13"
```

The capture command discovers the official Nasdaq directory, selects a real v5.0 file with the seed, streams gzip from the beginning, skips the administrative preamble, skips a seeded number of real messages, and writes a small original-format binary window.

The parser maintains order state for the captured window and maps add, execution, cancel, delete, replace, and trade messages into normalized events. A random mid-session window may contain executions whose add happened before the window; those messages are correctly ignored when state is unavailable. Full book reconstruction requires capturing from session start through the target point. Unsupported valid ITCH message types are skipped as non-L2 events.

## 8. ZeroMQ

`transport-zmq` contains a publisher and subscriber for protobuf byte arrays. The intended flow is:

```text
source adapter -> protobuf event -> publisher -> subscriber/book verifier
```

The current source commands print statistics and write captures. Publishing each normalized event is the next transport integration step.

## 9. Replay Buffer Status

`RingReplayBuffer` and `GapDetector` have isolated tests. They are not connected to Coinbase, Gemini FIX, or ITCH yet. This is deliberate: replay behavior should be integrated after all three source families share the same normalized-event path.

## 10. Performance

```bash
./gradlew --no-daemon :benchmark:jmh
```

For the local FIX bridge pipeline, first create a real Coinbase capture:

```bash
./gradlew --no-daemon :ingestion-service:captureCoinbaseLevel2 \
  --args="BTC-USD,ETH-USD,SOL-USD,LTC-USD 500 data/websocket/coinbase-benchmark.jsonl"
```

Then measure one Java worker translating the captured Coinbase JSON into FIX and parsing it back into normalized protobuf events:

```bash
./gradlew --no-daemon :ingestion-service:benchmarkLocalFixPipeline \
  --args="data/websocket/coinbase-benchmark.jsonl 10"
```

This is the useful comparison for a claim such as `280K+ messages/sec on one core`: one worker repeatedly processes real captured messages and reports `raw_messages_per_second` and `normalized_events_per_second`. It measures CPU-side parsing and translation, not Internet speed. The live bridge should be measured separately because WebSocket timing, network latency, and exchange batching become part of that result. On Linux, use `taskset -c 0` if strict OS-level CPU affinity is required; on macOS, the application still uses one processing worker, but the operating system may schedule JVM support threads on other cores.

Record machine/CPU, Java version, input size/source, warmup and measurement settings, throughput, allocations if measured, and whether parsing, protobuf creation, transport, and verification were included. A raw parser benchmark and an end-to-end network benchmark are different measurements.

## 11. Troubleshooting

`JAVA_HOME` errors: verify Java 11 with `/usr/libexec/java_home -V`.

Coinbase returns no normalized events: check product IDs, network access, and whether only subscription acknowledgments were captured.

Gemini FIX disconnects immediately: verify source IP, host/port, CompIDs, transport mode, and session schedule with Gemini. Do not guess private endpoint values.

ITCH capture produces only administrative messages: increase the random skip limit. A zero-event administrative window is valid but not useful for parser smoke testing.

ITCH execution produces no event: its corresponding add was outside the window. Use a session-start capture when testing book state.

## 12. Public Repository Checklist

- Run the full test suite.
- Remove local captures unless intentionally distributed.
- Confirm `git status` does not show `.env`, certificates, sequence state, packet captures, or large data files.
- Inspect README commands from a clean checkout.
- Keep only safe example configuration in `config/`.
- Document provenance for every data artifact.
