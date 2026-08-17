package com.akshit.marketdata.core;

import com.akshit.marketdata.proto.MarketDataEnvelope;

import java.util.List;

public interface MultiMessageFeedParser<T> {
    List<MarketDataEnvelope> parse(T rawMessage);
}
