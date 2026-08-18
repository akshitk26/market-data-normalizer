# Operator Guide

## 1. Build

The project uses Java 11 and Gradle.

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-11.jdk/Contents/Home \
GRADLE_USER_HOME=.gradle-home ./gradlew --no-daemon test
```

Build the native modules:

```bash
cmake -S native -B native/build
cmake --build native/build
```

## 2. Module Order

1. `proto` defines the protobuf messages.
2. `normalizer-core` defines parser contracts, gap detection, and replay.
3. `feed-sources` parses Coinbase, Gemini FIX, and Nasdaq ITCH.
4. `ingestion-service` provides runnable commands.
5. `transport-zmq` contains the ZeroMQ boundary.
6. `book-verifier` and `benchmark` are downstream modules.

## 3. Coinbase

Capture live data:

```bash
./gradlew --no-daemon :ingestion-service:captureCoinbaseLevel2 \
  --args="BTC-USD,ETH-USD,SOL-USD,LTC-USD 12 data/websocket/coinbase-live.jsonl"
```

Process the saved capture:

```bash
./gradlew --no-daemon :ingestion-service:processCoinbaseLevel2File \
  --args="data/websocket/coinbase-live.jsonl"
```

The capture command connects to Coinbase, writes JSON lines, and processes the messages. The second command checks that the saved file can be processed independently.

## 4. Local FIX Bridge

The bridge accepts a local FIX connection and uses real Coinbase WebSocket data as its source.

Start it:

```bash
./gradlew --no-daemon :ingestion-service:runLocalFixBridge \
  --args="9876 BTC-USD,ETH-USD 100"
```

In another terminal:

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

The bridge handles Logon, Logon acknowledgment, and Market Data Request. It converts Coinbase snapshots to `35=W` and updates to `35=X`. The existing FIX client parses and normalizes them.

## 5. Gemini FIX

Download and process official examples:

```bash
./gradlew --no-daemon :ingestion-service:captureGeminiFixExamples \
  --args="data/fix/gemini-official-market-data.jsonl"

./gradlew --no-daemon :ingestion-service:processGeminiFixFile \
  --args="data/fix/gemini-official-market-data.jsonl"
```

For a live session, use the values Gemini provides in a local ignored file. The live client sends Logon, sends a market-data request, handles heartbeats and Test Requests, checks sequence numbers, and writes `W` and `X` messages.

## 6. Nasdaq ITCH

Capture a real message window:

```bash
./gradlew --no-daemon :ingestion-service:captureNasdaqItchWindow \
  --args="data/itch/nasdaq-itch-window.bin 500 1000 12345"
```

Process it:

```bash
./gradlew --no-daemon :ingestion-service:processNasdaqItchFile \
  --args="data/itch/nasdaq-itch-window.bin 2021-07-13"
```

The parser maintains order state during the capture. A mid-session window can contain updates whose earlier add message is outside the window. Those updates are ignored when state is not available.

## 7. Replay

`RingReplayBuffer` stores recent events in memory. `ReplayCoordinator` adds events, tracks source sequence numbers, reports gaps, and creates protobuf `ReplayResponse` messages.

The first version is tested in isolation. It is not yet connected to every feed command or ZeroMQ.

## 8. ZeroMQ

`transport-zmq` contains protobuf publisher and subscriber classes. Source commands do not publish events through ZeroMQ yet.

## 9. Benchmark

Capture a real Coinbase input file:

```bash
./gradlew --no-daemon :ingestion-service:captureCoinbaseLevel2 \
  --args="BTC-USD,ETH-USD,SOL-USD,LTC-USD 500 data/websocket/coinbase-benchmark.jsonl"
```

Measure one Java worker translating Coinbase JSON to FIX and parsing FIX into normalized events:

```bash
./gradlew --no-daemon :ingestion-service:benchmarkLocalFixPipeline \
  --args="data/websocket/coinbase-benchmark.jsonl 10"
```

This is a CPU benchmark using real captured input. It is not a live-network benchmark. The application uses one processing worker. Linux users can use `taskset -c 0` for CPU affinity. macOS may schedule JVM support threads on other cores.

Measure the direct Coinbase JSON-to-normalized path:

```bash
./gradlew --no-daemon :ingestion-service:benchmarkCoinbasePipeline \
  --args="data/websocket/coinbase-benchmark.jsonl 10"
```

Compare this result with the FIX bridge result. The direct path shows source parsing cost. The FIX path includes translation and FIX parsing.

## 10. Troubleshooting

Java version:

```bash
/usr/libexec/java_home -V
```

Coinbase returns no messages: check network access, product IDs, and the capture timeout.

Local FIX bridge does not connect: start the bridge first, use port `9876`, and set `GEMINI_FIX_TRANSPORT_TLS=false`.

Gemini FIX disconnects: verify the host, port, CompIDs, source IP, transport mode, and sequence rules provided by Gemini.

ITCH capture has no normalized events: increase the random skip limit. A window can contain only administrative messages or updates whose earlier order state is outside the window.
