package com.akshit.marketdata.ingestion;

import com.akshit.marketdata.feed.FixTagValueMarketDataParser;
import com.akshit.marketdata.feed.GeminiFixJsonlProcessor;
import com.akshit.marketdata.feed.GeminiFixOfficialExampleDownloader;
import com.akshit.marketdata.feed.ParsedFeedStats;

import java.io.IOException;
import java.nio.file.Path;

public final class GeminiFixCaptureApp {
    private GeminiFixCaptureApp() {
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        Path output = args.length > 0
                ? Path.of(args[0])
                : Path.of("data", "fix", "gemini-official-market-data.jsonl");

        new GeminiFixOfficialExampleDownloader().downloadTo(output);
        ParsedFeedStats stats = new GeminiFixJsonlProcessor(new FixTagValueMarketDataParser()).process(output);

        System.out.println("source=gemini-official-fix-examples");
        System.out.println("raw_messages=" + stats.rawMessages());
        System.out.println("normalized_events=" + stats.normalizedEvents());
        System.out.println("snapshots=" + stats.snapshots());
        System.out.println("l2_updates=" + stats.l2Updates());
        System.out.println("deletes=" + stats.deletes());
        stats.normalizedEventsByInstrument().forEach((instrument, count) ->
                System.out.println("instrument." + instrument + ".normalized_events=" + count));
        System.out.println("output=" + output.toAbsolutePath());
    }
}
