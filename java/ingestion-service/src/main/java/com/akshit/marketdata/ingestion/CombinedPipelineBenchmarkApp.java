package com.akshit.marketdata.ingestion;

import com.akshit.marketdata.core.NormalizedEventPipeline;
import com.akshit.marketdata.feed.CoinbaseLevel2Parser;
import com.akshit.marketdata.feed.FixTagValueMarketDataParser;
import com.akshit.marketdata.feed.NasdaqItchBinaryParser;
import com.akshit.marketdata.proto.MarketDataEnvelope;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Single-worker benchmark that processes all three source paths through one pipeline. */
public final class CombinedPipelineBenchmarkApp {
    private CombinedPipelineBenchmarkApp() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 4) {
            throw new IllegalArgumentException("Usage: <coinbase-jsonl> <fix-capture> <itch-capture> <session-date> [seconds]");
        }
        List<String> coinbase = readLines(Path.of(args[0]));
        List<String> fix = readLines(Path.of(args[1]));
        List<byte[]> itch = readItch(Path.of(args[2]));
        LocalDate sessionDate = LocalDate.parse(args[3]);
        int seconds = args.length > 4 ? Integer.parseInt(args[4]) : 10;

        run(coinbase, fix, itch, sessionDate, 2_000_000_000L);
        Result result = run(coinbase, fix, itch, sessionDate, seconds * 1_000_000_000L);
        double elapsedSeconds = result.elapsedNs / 1_000_000_000.0;

        System.out.println("benchmark=single-worker-combined-coinbase-fix-itch");
        System.out.println("coinbase_messages=" + coinbase.size());
        System.out.println("fix_messages=" + fix.size());
        System.out.println("itch_messages=" + itch.size());
        System.out.println("raw_messages_processed=" + result.rawMessages);
        System.out.println("normalized_events=" + result.normalizedEvents);
        System.out.println("bytes_processed=" + result.bytesProcessed);
        System.out.println("elapsed_seconds=" + elapsedSeconds);
        System.out.println("raw_messages_per_second=" + result.rawMessages / elapsedSeconds);
        System.out.println("normalized_events_per_second=" + result.normalizedEvents / elapsedSeconds);
        System.out.println("bytes_per_second=" + result.bytesProcessed / elapsedSeconds);
        System.out.println("worker_threads=1");
        System.out.println("pipeline=source parsing, protobuf creation, replay tracking, sequence-gap tracking, order-book verification");
    }

    private static Result run(List<String> coinbase, List<String> fix, List<byte[]> itch,
                              LocalDate sessionDate, long durationNs) {
        long start = System.nanoTime();
        long rawMessages = 0;
        long normalizedEvents = 0;
        long bytesProcessed = 0;
        do {
            NormalizedEventPipeline pipeline = new NormalizedEventPipeline();
            CoinbaseLevel2Parser coinbaseParser = new CoinbaseLevel2Parser();
            FixTagValueMarketDataParser fixParser = new FixTagValueMarketDataParser();
            NasdaqItchBinaryParser itchParser = new NasdaqItchBinaryParser(sessionDate);
            int rounds = Math.max(coinbase.size(), Math.max(fix.size(), itch.size()));
            for (int index = 0; index < rounds; index++) {
                if (index < coinbase.size()) {
                    List<MarketDataEnvelope> events = coinbaseParser.parse(coinbase.get(index));
                    rawMessages++;
                    bytesProcessed += coinbase.get(index).getBytes(StandardCharsets.UTF_8).length;
                    normalizedEvents += accept(pipeline, events);
                }
                if (index < fix.size()) {
                    List<MarketDataEnvelope> events = fixParser.parse(fix.get(index));
                    rawMessages++;
                    bytesProcessed += fix.get(index).getBytes(StandardCharsets.US_ASCII).length;
                    normalizedEvents += accept(pipeline, events);
                }
                if (index < itch.size()) {
                    byte[] message = itch.get(index);
                    rawMessages++;
                    bytesProcessed += message.length;
                    normalizedEvents += accept(pipeline, itchParser.parse(ByteBuffer.wrap(message)));
                }
            }
        } while (System.nanoTime() - start < durationNs);
        return new Result(System.nanoTime() - start, rawMessages, normalizedEvents, bytesProcessed);
    }

    private static long accept(NormalizedEventPipeline pipeline, List<MarketDataEnvelope> events) {
        for (MarketDataEnvelope event : events) {
            pipeline.accept(event);
        }
        return events.size();
    }

    private static List<String> readLines(Path path) throws IOException {
        List<String> lines = new ArrayList<>();
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            if (!line.trim().isEmpty()) {
                lines.add(line);
            }
        }
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("Input capture contains no messages: " + path);
        }
        return lines;
    }

    private static List<byte[]> readItch(Path path) throws IOException {
        List<byte[]> messages = new ArrayList<>();
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
            while (true) {
                int length;
                try {
                    length = input.readUnsignedShort();
                } catch (EOFException end) {
                    break;
                }
                byte[] message = new byte[length];
                input.readFully(message);
                messages.add(message);
            }
        }
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("Input capture contains no ITCH messages: " + path);
        }
        return messages;
    }

    private static final class Result {
        private final long elapsedNs;
        private final long rawMessages;
        private final long normalizedEvents;
        private final long bytesProcessed;

        private Result(long elapsedNs, long rawMessages, long normalizedEvents, long bytesProcessed) {
            this.elapsedNs = elapsedNs;
            this.rawMessages = rawMessages;
            this.normalizedEvents = normalizedEvents;
            this.bytesProcessed = bytesProcessed;
        }
    }
}
