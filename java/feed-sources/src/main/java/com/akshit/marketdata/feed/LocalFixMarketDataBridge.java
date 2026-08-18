package com.akshit.marketdata.feed;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A localhost FIX acceptor backed by real Coinbase public WebSocket data.
 * It intentionally implements market-data-only session behavior for development.
 */
public final class LocalFixMarketDataBridge implements AutoCloseable {
    public static final int DEFAULT_PORT = 9876;

    private final int port;
    private final List<String> productIds;
    private final int maxMessages;
    private final Duration connectTimeout;
    private final CoinbaseToGeminiFixTranslator translator = new CoinbaseToGeminiFixTranslator();
    private CoinbaseLevel2WebSocketStream stream;

    public LocalFixMarketDataBridge(int port, List<String> productIds, int maxMessages) {
        if (port <= 0 || port > 65_535) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
        }
        if (productIds == null || productIds.isEmpty()) {
            throw new IllegalArgumentException("At least one Coinbase product is required");
        }
        if (maxMessages <= 0) {
            throw new IllegalArgumentException("maxMessages must be positive");
        }
        this.port = port;
        this.productIds = new ArrayList<>(productIds);
        this.maxMessages = maxMessages;
        this.connectTimeout = Duration.ofSeconds(15);
    }

    public BridgeResult run() throws IOException {
        try (ServerSocket server = new ServerSocket(port, 1, InetAddress.getLoopbackAddress())) {
            System.out.println("local_fix_bridge_listening=127.0.0.1:" + port);
            try (Socket client = server.accept();
                 BufferedInputStream input = new BufferedInputStream(client.getInputStream());
                 OutputStream output = client.getOutputStream()) {
                client.setSoTimeout(15_000);
                String logon = GeminiFixMessageCodec.read(input);
                if (logon == null || !"A".equals(GeminiFixMessageCodec.field(logon, 35))) {
                    throw new IOException("Local FIX bridge expected Logon (35=A)");
                }
                String clientCompId = GeminiFixMessageCodec.field(logon, 49);
                send(output, logonAcknowledgement(clientCompId));

                String request = GeminiFixMessageCodec.read(input);
                if (request == null || !"V".equals(GeminiFixMessageCodec.field(request, 35))) {
                    throw new IOException("Local FIX bridge expected Market Data Request (35=V)");
                }
                String requestId = valueOrDefault(GeminiFixMessageCodec.field(request, 262), "local-1");
                Set<String> requestedSymbols = new HashSet<>(GeminiFixMessageCodec.fields(request, 55));
                Set<String> requestedEntryTypes = new HashSet<>(GeminiFixMessageCodec.fields(request, 269));
                if (requestedEntryTypes.isEmpty()) {
                    requestedEntryTypes.add("0");
                    requestedEntryTypes.add("1");
                }
                startStream();

                int emitted = 0;
                long sequence = 2;
                while (emitted < maxMessages) {
                    String json;
                    try {
                        json = stream.nextMessage(Duration.ofSeconds(20));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Interrupted while reading Coinbase WebSocket", e);
                    }
                    if (json == null) {
                        if (stream.isClosed()) {
                            break;
                        }
                        continue;
                    }
                    String fix = translator.translate(json, sequence, clientCompId, requestId,
                            requestedSymbols, requestedEntryTypes);
                    if (fix == null) {
                        continue;
                    }
                    send(output, fix);
                    emitted++;
                    sequence++;
                }
                return new BridgeResult(emitted, productIds);
            }
        } finally {
            close();
        }
    }

    private void startStream() {
        stream = new CoinbaseLevel2WebSocketStream(productIds, connectTimeout);
        stream.start();
    }

    private static String logonAcknowledgement(String target) {
        return GeminiFixMessageCodec.build(Arrays.asList(
                "35=A",
                "34=1",
                "49=GEMINI",
                "52=" + java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HH:mm:ss.SSS")
                        .withZone(java.time.ZoneOffset.UTC).format(java.time.Instant.now()),
                "56=" + valueOrDefault(target, "LOCAL-CLIENT"),
                "98=0",
                "108=30"));
    }

    private static String valueOrDefault(String value, String fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }

    private static void send(OutputStream output, String message) throws IOException {
        output.write(message.getBytes(StandardCharsets.US_ASCII));
        output.flush();
    }

    @Override
    public void close() {
        if (stream != null) {
            stream.close();
        }
    }

    public static final class BridgeResult {
        private final int fixMessages;
        private final List<String> products;

        private BridgeResult(int fixMessages, List<String> products) {
            this.fixMessages = fixMessages;
            this.products = List.copyOf(products);
        }

        public int fixMessages() { return fixMessages; }
        public List<String> products() { return products; }
    }
}
