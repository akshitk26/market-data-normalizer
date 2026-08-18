package com.akshit.marketdata.core;

import com.akshit.marketdata.proto.Action;
import com.akshit.marketdata.proto.BookSnapshot;
import com.akshit.marketdata.proto.L2Update;
import com.akshit.marketdata.proto.MarketDataEnvelope;
import com.akshit.marketdata.proto.PriceLevel;
import com.akshit.marketdata.proto.Side;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class OrderBookVerifierTest {
    @Test
    void appliesSnapshotAndLevelUpdates() {
        OrderBookVerifier verifier = new OrderBookVerifier();
        assertTrue(verifier.accept(snapshot()).applied());
        assertTrue(verifier.accept(update(Action.MODIFY, 100, 8)).applied());
        assertTrue(verifier.accept(update(Action.TRADE, 100, 3)).applied());

        OrderBookVerificationReport report = verifier.report();
        assertEquals(3, report.appliedEvents());
        assertEquals(0, report.desynchronizedEvents());
    }

    @Test
    void reportsTradeThatExceedsKnownQuantityAsDesync() {
        OrderBookVerifier verifier = new OrderBookVerifier();
        verifier.accept(snapshot());

        OrderBookVerificationResult result = verifier.accept(update(Action.TRADE, 100, 11));

        assertFalse(result.applied());
        assertTrue(result.desynchronized());
        assertEquals(1, verifier.report().desynchronizedEvents());
    }

    @Test
    void tracksOrderLevelItchLifecycle() {
        OrderBookVerifier verifier = new OrderBookVerifier();
        assertTrue(verifier.accept(orderUpdate(Action.ADD, 100, 10, "order-1")).applied());
        assertTrue(verifier.accept(orderUpdate(Action.MODIFY, 100, 6, "order-1")).applied());
        assertTrue(verifier.accept(orderUpdate(Action.TRADE, 100, 6, "order-1")).applied());
        assertEquals(0, verifier.report().desynchronizedEvents());
    }

    private static MarketDataEnvelope snapshot() {
        return MarketDataEnvelope.newBuilder()
                .setInstrument("BTCUSD")
                .setBookSnapshot(BookSnapshot.newBuilder()
                        .addBids(PriceLevel.newBuilder().setPriceNanos(100).setQuantityNanos(10).setLevel(1))
                        .addAsks(PriceLevel.newBuilder().setPriceNanos(101).setQuantityNanos(5).setLevel(1)))
                .build();
    }

    private static MarketDataEnvelope update(Action action, long price, long quantity) {
        return orderUpdate(action, price, quantity, "");
    }

    private static MarketDataEnvelope orderUpdate(Action action, long price, long quantity, String orderId) {
        return MarketDataEnvelope.newBuilder()
                .setInstrument("BTCUSD")
                .setL2Update(L2Update.newBuilder()
                        .setSide(Side.BID)
                        .setAction(action)
                        .setPriceNanos(price)
                        .setQuantityNanos(quantity)
                        .setOrderId(orderId))
                .build();
    }
}
