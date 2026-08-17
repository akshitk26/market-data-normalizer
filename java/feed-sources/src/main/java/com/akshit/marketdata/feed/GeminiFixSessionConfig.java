package com.akshit.marketdata.feed;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public final class GeminiFixSessionConfig {
    private final String host;
    private final int port;
    private final String senderCompId;
    private final String targetCompId;
    private final boolean transportTls;
    private final boolean resetSequenceOnLogon;
    private final int heartbeatSeconds;
    private final List<String> symbols;
    private final int marketDepth;
    private final List<String> entryTypes;
    private final Path output;
    private final Path sequenceFile;
    private final int maxMarketDataMessages;

    public GeminiFixSessionConfig(
            String host,
            int port,
            String senderCompId,
            String targetCompId,
            boolean transportTls,
            boolean resetSequenceOnLogon,
            int heartbeatSeconds,
            List<String> symbols,
            int marketDepth,
            List<String> entryTypes,
            Path output,
            Path sequenceFile,
            int maxMarketDataMessages) {
        this.host = requireText(host, "host");
        if (port <= 0 || port > 65_535) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
        }
        this.port = port;
        this.senderCompId = requireText(senderCompId, "senderCompId");
        this.targetCompId = requireText(targetCompId, "targetCompId");
        this.transportTls = transportTls;
        this.resetSequenceOnLogon = resetSequenceOnLogon;
        if (heartbeatSeconds != 30) {
            throw new IllegalArgumentException("Gemini currently requires a 30-second FIX heartbeat");
        }
        this.heartbeatSeconds = heartbeatSeconds;
        this.symbols = immutableNonEmpty(symbols, "symbols");
        if (marketDepth != 0 && marketDepth != 1) {
            throw new IllegalArgumentException("marketDepth must be 0 (full book) or 1 (top of book)");
        }
        this.marketDepth = marketDepth;
        this.entryTypes = immutableNonEmpty(entryTypes, "entryTypes");
        this.output = Objects.requireNonNull(output, "output");
        this.sequenceFile = Objects.requireNonNull(sequenceFile, "sequenceFile");
        if (maxMarketDataMessages <= 0) {
            throw new IllegalArgumentException("maxMarketDataMessages must be positive");
        }
        this.maxMarketDataMessages = maxMarketDataMessages;
    }

    public static GeminiFixSessionConfig fromEnvironment(Map<String, String> environment) {
        return new GeminiFixSessionConfig(
                requiredEnvironment(environment, "GEMINI_FIX_HOST"),
                integerEnvironment(environment, "GEMINI_FIX_PORT"),
                requiredEnvironment(environment, "GEMINI_FIX_SENDER_COMP_ID"),
                environment.getOrDefault("GEMINI_FIX_TARGET_COMP_ID", "GEMINI"),
                booleanEnvironment(environment, "GEMINI_FIX_TRANSPORT_TLS", true),
                booleanEnvironment(environment, "GEMINI_FIX_RESET_SEQUENCE_ON_LOGON", false),
                integerEnvironment(environment, "GEMINI_FIX_HEARTBEAT_SECONDS", 30),
                listEnvironment(environment, "GEMINI_FIX_SYMBOLS", Arrays.asList("BTCUSD")),
                integerEnvironment(environment, "GEMINI_FIX_MARKET_DEPTH", 1),
                listEnvironment(environment, "GEMINI_FIX_ENTRY_TYPES", Arrays.asList("0", "1", "2")),
                Path.of(environment.getOrDefault("GEMINI_FIX_OUTPUT", "data/fix/gemini-live-market-data.jsonl")),
                Path.of(environment.getOrDefault("GEMINI_FIX_SEQUENCE_FILE", "data/fix/gemini-fix-sequence.properties")),
                integerEnvironment(environment, "GEMINI_FIX_MAX_MESSAGES", 1_000));
    }

    public String host() { return host; }
    public int port() { return port; }
    public String senderCompId() { return senderCompId; }
    public String targetCompId() { return targetCompId; }
    public boolean transportTls() { return transportTls; }
    public boolean resetSequenceOnLogon() { return resetSequenceOnLogon; }
    public int heartbeatSeconds() { return heartbeatSeconds; }
    public List<String> symbols() { return symbols; }
    public int marketDepth() { return marketDepth; }
    public List<String> entryTypes() { return entryTypes; }
    public Path output() { return output; }
    public Path sequenceFile() { return sequenceFile; }
    public int maxMarketDataMessages() { return maxMarketDataMessages; }

    private static String requiredEnvironment(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.trim().isEmpty() || value.startsWith("provided-by-gemini")) {
            throw new IllegalArgumentException("Missing required environment variable: " + name);
        }
        return value.trim();
    }

    private static int integerEnvironment(Map<String, String> environment, String name) {
        return integerEnvironment(environment, name, -1);
    }

    private static int integerEnvironment(Map<String, String> environment, String name, int defaultValue) {
        String value = environment.get(name);
        if (value == null || value.trim().isEmpty()) {
            if (defaultValue >= 0) {
                return defaultValue;
            }
            throw new IllegalArgumentException("Missing required environment variable: " + name);
        }
        return Integer.parseInt(value.trim());
    }

    private static boolean booleanEnvironment(Map<String, String> environment, String name, boolean defaultValue) {
        String value = environment.get(name);
        return value == null || value.trim().isEmpty() ? defaultValue : Boolean.parseBoolean(value.trim());
    }

    private static List<String> listEnvironment(Map<String, String> environment, String name, List<String> defaultValue) {
        String value = environment.get(name);
        if (value == null || value.trim().isEmpty()) {
            return immutableNonEmpty(defaultValue, name);
        }
        return immutableNonEmpty(
                Arrays.stream(value.split(","))
                        .map(String::trim)
                        .filter(item -> !item.isEmpty())
                        .collect(Collectors.toList()),
                name);
    }

    private static List<String> immutableNonEmpty(List<String> values, String name) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException(name + " must contain at least one value");
        }
        return Collections.unmodifiableList(values.stream().map(GeminiFixSessionConfig::requireValue).collect(Collectors.toList()));
    }

    private static String requireText(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        return value.trim();
    }

    private static String requireValue(String value) {
        return requireText(value, "list value");
    }
}
