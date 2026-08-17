package com.akshit.marketdata.core;

import java.util.Optional;

public final class GapDetector {
    private long expectedNextSequence;

    public GapDetector(long firstExpectedSequence) {
        if (firstExpectedSequence < 0) {
            throw new IllegalArgumentException("firstExpectedSequence must be non-negative");
        }
        this.expectedNextSequence = firstExpectedSequence;
    }

    public Optional<SequenceGap> observe(long observedSequence) {
        if (observedSequence < expectedNextSequence) {
            return Optional.empty();
        }

        if (observedSequence > expectedNextSequence) {
            SequenceGap gap = new SequenceGap(expectedNextSequence, observedSequence - 1, observedSequence);
            expectedNextSequence = observedSequence + 1;
            return Optional.of(gap);
        }

        expectedNextSequence++;
        return Optional.empty();
    }

    public long expectedNextSequence() {
        return expectedNextSequence;
    }
}
