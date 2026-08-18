package com.akshit.marketdata.core;

public final class OrderBookVerificationResult {
    private final boolean applied;
    private final boolean desynchronized;
    private final String instrument;
    private final String reason;

    private OrderBookVerificationResult(boolean applied, boolean desynchronized, String instrument, String reason) {
        this.applied = applied;
        this.desynchronized = desynchronized;
        this.instrument = instrument;
        this.reason = reason;
    }

    public static OrderBookVerificationResult applied(String instrument) {
        return new OrderBookVerificationResult(true, false, instrument, "");
    }

    public static OrderBookVerificationResult ignored(String instrument) {
        return new OrderBookVerificationResult(false, false, instrument, "");
    }

    public static OrderBookVerificationResult desynchronized(String instrument, String reason) {
        return new OrderBookVerificationResult(false, true, instrument, reason);
    }

    public boolean applied() { return applied; }
    public boolean desynchronized() { return desynchronized; }
    public String instrument() { return instrument; }
    public String reason() { return reason; }
}
