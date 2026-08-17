package com.akshit.marketdata.core;

public final class SequenceGap {
    private final long missingFrom;
    private final long missingTo;
    private final long observedSequence;

    public SequenceGap(long missingFrom, long missingTo, long observedSequence) {
        if (missingFrom > missingTo) {
            throw new IllegalArgumentException("missingFrom must be <= missingTo");
        }
        this.missingFrom = missingFrom;
        this.missingTo = missingTo;
        this.observedSequence = observedSequence;
    }

    public long missingFrom() {
        return missingFrom;
    }

    public long missingTo() {
        return missingTo;
    }

    public long observedSequence() {
        return observedSequence;
    }
}
