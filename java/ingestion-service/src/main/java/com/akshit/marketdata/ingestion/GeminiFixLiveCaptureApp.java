package com.akshit.marketdata.ingestion;

import com.akshit.marketdata.feed.FixTagValueMarketDataParser;
import com.akshit.marketdata.feed.GeminiFixJsonlProcessor;
import com.akshit.marketdata.feed.GeminiFixMarketDataSession;
import com.akshit.marketdata.feed.GeminiFixSessionConfig;
import com.akshit.marketdata.feed.ParsedFeedStats;

import java.io.IOException;
import java.util.Map;

public final class GeminiFixLiveCaptureApp {
    private GeminiFixLiveCaptureApp() {
    }

    public static void main(String[] args) throws IOException {
        GeminiFixSessionConfig config = GeminiFixSessionConfig.fromEnvironment(System.getenv());
        GeminiFixMarketDataSession.SessionResult result = new GeminiFixMarketDataSession(config).capture();
        ParsedFeedStats stats = new GeminiFixJsonlProcessor(new FixTagValueMarketDataParser()).process(result.output());

        System.out.println("source=gemini-live-fix-market-data");
        System.out.println("market_data_messages=" + result.marketDataMessages());
        System.out.println("next_incoming_sequence=" + result.nextIncomingSequence());
        System.out.println("raw_messages=" + stats.rawMessages());
        System.out.println("normalized_events=" + stats.normalizedEvents());
        System.out.println("snapshots=" + stats.snapshots());
        System.out.println("l2_updates=" + stats.l2Updates());
        System.out.println("deletes=" + stats.deletes());
        System.out.println("sequence_gaps=" + stats.sequenceGaps());
        System.out.println("desynchronized_events=" + stats.desynchronizedEvents());
        System.out.println("last_verification_error=" + stats.lastVerificationError());
        for (Map.Entry<String, Integer> entry : stats.normalizedEventsByInstrument().entrySet()) {
            System.out.println("instrument." + entry.getKey() + ".normalized_events=" + entry.getValue());
        }
        System.out.println("output=" + result.output().toAbsolutePath());
    }
}
