package com.akshit.marketdata.ingestion;

import com.akshit.marketdata.feed.CoinbaseLevel2JsonlProcessor;
import com.akshit.marketdata.feed.CoinbaseLevel2WebSocketClient;
import com.akshit.marketdata.feed.CoinbaseLevel2Parser;
import com.akshit.marketdata.feed.ParsedFeedStats;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class CoinbaseCaptureApp {
    private CoinbaseCaptureApp() {
    }

    public static void main(String[] args) throws IOException {
        List<String> productIds = args.length > 0
                ? parseProductIds(args[0])
                : CoinbaseLevel2WebSocketClient.DEFAULT_PRODUCT_IDS;
        int maxMessages = args.length > 1 ? Integer.parseInt(args[1]) : 12;
        Path output = args.length > 2
                ? Path.of(args[2])
                : Path.of("data", "websocket", "coinbase-level2-live-sample.jsonl");

        CoinbaseLevel2WebSocketClient client = new CoinbaseLevel2WebSocketClient(productIds, maxMessages, Duration.ofSeconds(20));
        List<String> rawMessages = client.capture();

        if (output.getParent() != null) {
            Files.createDirectories(output.getParent());
        }
        Files.write(output, rawMessages, StandardCharsets.UTF_8);

        CoinbaseLevel2JsonlProcessor processor = new CoinbaseLevel2JsonlProcessor(new CoinbaseLevel2Parser());
        ParsedFeedStats stats = processor.process(output);

        List<String> summary = new ArrayList<>();
        summary.add("product_ids=" + String.join(",", productIds));
        summary.add("raw_messages=" + stats.rawMessages());
        summary.add("normalized_events=" + stats.normalizedEvents());
        summary.add("snapshots=" + stats.snapshots());
        summary.add("l2_updates=" + stats.l2Updates());
        summary.add("deletes=" + stats.deletes());
        summary.add("sequence_gaps=" + stats.sequenceGaps());
        summary.add("desynchronized_events=" + stats.desynchronizedEvents());
        summary.add("last_verification_error=" + stats.lastVerificationError());
        for (Map.Entry<String, Integer> entry : stats.normalizedEventsByInstrument().entrySet()) {
            summary.add("instrument." + entry.getKey() + ".normalized_events=" + entry.getValue());
        }
        summary.add("output=" + output.toAbsolutePath());
        System.out.println(String.join(System.lineSeparator(), summary));
    }

    private static List<String> parseProductIds(String rawProductIds) {
        return Arrays.stream(rawProductIds.split(","))
                .map(String::trim)
                .filter(productId -> !productId.isEmpty())
                .collect(Collectors.toList());
    }
}
