package com.akshit.marketdata.core;

import com.akshit.marketdata.proto.MarketDataEnvelope;

import java.util.List;

public interface ReplayBuffer {
    void append(MarketDataEnvelope event);

    List<MarketDataEnvelope> replay(long fromSequenceInclusive, long toSequenceInclusive);
}
