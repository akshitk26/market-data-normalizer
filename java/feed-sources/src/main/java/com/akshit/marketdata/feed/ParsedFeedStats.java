package com.akshit.marketdata.feed;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ParsedFeedStats {
    private final int rawMessages;
    private final int normalizedEvents;
    private final int snapshots;
    private final int l2Updates;
    private final int deletes;
    private final long sequenceGaps;
    private final long desynchronizedEvents;
    private final String lastVerificationError;
    private final Map<String, Integer> normalizedEventsByInstrument;

    public ParsedFeedStats(int rawMessages, int normalizedEvents, int snapshots, int l2Updates, int deletes) {
        this(rawMessages, normalizedEvents, snapshots, l2Updates, deletes, Collections.emptyMap());
    }

    public ParsedFeedStats(
            int rawMessages,
            int normalizedEvents,
            int snapshots,
            int l2Updates,
            int deletes,
            Map<String, Integer> normalizedEventsByInstrument) {
        this(rawMessages, normalizedEvents, snapshots, l2Updates, deletes,
                normalizedEventsByInstrument, 0, 0);
    }

    public ParsedFeedStats(
            int rawMessages,
            int normalizedEvents,
            int snapshots,
            int l2Updates,
            int deletes,
            Map<String, Integer> normalizedEventsByInstrument,
            long sequenceGaps,
            long desynchronizedEvents) {
        this(rawMessages, normalizedEvents, snapshots, l2Updates, deletes, normalizedEventsByInstrument,
                sequenceGaps, desynchronizedEvents, "");
    }

    public ParsedFeedStats(
            int rawMessages,
            int normalizedEvents,
            int snapshots,
            int l2Updates,
            int deletes,
            Map<String, Integer> normalizedEventsByInstrument,
            long sequenceGaps,
            long desynchronizedEvents,
            String lastVerificationError) {
        this.rawMessages = rawMessages;
        this.normalizedEvents = normalizedEvents;
        this.snapshots = snapshots;
        this.l2Updates = l2Updates;
        this.deletes = deletes;
        this.normalizedEventsByInstrument = Collections.unmodifiableMap(new LinkedHashMap<>(normalizedEventsByInstrument));
        this.sequenceGaps = sequenceGaps;
        this.desynchronizedEvents = desynchronizedEvents;
        this.lastVerificationError = lastVerificationError;
    }

    public int rawMessages() {
        return rawMessages;
    }

    public int normalizedEvents() {
        return normalizedEvents;
    }

    public int snapshots() {
        return snapshots;
    }

    public int l2Updates() {
        return l2Updates;
    }

    public int deletes() {
        return deletes;
    }

    public Map<String, Integer> normalizedEventsByInstrument() {
        return normalizedEventsByInstrument;
    }

    public long sequenceGaps() { return sequenceGaps; }

    public long desynchronizedEvents() { return desynchronizedEvents; }

    public String lastVerificationError() { return lastVerificationError; }
}
