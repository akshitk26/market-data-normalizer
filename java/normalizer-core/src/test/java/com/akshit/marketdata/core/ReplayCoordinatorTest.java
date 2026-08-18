package com.akshit.marketdata.core;

import com.akshit.marketdata.proto.MarketDataEnvelope;
import com.akshit.marketdata.proto.ReplayRequest;
import com.akshit.marketdata.proto.ReplayResponse;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ReplayCoordinatorTest {
    @Test
    void detectsGapAndReplaysCompleteRange() {
        ReplayCoordinator coordinator = new ReplayCoordinator(8);
        coordinator.accept(event("feed", "BTCUSD", 1));
        Optional<SequenceGap> gap = coordinator.accept(event("feed", "BTCUSD", 3));
        coordinator.accept(event("feed", "BTCUSD", 2));

        assertTrue(gap.isPresent());
        assertEquals(2, gap.get().missingFrom());
        assertEquals(2, gap.get().missingTo());

        ReplayResponse response = coordinator.replay(request("feed", "BTCUSD", 1, 3));

        assertTrue(response.getComplete());
        assertEquals(3, response.getEventsCount());
        assertEquals(1, response.getEvents(0).getSequenceNumber());
        assertEquals(2, response.getEvents(1).getSequenceNumber());
        assertEquals(3, response.getEvents(2).getSequenceNumber());
    }

    @Test
    void reportsIncompleteRangeAfterEviction() {
        ReplayCoordinator coordinator = new ReplayCoordinator(2);
        coordinator.accept(event("feed", "BTCUSD", 1));
        coordinator.accept(event("feed", "BTCUSD", 2));
        coordinator.accept(event("feed", "BTCUSD", 3));

        ReplayResponse response = coordinator.replay(request("feed", "BTCUSD", 1, 3));

        assertFalse(response.getComplete());
        assertEquals(2, response.getEventsCount());
        assertEquals(2, response.getEvents(0).getSequenceNumber());
    }

    @Test
    void filtersReplayByInstrument() {
        ReplayCoordinator coordinator = new ReplayCoordinator(8);
        coordinator.accept(event("feed", "BTCUSD", 1));
        coordinator.accept(event("feed", "ETHUSD", 2));

        ReplayResponse response = coordinator.replay(request("feed", "ETHUSD", 1, 2));

        assertFalse(response.getComplete());
        assertEquals(1, response.getEventsCount());
        assertEquals("ETHUSD", response.getEvents(0).getInstrument());
    }

    @Test
    void acceptsDuplicateSourceSequenceForMultiEventMessage() {
        ReplayCoordinator coordinator = new ReplayCoordinator(8);
        coordinator.accept(event("feed", "BTCUSD", 1));
        coordinator.accept(event("feed", "BTCUSD", 2));
        coordinator.accept(event("feed", "BTCUSD", 2));
        coordinator.accept(event("feed", "BTCUSD", 3));

        ReplayResponse response = coordinator.replay(request("feed", "BTCUSD", 1, 3));

        assertTrue(response.getComplete());
        assertEquals(4, response.getEventsCount());
    }

    private static ReplayRequest request(String source, String instrument, long from, long to) {
        return ReplayRequest.newBuilder()
                .setSourceFeed(source)
                .setInstrument(instrument)
                .setFromSequence(from)
                .setToSequence(to)
                .build();
    }

    private static MarketDataEnvelope event(String source, String instrument, long sequence) {
        return MarketDataEnvelope.newBuilder()
                .setSourceFeed(source)
                .setInstrument(instrument)
                .setSequenceNumber(sequence)
                .build();
    }
}
