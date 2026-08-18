package com.akshit.marketdata.ingestion;

import com.akshit.marketdata.feed.CoinbaseLevel2JsonlProcessor;
import com.akshit.marketdata.feed.CoinbaseLevel2Parser;
import com.akshit.marketdata.feed.ParsedFeedStats;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

public final class CoinbaseProcessFileApp {
    private CoinbaseProcessFileApp() {
    }

    public static void main(String[] args) throws IOException {
        Path input = args.length > 0
                ? Path.of(args[0])
                : Path.of("data", "websocket", "coinbase-level2-live-sample.jsonl");

        CoinbaseLevel2JsonlProcessor processor = new CoinbaseLevel2JsonlProcessor(new CoinbaseLevel2Parser());
        ParsedFeedStats stats = processor.process(input);

        System.out.println("input=" + input.toAbsolutePath());
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
    }
}
