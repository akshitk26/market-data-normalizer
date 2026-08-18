package com.akshit.marketdata.book;

public final class BookVerifierApp {
    private BookVerifierApp() {
    }

    public static void main(String[] args) {
        System.out.println("market-data-normalizer book verifier");
        System.out.println("OrderBookVerifier is used by the Coinbase, Gemini FIX, and Nasdaq ITCH processing paths.");
        System.out.println("Run a source processing command to see desynchronized_events in its output.");
    }
}
