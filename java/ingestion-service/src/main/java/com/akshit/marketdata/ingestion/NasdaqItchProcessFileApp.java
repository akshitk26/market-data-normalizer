package com.akshit.marketdata.ingestion;

import com.akshit.marketdata.feed.NasdaqItchBinaryFileProcessor;
import com.akshit.marketdata.feed.ParsedFeedStats;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;

public final class NasdaqItchProcessFileApp {
    private NasdaqItchProcessFileApp() {
    }

    public static void main(String[] args) throws IOException {
        Path input = args.length > 0 ? Path.of(args[0]) : Path.of("data", "itch", "nasdaq-itch-window.bin");
        LocalDate sessionDate = args.length > 1 ? LocalDate.parse(args[1]) : null;
        ParsedFeedStats stats = new NasdaqItchBinaryFileProcessor(sessionDate).process(input);

        System.out.println("input=" + input.toAbsolutePath());
        NasdaqItchCaptureApp.printStats(stats);
    }
}
