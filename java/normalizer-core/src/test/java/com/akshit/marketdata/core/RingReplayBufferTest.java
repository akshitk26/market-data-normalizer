package com.akshit.marketdata.core;

import com.akshit.marketdata.proto.MarketDataEnvelope;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class RingReplayBufferTest {
    @Test
    void replaysRequestedSequenceRangeInOrder() {
        RingReplayBuffer buffer = new RingReplayBuffer(8);
        for (int sequence = 1; sequence <= 7; sequence++) {
            buffer.append(MarketDataEnvelope.newBuilder()
                    .setSourceFeed("test")
                    .setInstrument("AAPL")
                    .setSequenceNumber(sequence)
                    .build());
        }

        List<MarketDataEnvelope> replayed = buffer.replay(4, 6);

        assertEquals(3, replayed.size());
        assertEquals(4, replayed.get(0).getSequenceNumber());
        assertEquals(5, replayed.get(1).getSequenceNumber());
        assertEquals(6, replayed.get(2).getSequenceNumber());
    }
}
