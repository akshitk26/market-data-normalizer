package com.akshit.marketdata.ingestion;

import com.akshit.marketdata.feed.FixTagValueMarketDataParser;
import com.akshit.marketdata.feed.GeminiFixJsonlProcessor;
import com.akshit.marketdata.feed.ParsedFeedStats;

import java.io.IOException;
import java.nio.file.Path;

public final class GeminiFixProcessFileApp {
    private GeminiFixProcessFileApp() {
    }

    public static void main(String[] args) throws IOException {
        Path input = args.length > 0
                ? Path.of(args[0])
                : Path.of("data", "fix", "gemini-official-market-data.jsonl");
        ParsedFeedStats stats = new GeminiFixJsonlProcessor(new FixTagValueMarketDataParser()).process(input);

        System.out.println("input=" + input.toAbsolutePath());
        System.out.println("raw_messages=" + stats.rawMessages());
        System.out.println("normalized_events=" + stats.normalizedEvents());
        System.out.println("snapshots=" + stats.snapshots());
        System.out.println("l2_updates=" + stats.l2Updates());
        System.out.println("deletes=" + stats.deletes());
        System.out.println("sequence_gaps=" + stats.sequenceGaps());
        System.out.println("desynchronized_events=" + stats.desynchronizedEvents());
        System.out.println("last_verification_error=" + stats.lastVerificationError());
        stats.normalizedEventsByInstrument().forEach((instrument, count) ->
                System.out.println("instrument." + instrument + ".normalized_events=" + count));
    }
}
