package com.akshit.marketdata.core;

import com.akshit.marketdata.proto.MarketDataEnvelope;

import java.util.Optional;

/** Shared processing boundary used by file and live ingestion paths. */
public final class NormalizedEventPipeline {
    private final ReplayCoordinator replayCoordinator;
    private final OrderBookVerifier orderBookVerifier;
    private long sequenceGaps;

    public NormalizedEventPipeline() {
        this(100_000);
    }

    public NormalizedEventPipeline(int replayCapacity) {
        this.replayCoordinator = new ReplayCoordinator(replayCapacity);
        this.orderBookVerifier = new OrderBookVerifier();
    }

    public synchronized PipelineEventResult accept(MarketDataEnvelope event) {
        Optional<SequenceGap> gap = replayCoordinator.accept(event);
        if (gap.isPresent()) {
            sequenceGaps++;
        }
        OrderBookVerificationResult verification = orderBookVerifier.accept(event);
        return new PipelineEventResult(gap, verification);
    }

    public ReplayCoordinator replayCoordinator() { return replayCoordinator; }
    public OrderBookVerifier orderBookVerifier() { return orderBookVerifier; }
    public synchronized long sequenceGaps() { return sequenceGaps; }

    public static final class PipelineEventResult {
        private final Optional<SequenceGap> gap;
        private final OrderBookVerificationResult verification;

        private PipelineEventResult(Optional<SequenceGap> gap, OrderBookVerificationResult verification) {
            this.gap = gap;
            this.verification = verification;
        }

        public Optional<SequenceGap> gap() { return gap; }
        public OrderBookVerificationResult verification() { return verification; }
    }
}
