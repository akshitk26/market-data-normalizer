package com.akshit.marketdata.ingestion;

import com.akshit.marketdata.core.NormalizedEventPipeline;
import com.akshit.marketdata.core.OrderBookVerificationReport;
import com.akshit.marketdata.core.ReplayCoordinator;
import com.akshit.marketdata.proto.MarketDataEnvelope;
import com.akshit.marketdata.proto.ReplayRequest;
import com.akshit.marketdata.proto.ReplayResponse;
import com.akshit.marketdata.feed.CoinbaseLevel2Parser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Fault-injection run using a real Coinbase capture and the shared replay pipeline. */
public final class ReplayResilienceSimulationApp {
    private static final String SOURCE = "resilience-coinbase-capture";

    private ReplayResilienceSimulationApp() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            throw new IllegalArgumentException(
                    "Usage: <coinbase-jsonl> [drop-rate] [seed] [replay-capacity] [burst-length] [reorder-window] [duplicate-rate]");
        }
        Path input = Path.of(args[0]);
        double dropRate = args.length > 1 ? Double.parseDouble(args[1]) : 0.005;
        long seed = args.length > 2 ? Long.parseLong(args[2]) : 20260818L;
        int replayCapacity = args.length > 3 ? Integer.parseInt(args[3]) : 100_000;
        int burstLength = args.length > 4 ? Integer.parseInt(args[4]) : 1;
        int reorderWindow = args.length > 5 ? Integer.parseInt(args[5]) : 1;
        double duplicateRate = args.length > 6 ? Double.parseDouble(args[6]) : 0.0;

        List<List<MarketDataEnvelope>> messages = loadMessages(input);
        List<Integer> delivery = FaultModel.createDelivery(
                messages.size(), dropRate, seed, burstLength, reorderWindow, duplicateRate);
        ScenarioResult withoutReplay = run(messages, delivery, replayCapacity, false);
        ScenarioResult withReplay = run(messages, delivery, replayCapacity, true);

        System.out.println("simulation=real-capture-replay-resilience");
        System.out.println("input=" + input.toAbsolutePath());
        System.out.println("source_messages=" + messages.size());
        System.out.println("normalized_events=" + countEvents(messages));
        System.out.println("drop_rate=" + dropRate);
        System.out.println("seed=" + seed);
        System.out.println("burst_length=" + burstLength);
        System.out.println("reorder_window=" + reorderWindow);
        System.out.println("duplicate_rate=" + duplicateRate);
        System.out.println("messages_dropped=" + FaultModel.droppedMessages(messages.size(), delivery));
        print("without_replay", withoutReplay, countEvents(messages));
        print("with_replay", withReplay, countEvents(messages));
        System.out.println("note=Loss is applied to source messages before parsing. It models feed-message loss, not individual IP packet loss.");
    }

    private static void print(String name, ScenarioResult result, long normalizedEvents) {
        double perMillion = normalizedEvents == 0
                ? 0.0
                : result.unresolvedDesynchronizations * 1_000_000.0 / normalizedEvents;
        System.out.println(name + ".replay_requests=" + result.replayRequests);
        System.out.println(name + ".replayed_events=" + result.replayedEvents);
        System.out.println(name + ".incomplete_replays=" + result.incompleteReplays);
        System.out.println(name + ".verification_errors=" + result.verificationErrors);
        System.out.println(name + ".book_mismatch_instruments=" + result.bookMismatchInstruments);
        System.out.println(name + ".unresolved_desynchronizations=" + result.unresolvedDesynchronizations);
        System.out.println(name + ".unresolved_desynchronizations_per_million=" + perMillion);
    }

    private static List<List<MarketDataEnvelope>> loadMessages(Path input) throws IOException {
        CoinbaseLevel2Parser parser = new CoinbaseLevel2Parser();
        List<List<MarketDataEnvelope>> messages = new ArrayList<>();
        long sequence = 1;
        for (String line : Files.readAllLines(input, StandardCharsets.UTF_8)) {
            if (line.trim().isEmpty()) {
                continue;
            }
            List<MarketDataEnvelope> normalized = new ArrayList<>();
            for (MarketDataEnvelope event : parser.parse(line)) {
                normalized.add(event.toBuilder()
                        .setSourceFeed(SOURCE)
                        .setSequenceNumber(sequence)
                        .build());
            }
            if (!normalized.isEmpty()) {
                messages.add(normalized);
                sequence++;
            }
        }
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("Input capture contains no normalized messages: " + input);
        }
        return messages;
    }

    private static ScenarioResult run(
            List<List<MarketDataEnvelope>> messages,
            List<Integer> delivery,
            int replayCapacity,
            boolean replayEnabled) {
        NormalizedEventPipeline receiver = new NormalizedEventPipeline(replayCapacity);
        ReplayCoordinator sourceReplay = new ReplayCoordinator(replayCapacity);
        com.akshit.marketdata.core.OrderBookVerifier reference = new com.akshit.marketdata.core.OrderBookVerifier();
        for (List<MarketDataEnvelope> message : messages) {
            for (MarketDataEnvelope event : message) {
                reference.accept(event);
                sourceReplay.accept(event);
            }
        }

        long replayRequests = 0;
        long replayedEvents = 0;
        long incompleteReplays = 0;
        Set<Long> requestedRanges = new HashSet<>();
        long nextExpectedSequence = 1;
        for (int sequence : delivery) {
            if (sequence < nextExpectedSequence) {
                continue;
            }
            if (sequence > nextExpectedSequence && replayEnabled) {
                long from = nextExpectedSequence;
                long to = sequence - 1L;
                long rangeKey = (from << 32) ^ to;
                if (requestedRanges.add(rangeKey)) {
                    replayRequests++;
                    ReplayResponse response = sourceReplay.replay(ReplayRequest.newBuilder()
                            .setSourceFeed(SOURCE)
                            .setFromSequence(from)
                            .setToSequence(to)
                            .build());
                    if (!response.getComplete()) {
                        incompleteReplays++;
                    }
                    for (MarketDataEnvelope replayed : response.getEventsList()) {
                        accept(receiver, Collections.singletonList(replayed));
                        replayedEvents++;
                    }
                }
            }
            List<MarketDataEnvelope> message = messages.get(sequence - 1);
            accept(receiver, message);
            nextExpectedSequence = sequence + 1L;
        }

        Map<String, String> referenceDigests = reference.bookDigests();
        Map<String, String> receiverDigests = receiver.orderBookVerifier().bookDigests();
        Set<String> instruments = new HashSet<>(referenceDigests.keySet());
        instruments.addAll(receiverDigests.keySet());
        int mismatches = 0;
        for (String instrument : instruments) {
            if (!referenceDigests.getOrDefault(instrument, "").equals(receiverDigests.getOrDefault(instrument, ""))) {
                mismatches++;
            }
        }
        OrderBookVerificationReport report = receiver.orderBookVerifier().report();
        return new ScenarioResult(
                replayRequests,
                replayedEvents,
                incompleteReplays,
                report.desynchronizedEvents(),
                mismatches,
                mismatches);
    }

    private static NormalizedEventPipeline.PipelineEventResult accept(
            NormalizedEventPipeline pipeline, List<MarketDataEnvelope> events) {
        NormalizedEventPipeline.PipelineEventResult result = null;
        for (MarketDataEnvelope event : events) {
            result = pipeline.accept(event);
        }
        return result;
    }

    private static long countEvents(List<List<MarketDataEnvelope>> messages) {
        long count = 0;
        for (List<MarketDataEnvelope> message : messages) {
            count += message.size();
        }
        return count;
    }

    private static final class ScenarioResult {
        private final long replayRequests;
        private final long replayedEvents;
        private final long incompleteReplays;
        private final long verificationErrors;
        private final int bookMismatchInstruments;
        private final int unresolvedDesynchronizations;

        private ScenarioResult(long replayRequests, long replayedEvents, long incompleteReplays,
                               long verificationErrors, int bookMismatchInstruments,
                               int unresolvedDesynchronizations) {
            this.replayRequests = replayRequests;
            this.replayedEvents = replayedEvents;
            this.incompleteReplays = incompleteReplays;
            this.verificationErrors = verificationErrors;
            this.bookMismatchInstruments = bookMismatchInstruments;
            this.unresolvedDesynchronizations = unresolvedDesynchronizations;
        }
    }

    private static final class FaultModel {
        private static List<Integer> createDelivery(int count, double dropRate, long seed,
                                                     int burstLength, int reorderWindow,
                                                     double duplicateRate) {
            if (dropRate < 0 || dropRate > 1 || duplicateRate < 0 || duplicateRate > 1
                    || burstLength <= 0 || reorderWindow <= 0) {
                throw new IllegalArgumentException("invalid fault model configuration");
            }
            Random random = new Random(seed);
            List<Integer> pending = new ArrayList<>();
            List<Integer> delivery = new ArrayList<>();
            int burstRemaining = 0;
            for (int sequence = 1; sequence <= count; sequence++) {
                boolean dropped = burstRemaining > 0;
                if (burstRemaining > 0) {
                    burstRemaining--;
                } else if (random.nextDouble() < dropRate) {
                    dropped = true;
                    burstRemaining = burstLength - 1;
                }
                if (dropped) {
                    continue;
                }
                pending.add(sequence);
                if (random.nextDouble() < duplicateRate) {
                    pending.add(sequence);
                }
                if (pending.size() >= reorderWindow) {
                    Collections.shuffle(pending, random);
                    delivery.addAll(pending);
                    pending.clear();
                }
            }
            Collections.shuffle(pending, random);
            delivery.addAll(pending);
            return delivery;
        }

        private static int droppedMessages(int count, List<Integer> delivery) {
            Set<Integer> delivered = new HashSet<>(delivery);
            return count - delivered.size();
        }
    }
}
