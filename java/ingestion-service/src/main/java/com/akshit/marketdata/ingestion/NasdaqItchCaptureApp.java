package com.akshit.marketdata.ingestion;

import com.akshit.marketdata.feed.NasdaqItchBinaryFileProcessor;
import com.akshit.marketdata.feed.NasdaqItchSampleDownloader;
import com.akshit.marketdata.feed.ParsedFeedStats;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;

public final class NasdaqItchCaptureApp {
    private NasdaqItchCaptureApp() {
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        Path output = args.length > 0 ? Path.of(args[0]) : Path.of("data", "itch", "nasdaq-itch-window.bin");
        int maxMessages = args.length > 1 ? Integer.parseInt(args[1]) : 10_000;
        int maxSkipMessages = args.length > 2 ? Integer.parseInt(args[2]) : 20_000;
        long seed = args.length > 3 ? Long.parseLong(args[3]) : System.nanoTime();

        NasdaqItchSampleDownloader downloader = new NasdaqItchSampleDownloader();
        NasdaqItchSampleDownloader.CaptureResult capture = downloader.captureRandomWindow(
                output,
                maxMessages,
                maxSkipMessages,
                seed);
        ParsedFeedStats stats = new NasdaqItchBinaryFileProcessor(capture.source().sessionDate()).process(output);

        System.out.println("source=nasdaq-public-totalview-itch-v50");
        System.out.println("provenance=downloaded_real_nasdaq_sample_binary");
        System.out.println("source_file=" + capture.source().name());
        System.out.println("source_uri=" + capture.source().uri());
        System.out.println("session_date=" + capture.source().sessionDate());
        System.out.println("random_seed=" + capture.seed());
        System.out.println("skipped_messages=" + capture.skippedMessages());
        System.out.println("captured_messages=" + capture.capturedMessages());
        printStats(stats);
        System.out.println("output=" + output.toAbsolutePath());
    }

    static void printStats(ParsedFeedStats stats) {
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
