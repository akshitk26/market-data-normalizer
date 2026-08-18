package com.akshit.marketdata.feed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Converts real Coinbase level2 JSON into the Gemini FIX 4.4 market-data shape. */
public final class CoinbaseToGeminiFixTranslator {
    private static final DateTimeFormatter FIX_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HH:mm:ss.SSS")
            .withZone(ZoneOffset.UTC);
    private final ObjectMapper objectMapper;

    public CoinbaseToGeminiFixTranslator() {
        this(new ObjectMapper());
    }

    CoinbaseToGeminiFixTranslator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String translate(String rawJson, long fixSequence, String targetCompId, String mdReqId) {
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            String type = root.path("type").asText();
            if ("snapshot".equals(type)) {
                return snapshot(root, fixSequence, targetCompId, mdReqId);
            }
            if ("l2update".equals(type)) {
                return incremental(root, fixSequence, targetCompId, mdReqId);
            }
            return null;
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid Coinbase JSON message", e);
        }
    }

    private String snapshot(JsonNode root, long sequence, String target, String requestId) {
        List<String> fields = header("W", sequence, target, timestamp(root));
        String symbol = compactSymbol(required(root, "product_id"));
        fields.add("55=" + symbol);
        fields.add("262=" + requestId);
        JsonNode bids = requiredArray(root, "bids");
        JsonNode asks = requiredArray(root, "asks");
        fields.add("268=" + (bids.size() + asks.size()));
        appendSnapshotLevels(fields, bids, "0");
        appendSnapshotLevels(fields, asks, "1");
        return GeminiFixMessageCodec.build(fields);
    }

    private String incremental(JsonNode root, long sequence, String target, String requestId) {
        JsonNode changes = requiredArray(root, "changes");
        if (changes.isEmpty()) {
            return null;
        }
        List<String> fields = header("X", sequence, target, timestamp(root));
        fields.add("262=" + requestId);
        fields.add("268=" + changes.size());
        for (JsonNode change : changes) {
            if (!change.isArray() || change.size() != 3) {
                throw new IllegalArgumentException("Coinbase change must be [side, price, size]");
            }
            String side = change.get(0).asText();
            String action = isZero(change.get(2).asText()) ? "2" : "1";
            fields.add("279=" + action);
            fields.add("269=" + sideCode(side));
            fields.add("55=" + compactSymbol(required(root, "product_id")));
            fields.add("270=" + change.get(1).asText());
            fields.add("271=" + change.get(2).asText());
        }
        return GeminiFixMessageCodec.build(fields);
    }

    private static List<String> header(String type, long sequence, String target, String timestamp) {
        return new ArrayList<>(List.of(
                "35=" + type,
                "34=" + sequence,
                "49=GEMINI",
                "52=" + timestamp,
                "56=" + target));
    }

    private static void appendSnapshotLevels(List<String> fields, JsonNode levels, String side) {
        for (JsonNode level : levels) {
            if (!level.isArray() || level.size() != 2) {
                throw new IllegalArgumentException("Coinbase snapshot level must be [price, size]");
            }
            fields.add("269=" + side);
            fields.add("270=" + level.get(0).asText());
            fields.add("271=" + level.get(1).asText());
        }
    }

    private static String timestamp(JsonNode root) {
        String value = root.path("time").asText(null);
        return value == null ? FIX_TIME.format(Instant.now()) : FIX_TIME.format(Instant.parse(value));
    }

    private static JsonNode requiredArray(JsonNode root, String field) {
        JsonNode value = root.path(field);
        if (!value.isArray()) {
            throw new IllegalArgumentException("Coinbase message must include array: " + field);
        }
        return value;
    }

    private static String required(JsonNode root, String field) {
        String value = root.path(field).asText(null);
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("Coinbase message missing field: " + field);
        }
        return value;
    }

    private static String compactSymbol(String productId) {
        return productId.replace("-", "");
    }

    private static String sideCode(String side) {
        if ("buy".equals(side)) {
            return "0";
        }
        if ("sell".equals(side)) {
            return "1";
        }
        throw new IllegalArgumentException("Unknown Coinbase side: " + side);
    }

    private static boolean isZero(String value) {
        return new java.math.BigDecimal(value).signum() == 0;
    }
}
