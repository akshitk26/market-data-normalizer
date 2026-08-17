package com.akshit.marketdata.core;

import com.akshit.marketdata.proto.MarketDataEnvelope;

public interface FeedParser<T> {
    MarketDataEnvelope parse(T rawMessage);
}
