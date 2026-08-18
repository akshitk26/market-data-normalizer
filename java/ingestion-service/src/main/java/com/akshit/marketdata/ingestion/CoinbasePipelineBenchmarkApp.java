package com.akshit.marketdata.ingestion;

import com.akshit.marketdata.feed.CoinbaseLevel2Parser;
import com.akshit.marketdata.proto.MarketDataEnvelope;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Single-worker benchmark for the direct Coinbase JSON-to-protobuf path. */
public final class CoinbasePipelineBenchmarkApp {
    private CoinbasePipelineBenchmarkApp() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: <coinbase-jsonl> [seconds]");
        }
        Path input = Path.of(args[0]);
        int seconds = args.length > 1 ? Integer.parseInt(args[1]) : 10;
        if (seconds <= 0) {
            throw new IllegalArgumentException("seconds must be positive");
        }

        List<String> messages = loadMessages(input);
        CoinbaseLevel2Parser parser = new CoinbaseLevel2Parser();
        run(messages, parser, 2_000_000_000L);
        Result result = run(messages, parser, seconds * 1_000_000_000L);
        double elapsedSeconds = result.elapsedNs / 1_000_000_000.0;

        System.out.println("benchmark=single-worker-coinbase-json-to-normalized");
        System.out.println("input=" + input.toAbsolutePath());
        System.out.println("source_messages=" + messages.size());
        System.out.println("raw_messages_processed=" + result.rawMessages);
        System.out.println("normalized_events=" + result.normalizedEvents);
        System.out.println("elapsed_seconds=" + elapsedSeconds);
        System.out.println("raw_messages_per_second=" + result.rawMessages / elapsedSeconds);
        System.out.println("normalized_events_per_second=" + result.normalizedEvents / elapsedSeconds);
        System.out.println("worker_threads=1");
        System.out.println("note=The real capture is replayed in memory to measure parser throughput; this is not a live-network rate.");
    }

    private static List<String> loadMessages(Path input) throws IOException {
        List<String> messages = new ArrayList<>();
        for (String line : Files.readAllLines(input, StandardCharsets.UTF_8)) {
            if (!line.trim().isEmpty()) {
                messages.add(line);
            }
        }
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("Input capture contains no messages: " + input);
        }
        return messages;
    }

    private static Result run(List<String> messages, CoinbaseLevel2Parser parser, long durationNs) {
        long start = System.nanoTime();
        long rawMessages = 0;
        long normalizedEvents = 0;
        do {
            for (String message : messages) {
                List<MarketDataEnvelope> events = parser.parse(message);
                rawMessages++;
                normalizedEvents += events.size();
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
