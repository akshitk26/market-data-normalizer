package com.akshit.marketdata.core;

import com.akshit.marketdata.proto.MarketDataEnvelope;

import java.util.ArrayList;
import java.util.List;

public final class RingReplayBuffer implements ReplayBuffer {
    private final MarketDataEnvelope[] entries;
    private int writeIndex;
    private int size;

    public RingReplayBuffer(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.entries = new MarketDataEnvelope[capacity];
    }

    @Override
    public void append(MarketDataEnvelope event) {
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }
        entries[writeIndex] = event;
        writeIndex = (writeIndex + 1) % entries.length;
        if (size < entries.length) {
            size++;
        }
    }

    @Override
    public List<MarketDataEnvelope> replay(long fromSequenceInclusive, long toSequenceInclusive) {
        if (fromSequenceInclusive > toSequenceInclusive) {
            throw new IllegalArgumentException("fromSequenceInclusive must be <= toSequenceInclusive");
        }
        List<MarketDataEnvelope> replayed = new ArrayList<>();
        int oldestIndex = (writeIndex - size + entries.length) % entries.length;
        for (int i = 0; i < size; i++) {
            MarketDataEnvelope event = entries[(oldestIndex + i) % entries.length];
            if (event == null) {
                continue;
            }
            long sequence = event.getSequenceNumber();
            if (sequence >= fromSequenceInclusive && sequence <= toSequenceInclusive) {
                replayed.add(event);
            }
        }
        replayed.sort((left, right) -> Long.compare(left.getSequenceNumber(), right.getSequenceNumber()));
        return replayed;
    }
}
