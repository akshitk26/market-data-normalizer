package com.akshit.marketdata.feed;

import com.akshit.marketdata.core.MultiMessageFeedParser;
import com.akshit.marketdata.proto.MarketDataEnvelope;

import java.util.List;

public final class BinanceDepthParser implements MultiMessageFeedParser<String> {
    @Override
    public List<MarketDataEnvelope> parse(String rawMessage) {
        throw new UnsupportedOperationException("Binance depth parsing is planned after Coinbase level2_batch is complete");
    }
}
