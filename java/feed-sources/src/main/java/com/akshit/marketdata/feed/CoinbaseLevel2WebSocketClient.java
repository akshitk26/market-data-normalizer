package com.akshit.marketdata.feed;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public final class CoinbaseLevel2WebSocketClient implements AutoCloseable {
    public static final URI DEFAULT_URI = URI.create("wss://ws-feed.exchange.coinbase.com");
    public static final List<String> DEFAULT_PRODUCT_IDS = Arrays.asList("BTC-USD", "ETH-USD", "SOL-USD", "LTC-USD");

    private final URI uri;
    private final List<String> productIds;
    private final int maxMessages;
    private final Duration timeout;
    private final LinkedBlockingQueue<String> messages = new LinkedBlockingQueue<>();
    private final CountDownLatch done = new CountDownLatch(1);
    private volatile Throwable failure;
    private WebSocket webSocket;

    public CoinbaseLevel2WebSocketClient(String productId, int maxMessages, Duration timeout) {
        this(Arrays.asList(productId), maxMessages, timeout);
    }

    public CoinbaseLevel2WebSocketClient(List<String> productIds, int maxMessages, Duration timeout) {
        this(DEFAULT_URI, productIds, maxMessages, timeout);
    }

    CoinbaseLevel2WebSocketClient(URI uri, String productId, int maxMessages, Duration timeout) {
        this(uri, Arrays.asList(productId), maxMessages, timeout);
    }

    CoinbaseLevel2WebSocketClient(URI uri, List<String> productIds, int maxMessages, Duration timeout) {
        if (maxMessages <= 0) {
            throw new IllegalArgumentException("maxMessages must be positive");
        }
        if (productIds == null || productIds.isEmpty()) {
            throw new IllegalArgumentException("At least one product id is required");
        }
        this.uri = Objects.requireNonNull(uri, "uri");
        this.productIds = new ArrayList<>(productIds);
        this.maxMessages = maxMessages;
        this.timeout = Objects.requireNonNull(timeout, "timeout");
    }

    public List<String> capture() {
        HttpClient client = HttpClient.newHttpClient();
        webSocket = client.newWebSocketBuilder()
                .connectTimeout(timeout)
                .buildAsync(uri, new CaptureListener())
                .join();

        try {
            boolean completed = done.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!completed) {
                throw new IllegalStateException("Timed out waiting for Coinbase WebSocket capture");
            }
            if (failure != null) {
                throw new IllegalStateException("Coinbase WebSocket capture failed", failure);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while capturing Coinbase WebSocket messages", e);
        } finally {
            close();
        }
        return new ArrayList<>(messages);
    }

    @Override
    public void close() {
        if (webSocket != null) {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "capture complete").join();
        }
    }

    private final class CaptureListener implements WebSocket.Listener {
        private final StringBuilder currentMessage = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            String productsJson = productIds.stream()
                    .map(productId -> "\"" + productId + "\"")
                    .collect(Collectors.joining(","));
            String subscribe = "{\"type\":\"subscribe\",\"product_ids\":[" + productsJson + "],\"channels\":[\"level2_batch\"]}";
            webSocket.sendText(subscribe, true);
            WebSocket.Listener.super.onOpen(webSocket);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            currentMessage.append(data);
            if (last) {
                if (messages.size() < maxMessages) {
                    messages.offer(currentMessage.toString());
                }
                currentMessage.setLength(0);
                if (messages.size() >= maxMessages) {
                    done.countDown();
                }
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            failure = error;
            done.countDown();
        }
    }
}
