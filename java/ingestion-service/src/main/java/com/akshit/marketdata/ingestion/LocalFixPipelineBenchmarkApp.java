package com.akshit.marketdata.ingestion;

import com.akshit.marketdata.feed.CoinbaseToGeminiFixTranslator;
import com.akshit.marketdata.feed.FixTagValueMarketDataParser;
import com.akshit.marketdata.proto.MarketDataEnvelope;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Single-worker throughput benchmark using a real Coinbase capture as the input corpus. */
public final class LocalFixPipelineBenchmarkApp {
    private LocalFixPipelineBenchmarkApp() {
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
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("Input capture contains no messages: " + input);
        }

        CoinbaseToGeminiFixTranslator translator = new CoinbaseToGeminiFixTranslator();
        FixTagValueMarketDataParser parser = new FixTagValueMarketDataParser();
        run(messages, translator, parser, 2_000_000_000L);
        Result result = run(messages, translator, parser, seconds * 1_000_000_000L);

        double elapsedSeconds = result.elapsedNs / 1_000_000_000.0;
        System.out.println("benchmark=single-worker-coinbase-json-to-fix-to-normalized");
        System.out.println("input=" + input.toAbsolutePath());
        System.out.println("source_messages=" + messages.size());
        System.out.println("raw_messages_processed=" + result.rawMessages);
        System.out.println("fix_messages_created=" + result.fixMessages);
        System.out.println("normalized_events=" + result.normalizedEvents);
        System.out.println("elapsed_seconds=" + elapsedSeconds);
        System.out.println("raw_messages_per_second=" + result.rawMessages / elapsedSeconds);
        System.out.println("normalized_events_per_second=" + result.normalizedEvents / elapsedSeconds);
        System.out.println("worker_threads=1");
        System.out.println("note=The real capture is replayed in memory to measure parser/translation throughput; this is not a live-network rate.");
    }

    private static List<String> loadMessages(Path input) throws IOException {
        List<String> messages = new ArrayList<>();
        for (String line : Files.readAllLines(input, StandardCharsets.UTF_8)) {
            if (!line.trim().isEmpty()) {
                messages.add(line);
            }
        }
        return messages;
    }

    private static Result run(
            List<String> messages,
            CoinbaseToGeminiFixTranslator translator,
            FixTagValueMarketDataParser parser,
            long durationNs) {
        long start = System.nanoTime();
        long rawMessages = 0;
        long fixMessages = 0;
        long normalizedEvents = 0;
        long sequence = 2;
        do {
            for (String json : messages) {
                String fix = translator.translate(json, sequence++, "LOCAL-CLIENT", "benchmark");
                rawMessages++;
                if (fix != null) {
                    fixMessages++;
                    List<MarketDataEnvelope> events = parser.parse(fix);
                    normalizedEvents += events.size();
                }
            }
        } while (System.nanoTime() - start < durationNs);
        return new Result(System.nanoTime() - start, rawMessages, fixMessages, normalizedEvents);
    }

    private static final class Result {
        private final long elapsedNs;
        private final long rawMessages;
        private final long fixMessages;
        private final long normalizedEvents;

        private Result(long elapsedNs, long rawMessages, long fixMessages, long normalizedEvents) {
            this.elapsedNs = elapsedNs;
            this.rawMessages = rawMessages;
            this.fixMessages = fixMessages;
            this.normalizedEvents = normalizedEvents;
        }
    }
}
