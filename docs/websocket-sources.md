# WebSocket JSON Sources

## Current Implementation: Coinbase Exchange `level2_batch`

Coinbase is the first WebSocket JSON source because the public Exchange feed supports unauthenticated Level 2 market data.

The app subscribes to:

```json
{"type":"subscribe","product_ids":["BTC-USD","ETH-USD","SOL-USD","LTC-USD"],"channels":["level2_batch"]}
```

Coinbase sends:

- `snapshot` messages with `bids` and `asks` arrays of `[price, size]`
- `l2update` messages with `changes` arrays of `[side, price, size]`

The parser maps this into the shared protobuf `MarketDataEnvelope`:

- `snapshot` -> `BookSnapshot`
- `l2update` change -> one `L2Update`
- Coinbase `buy` -> protobuf `BID`
- Coinbase `sell` -> protobuf `ASK`
- zero size -> protobuf `DELETE`
- non-zero size -> protobuf `MODIFY`

Coinbase `level2_batch` does not expose the same explicit sequence range as Binance depth streams, so this source is for validating JSON normalization and live capture first. Binance is kept as the later WebSocket source for richer sequence-gap testing.

Replay-buffer wiring is intentionally held back for now. The current WebSocket phase captures and normalizes real Coinbase data, prints processing stats, and leaves replay integration for the later phase after WebSocket JSON, FIX, and ITCH all share the same normalizing path.

## Planned: Binance Depth Streams

`BinanceDepthParser` exists as a placeholder only. Nothing uses it yet.

Binance should be implemented after the Coinbase slice because its depth stream has explicit update ID ranges, which makes it a stronger source for sequence-gap detection tests.
