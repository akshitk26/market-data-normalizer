# Data Sources

The project is designed around real or captured market data.

## FIX Tag-Value

Most FIX market-data sessions are private, so the project should start with captured or sample market-data messages under `data/fix/`.

Recommended free starting source: [Gemini's official FIX market-data examples](https://developer.gemini.com/trading/fix/market-data/examples/market-data-responses). The project downloads those pages at runtime, extracts the published raw FIX messages, and writes a local capture under `data/fix/`. They include `35=W` snapshot/full-refresh and `35=X` incremental-refresh messages with bid/offer, price, size, symbol, and update-action fields.

Implementation status: the Gemini FIX parser, JSONL processor, official-example downloader, and provisioned-session client are implemented. The downloader is an official-example ingestion path, not a fake generator. The live client still requires Gemini to provision the private session and network details.

The parser should support common market-data snapshot and incremental-refresh fields first, especially:

- `35=W` market data snapshot/full refresh
- `35=X` market data incremental refresh
- bid/ask side
- price
- size
- symbol
- sequence number

## WebSocket JSON

This is the most practical live source because several exchanges publish free public market-data WebSocket feeds.

Current implemented source:

- Coinbase Exchange public `level2_batch` feed for `BTC-USD`, `ETH-USD`, `SOL-USD`, and `LTC-USD`

Planned candidates:

- Binance market-data streams
- Kraken public book feed

The Binance parser exists only as boilerplate. It should be implemented later because Binance depth streams expose update id ranges that are better suited for sequence-gap testing than Coinbase's public `level2_batch` messages.

## Nasdaq ITCH

Live Nasdaq TotalView-ITCH usually requires a commercial market-data agreement. The free path for this project is file-based parsing against public/sample Historical TotalView-ITCH data, not live exchange connectivity.

Recommended free starting sources:

- Nasdaq sample TotalView-ITCH files where available
- LOBSTER sample files for order-book/message examples derived from official Nasdaq Historical TotalView-ITCH samples
- local user-provided ITCH sample files placed under `data/itch/`

For non-static ongoing test data, the best practical approach is a two-tier source strategy:

1. Download the official Nasdaq sample binary files once for parser correctness and message-layout tests.
2. Add a capture/import command that accepts new real binary files from the public sample directory or another legally available source, then runs randomized windows over those files. The randomness selects real message ranges, symbols, and session portions; it does not invent order fields.
3. Use LOBSTER's free sample files as a second independent source for book-reconstruction checks. They are derived from official Nasdaq Historical TotalView-ITCH samples, so they provide realistic message behavior without pretending to be a live feed.
4. If we later need continuously changing data without licensing ITCH, run a free public exchange feed such as Coinbase or Binance, record its full-depth events, and add an ITCH-shaped export only as a clearly labeled compatibility fixture. That validates downstream behavior but is not presented as real Nasdaq ITCH.

There is no legitimate free anonymous Nasdaq TotalView-ITCH live stream. [Nasdaq's ITCH FAQ](https://www.nasdaqtrader.com/Content/TechnicalSupport/FAQs/ITCH_FAQ.pdf) points to sample files, while complete historical/live TotalView access is a licensed product. [LOBSTER's sample files](https://data.lobsterdata.com/info/DataSamples.php) are another useful free source and state that their samples are based on official Nasdaq Historical TotalView-ITCH data. The implementation will therefore keep the source provenance visible and will never silently turn synthetic data into an ITCH claim.

Implementation status: `NasdaqItchBinaryParser`, the official sample-directory downloader, the length-prefixed file processor, and runnable capture/process commands are implemented. The capture command stores a small seeded window of real messages locally instead of committing large source archives.

The first ITCH implementation should target a documented subset:

- add order
- order executed
- order cancel/delete
- replace order
- stock directory / symbol metadata where needed
