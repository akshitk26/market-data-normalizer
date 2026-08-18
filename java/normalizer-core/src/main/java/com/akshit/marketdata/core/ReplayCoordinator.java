package com.akshit.marketdata.core;

import com.akshit.marketdata.proto.MarketDataEnvelope;
import com.akshit.marketdata.proto.ReplayRequest;
import com.akshit.marketdata.proto.ReplayResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * First replay integration for normalized events.
 * Sequence tracking is kept per source feed because some feeds use session-wide sequences.
 * This class is intended for one ingestion worker at a time.
 */
public final class ReplayCoordinator {
    private final int bufferCapacity;
    private final Map<String, SourceState> states = new HashMap<>();

    public ReplayCoordinator(int bufferCapacity) {
        if (bufferCapacity <= 0) {
            throw new IllegalArgumentException("bufferCapacity must be positive");
        }
        this.bufferCapacity = bufferCapacity;
    }

    public synchronized Optional<SequenceGap> accept(MarketDataEnvelope event) {
        if (event == null || event.getSourceFeed().isEmpty()) {
            throw new IllegalArgumentException("event and source_feed must be present");
        }
        SourceState state = states.computeIfAbsent(event.getSourceFeed(), ignored -> new SourceState(bufferCapacity));
        state.buffer.append(event);
        if (event.getSequenceNumber() == 0) {
            return Optional.empty();
        }
        return state.gapDetector.observe(event.getSequenceNumber());
    }

    public synchronized ReplayResponse replay(ReplayRequest request) {
        if (request == null || request.getSourceFeed().isEmpty()) {
            throw new IllegalArgumentException("replay request must include source_feed");
        }
        if (request.getFromSequence() > request.getToSequence()) {
            throw new IllegalArgumentException("replay request range is invalid");
        }

        SourceState state = states.get(request.getSourceFeed());
        List<MarketDataEnvelope> candidates = state == null
                ? new ArrayList<>()
                : state.buffer.replay(request.getFromSequence(), request.getToSequence());
        List<MarketDataEnvelope> filtered = new ArrayList<>();
        for (MarketDataEnvelope event : candidates) {
            if (request.getInstrument().isEmpty() || request.getInstrument().equals(event.getInstrument())) {
                filtered.add(event);
            }
        }

        boolean complete = isContiguous(filtered, request.getFromSequence(), request.getToSequence());
        return ReplayResponse.newBuilder()
                .setSourceFeed(request.getSourceFeed())
                .setInstrument(request.getInstrument())
                .setFromSequence(request.getFromSequence())
                .setToSequence(request.getToSequence())
                .addAllEvents(filtered)
                .setComplete(complete)
                .build();
    }

    public int sourceCount() {
        return states.size();
    }

    private static boolean isContiguous(List<MarketDataEnvelope> events, long from, long to) {
        if (events.isEmpty()) {
            return false;
        }
        long expected = from;
        for (MarketDataEnvelope event : events) {
            long sequence = event.getSequenceNumber();
            if (sequence < expected) {
                continue;
            }
            if (sequence > expected) {
                return false;
            }
            if (expected == to) {
                return true;
            }
            expected++;
        }
        return expected > to;
    }

    private static final class SourceState {
        private final RingReplayBuffer buffer;
        private final GapDetector gapDetector = new GapDetector(1);

        private SourceState(int capacity) {
            this.buffer = new RingReplayBuffer(capacity);
        }
    }
}
