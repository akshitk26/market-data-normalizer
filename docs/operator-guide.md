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

## 6. Nasdaq ITCH Workflow

Purpose: exercise a real binary feed handler without paying for a live TotalView connection or committing multi-gigabyte files.

```bash
./gradlew --no-daemon :ingestion-service:captureNasdaqItchWindow \
  --args="data/itch/nasdaq-itch-window.bin 500 1000 12345"

./gradlew --no-daemon :ingestion-service:processNasdaqItchFile \
  --args="data/itch/nasdaq-itch-window.bin 2021-07-13"
```

The capture command discovers the official Nasdaq directory, selects a real v5.0 file with the seed, streams gzip from the beginning, skips the administrative preamble, skips a seeded number of real messages, and writes a small original-format binary window.

The parser maintains order state for the captured window and maps add, execution, cancel, delete, replace, and trade messages into normalized events. A random mid-session window may contain executions whose add happened before the window; those messages are correctly ignored when state is unavailable. Full book reconstruction requires capturing from session start through the target point. Unsupported valid ITCH message types are skipped as non-L2 events.

## 7. ZeroMQ

`transport-zmq` contains a publisher and subscriber for protobuf byte arrays. The intended flow is:

```text
source adapter -> protobuf event -> publisher -> subscriber/book verifier
```

The current source commands print statistics and write captures. Publishing each normalized event is the next transport integration step.

## 8. Replay Buffer Status

`RingReplayBuffer` and `GapDetector` have isolated tests. They are not connected to Coinbase, Gemini FIX, or ITCH yet. This is deliberate: replay behavior should be integrated after all three source families share the same normalized-event path.

## 9. Performance

```bash
./gradlew --no-daemon :benchmark:jmh
```

Record machine/CPU, Java version, input size/source, warmup and measurement settings, throughput, allocations if measured, and whether parsing, protobuf creation, transport, and verification were included. A raw parser benchmark and an end-to-end network benchmark are different measurements.

## 10. Troubleshooting

`JAVA_HOME` errors: verify Java 11 with `/usr/libexec/java_home -V`.

Coinbase returns no normalized events: check product IDs, network access, and whether only subscription acknowledgments were captured.

Gemini FIX disconnects immediately: verify source IP, host/port, CompIDs, transport mode, and session schedule with Gemini. Do not guess private endpoint values.

ITCH capture produces only administrative messages: increase the random skip limit. A zero-event administrative window is valid but not useful for parser smoke testing.

ITCH execution produces no event: its corresponding add was outside the window. Use a session-start capture when testing book state.

## 11. Public Repository Checklist

- Run the full test suite.
- Remove local captures unless intentionally distributed.
- Confirm `git status` does not show `.env`, certificates, sequence state, packet captures, or large data files.
- Inspect README commands from a clean checkout.
- Keep only safe example configuration in `config/`.
- Document provenance for every data artifact.
