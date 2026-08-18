package com.akshit.marketdata.core;

public final class OrderBookVerificationReport {
    private final long processedEvents;
    private final long appliedEvents;
    private final long ignoredEvents;
    private final long desynchronizedEvents;
    private final long snapshots;
    private final String lastError;

    public OrderBookVerificationReport(long processedEvents, long appliedEvents, long ignoredEvents,
                                       long desynchronizedEvents, long snapshots, String lastError) {
        this.processedEvents = processedEvents;
        this.appliedEvents = appliedEvents;
        this.ignoredEvents = ignoredEvents;
        this.desynchronizedEvents = desynchronizedEvents;
        this.snapshots = snapshots;
        this.lastError = lastError;
    }

    public long processedEvents() { return processedEvents; }
    public long appliedEvents() { return appliedEvents; }
    public long ignoredEvents() { return ignoredEvents; }
    public long desynchronizedEvents() { return desynchronizedEvents; }
    public long snapshots() { return snapshots; }
    public String lastError() { return lastError; }
}
