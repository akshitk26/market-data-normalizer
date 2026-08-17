package com.akshit.marketdata.benchmark;

import com.akshit.marketdata.core.RingReplayBuffer;
import com.akshit.marketdata.proto.MarketDataEnvelope;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.util.List;

@State(Scope.Thread)
public class ReplayBufferBenchmark {
    private RingReplayBuffer buffer;

    @Setup
    public void setUp() {
        buffer = new RingReplayBuffer(1_000_000);
        for (int sequence = 1; sequence <= 1_000_000; sequence++) {
            buffer.append(MarketDataEnvelope.newBuilder()
                    .setSourceFeed("benchmark")
                    .setInstrument("AAPL")
                    .setSequenceNumber(sequence)
                    .build());
        }
    }

    @Benchmark
    public List<MarketDataEnvelope> replaySmallGap() {
        return buffer.replay(500_000, 500_010);
    }
}
