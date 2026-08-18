package com.akshit.marketdata.feed;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/** Continuous public Coinbase level2_batch input for local integration tests. */
public final class CoinbaseLevel2WebSocketStream implements AutoCloseable {
    private final URI uri;
    private final List<String> productIds;
    private final Duration connectTimeout;
    private final ArrayBlockingQueue<String> messages = new ArrayBlockingQueue<>(8_192);
    private final CompletableFuture<Void> closed = new CompletableFuture<>();
    private volatile Throwable failure;
    private volatile WebSocket webSocket;

    public CoinbaseLevel2WebSocketStream(List<String> productIds, Duration connectTimeout) {
        this(CoinbaseLevel2WebSocketClient.DEFAULT_URI, productIds, connectTimeout);
    }

    CoinbaseLevel2WebSocketStream(URI uri, List<String> productIds, Duration connectTimeout) {
        if (productIds == null || productIds.isEmpty()) {
            throw new IllegalArgumentException("At least one Coinbase product is required");
        }
        this.uri = Objects.requireNonNull(uri, "uri");
        this.productIds = new ArrayList<>(productIds);
        this.connectTimeout = Objects.requireNonNull(connectTimeout, "connectTimeout");
    }

    public void start() {
        webSocket = HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .connectTimeout(connectTimeout)
                .buildAsync(uri, new Listener())
                .join();
    }

    public String nextMessage(Duration timeout) throws InterruptedException {
        String message = messages.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (message != null) {
            return message;
        }
        if (failure != null) {
            throw new IllegalStateException("Coinbase WebSocket stream failed", failure);
        }
        if (closed.isDone()) {
            return null;
        }
        return null;
    }

    public boolean isClosed() {
        return closed.isDone();
    }

    @Override
    public void close() {
        WebSocket current = webSocket;
        if (current != null) {
            current.sendClose(WebSocket.NORMAL_CLOSURE, "local FIX bridge complete").join();
        }
    }

    private final class Listener implements WebSocket.Listener {
        private final StringBuilder currentMessage = new StringBuilder();

        @Override
        public void onOpen(WebSocket socket) {
            String productsJson = productIds.stream()
                    .map(product -> "\"" + product + "\"")
                    .collect(Collectors.joining(","));
            String subscribe = "{\"type\":\"subscribe\",\"product_ids\":["
                    + productsJson + "],\"channels\":[\"level2_batch\"]}";
            socket.sendText(subscribe, true);
            socket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
            currentMessage.append(data);
            if (last) {
                if (!messages.offer(currentMessage.toString())) {
                    failure = new IllegalStateException("Coinbase WebSocket bridge queue is full");
                    socket.abort();
                    closed.complete(null);
                    return null;
                }
                currentMessage.setLength(0);
            }
            socket.request(1);
            return null;
        }

        @Override
        public void onError(WebSocket socket, Throwable error) {
            failure = error;
            closed.complete(null);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket socket, int statusCode, String reason) {
            closed.complete(null);
            return WebSocket.Listener.super.onClose(socket, statusCode, reason);
        }
    }
}
