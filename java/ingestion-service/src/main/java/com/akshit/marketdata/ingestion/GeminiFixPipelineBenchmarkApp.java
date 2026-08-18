package com.akshit.marketdata.ingestion;

import com.akshit.marketdata.feed.FixTagValueMarketDataParser;
import com.akshit.marketdata.core.NormalizedEventPipeline;
import com.akshit.marketdata.proto.MarketDataEnvelope;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Single-worker benchmark for FIX tag-value messages to normalized protobuf events. */
public final class GeminiFixPipelineBenchmarkApp {
    private GeminiFixPipelineBenchmarkApp() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: <fix-capture> [seconds]");
        }
        Path input = Path.of(args[0]);
        int seconds = args.length > 1 ? Integer.parseInt(args[1]) : 10;
        List<String> messages = loadMessages(input);
        run(messages, 2_000_000_000L);
        Result result = run(messages, seconds * 1_000_000_000L);
        double elapsedSeconds = result.elapsedNs / 1_000_000_000.0;

        System.out.println("benchmark=single-worker-fix-tag-value-to-normalized");
        System.out.println("input=" + input.toAbsolutePath());
        System.out.println("source_messages=" + messages.size());
        System.out.println("raw_messages_processed=" + result.rawMessages);
        System.out.println("normalized_events=" + result.normalizedEvents);
        System.out.println("elapsed_seconds=" + elapsedSeconds);
        System.out.println("raw_messages_per_second=" + result.rawMessages / elapsedSeconds);
        System.out.println("normalized_events_per_second=" + result.normalizedEvents / elapsedSeconds);
        System.out.println("worker_threads=1");
        System.out.println("note=This measures FIX parsing, protobuf creation, replay tracking, and order-book verification from a real capture; it does not measure network delivery.");
    }

    private static List<String> loadMessages(Path input) throws IOException {
        List<String> messages = new ArrayList<>();
        for (String line : Files.readAllLines(input, StandardCharsets.US_ASCII)) {
            if (!line.trim().isEmpty()) {
                messages.add(line);
            }
        }
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("Input capture contains no messages: " + input);
        }
        return messages;
    }

    private static Result run(List<String> messages, long durationNs) {
        long start = System.nanoTime();
        long rawMessages = 0;
        long normalizedEvents = 0;
        do {
            FixTagValueMarketDataParser parser = new FixTagValueMarketDataParser();
            NormalizedEventPipeline pipeline = new NormalizedEventPipeline();
            for (String message : messages) {
                List<MarketDataEnvelope> events = parser.parse(message);
                rawMessages++;
                for (MarketDataEnvelope event : events) {
                    pipeline.accept(event);
                    normalizedEvents++;
                }
            }
        } while (System.nanoTime() - start < durationNs);
        return new Result(System.nanoTime() - start, rawMessages, normalizedEvents);
    }

    private static final class Result {
        private final long elapsedNs;
        private final long rawMessages;
        private final long normalizedEvents;

        private Result(long elapsedNs, long rawMessages, long normalizedEvents) {
            this.elapsedNs = elapsedNs;
            this.rawMessages = rawMessages;
            this.normalizedEvents = normalizedEvents;
        }
    }
}
