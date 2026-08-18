package com.akshit.marketdata.ingestion;

import com.akshit.marketdata.feed.NasdaqItchBinaryParser;
import com.akshit.marketdata.core.NormalizedEventPipeline;
import com.akshit.marketdata.proto.MarketDataEnvelope;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Single-worker benchmark for length-prefixed Nasdaq ITCH binary messages. */
public final class NasdaqItchPipelineBenchmarkApp {
    private NasdaqItchPipelineBenchmarkApp() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: <itch-capture> <session-date> [seconds]");
        }
        Path input = Path.of(args[0]);
        LocalDate sessionDate = LocalDate.parse(args[1]);
        int seconds = args.length > 2 ? Integer.parseInt(args[2]) : 10;
        List<byte[]> messages = loadMessages(input);
        run(messages, sessionDate, 2_000_000_000L);
        Result result = run(messages, sessionDate, seconds * 1_000_000_000L);
        double elapsedSeconds = result.elapsedNs / 1_000_000_000.0;

        System.out.println("benchmark=single-worker-itch-binary-to-normalized");
        System.out.println("input=" + input.toAbsolutePath());
        System.out.println("session_date=" + sessionDate);
        System.out.println("source_messages=" + messages.size());
        System.out.println("raw_messages_processed=" + result.rawMessages);
        System.out.println("normalized_events=" + result.normalizedEvents);
        System.out.println("bytes_processed=" + result.bytesProcessed);
        System.out.println("elapsed_seconds=" + elapsedSeconds);
        System.out.println("raw_messages_per_second=" + result.rawMessages / elapsedSeconds);
        System.out.println("normalized_events_per_second=" + result.normalizedEvents / elapsedSeconds);
        System.out.println("bytes_per_second=" + result.bytesProcessed / elapsedSeconds);
        System.out.println("worker_threads=1");
        System.out.println("note=This measures ITCH parsing, protobuf creation, replay tracking, and order-book verification from real sample messages; it does not claim live Nasdaq connectivity.");
    }

    private static List<byte[]> loadMessages(Path input) throws IOException {
        List<byte[]> messages = new ArrayList<>();
        try (DataInputStream stream = new DataInputStream(new BufferedInputStream(Files.newInputStream(input)))) {
            while (true) {
                int length;
                try {
                    length = stream.readUnsignedShort();
                } catch (EOFException end) {
                    break;
                }
                byte[] message = new byte[length];
                stream.readFully(message);
                messages.add(message);
            }
        }
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("Input capture contains no ITCH messages: " + input);
        }
        return messages;
    }

    private static Result run(List<byte[]> messages, LocalDate sessionDate, long durationNs) {
        long start = System.nanoTime();
        long rawMessages = 0;
        long normalizedEvents = 0;
        long bytesProcessed = 0;
        do {
            NasdaqItchBinaryParser parser = new NasdaqItchBinaryParser(sessionDate);
            NormalizedEventPipeline pipeline = new NormalizedEventPipeline();
            for (byte[] message : messages) {
                List<MarketDataEnvelope> events = parser.parse(ByteBuffer.wrap(message));
                rawMessages++;
                bytesProcessed += message.length;
                for (MarketDataEnvelope event : events) {
                    pipeline.accept(event);
                    normalizedEvents++;
                }
            }
        } while (System.nanoTime() - start < durationNs);
        return new Result(System.nanoTime() - start, rawMessages, normalizedEvents, bytesProcessed);
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
