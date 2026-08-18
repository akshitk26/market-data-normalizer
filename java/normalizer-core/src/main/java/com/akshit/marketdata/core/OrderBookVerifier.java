package com.akshit.marketdata.core;

import com.akshit.marketdata.proto.Action;
import com.akshit.marketdata.proto.BookSnapshot;
import com.akshit.marketdata.proto.L2Update;
import com.akshit.marketdata.proto.MarketDataEnvelope;
import com.akshit.marketdata.proto.PriceLevel;
import com.akshit.marketdata.proto.Side;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Applies normalized events and reports state transitions that cannot be reconciled. */
public final class OrderBookVerifier {
    private final Map<String, Book> books = new HashMap<>();
    private long processedEvents;
    private long appliedEvents;
    private long ignoredEvents;
    private long desynchronizedEvents;
    private long snapshots;
    private String lastError = "";

    public synchronized OrderBookVerificationResult accept(MarketDataEnvelope event) {
        if (event == null || event.getInstrument().isEmpty()) {
            return recordDesync("", "event is missing an instrument");
        }
        processedEvents++;
        if (!event.hasBookSnapshot() && !event.hasL2Update()) {
            ignoredEvents++;
            return OrderBookVerificationResult.ignored(event.getInstrument());
        }

        Book book = books.computeIfAbsent(event.getInstrument(), ignored -> new Book());
        try {
            if (event.hasBookSnapshot()) {
                applySnapshot(book, event.getBookSnapshot());
                snapshots++;
            } else {
                applyUpdate(book, event.getL2Update());
            }
            appliedEvents++;
            return OrderBookVerificationResult.applied(event.getInstrument());
        } catch (IllegalArgumentException error) {
            return recordDesync(event.getInstrument(), error.getMessage());
        }
    }

    public synchronized OrderBookVerificationReport report() {
        return new OrderBookVerificationReport(
                processedEvents, appliedEvents, ignoredEvents, desynchronizedEvents, snapshots, lastError);
    }

    /** Returns deterministic per-instrument state digests for end-state comparisons. */
    public synchronized Map<String, String> bookDigests() {
        Map<String, String> digests = new HashMap<>();
        for (Map.Entry<String, Book> entry : books.entrySet()) {
            StringBuilder state = new StringBuilder();
            appendLevels(state, "B", entry.getValue().bids);
            appendLevels(state, "A", entry.getValue().asks);
            for (Map.Entry<String, OrderState> order : new TreeMap<>(entry.getValue().orders).entrySet()) {
                OrderState value = order.getValue();
                state.append("O|").append(order.getKey()).append('|')
                        .append(value.side).append('|').append(value.price).append('|')
                        .append(value.quantity).append(';');
            }
            digests.put(entry.getKey(), sha256(state.toString()));
        }
        return digests;
    }

    private static void appendLevels(StringBuilder state, String side, TreeMap<Long, Long> levels) {
        for (Map.Entry<Long, Long> level : levels.entrySet()) {
            state.append(side).append('|').append(level.getKey()).append('|')
                    .append(level.getValue()).append(';');
        }
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                hex.append(String.format(java.util.Locale.ROOT, "%02x", item));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private OrderBookVerificationResult recordDesync(String instrument, String reason) {
        desynchronizedEvents++;
        lastError = reason == null ? "unknown verification error" : reason;
        return OrderBookVerificationResult.desynchronized(instrument, lastError);
    }

    private static void applySnapshot(Book book, BookSnapshot snapshot) {
        book.bids.clear();
        book.asks.clear();
        book.orders.clear();
        for (PriceLevel level : snapshot.getBidsList()) {
            addSnapshotLevel(book.bids, level);
        }
        for (PriceLevel level : snapshot.getAsksList()) {
            addSnapshotLevel(book.asks, level);
        }
    }

    private static void addSnapshotLevel(TreeMap<Long, Long> side, PriceLevel level) {
        if (level.getPriceNanos() <= 0 || level.getQuantityNanos() <= 0) {
            throw new IllegalArgumentException("snapshot contains a non-positive price or quantity");
        }
        if (side.put(level.getPriceNanos(), level.getQuantityNanos()) != null) {
            throw new IllegalArgumentException("snapshot contains duplicate price levels");
        }
    }

    private static void applyUpdate(Book book, L2Update update) {
        if (update.getSide() == Side.SIDE_UNSPECIFIED) {
            throw new IllegalArgumentException("update is missing a side");
        }
        if (update.getPriceNanos() <= 0 || update.getQuantityNanos() < 0) {
            throw new IllegalArgumentException("update contains an invalid price or quantity");
        }

        TreeMap<Long, Long> side = update.getSide() == Side.BID ? book.bids : book.asks;
        String orderId = update.getOrderId();
        if (!orderId.isEmpty()) {
            applyOrderUpdate(book, side, update, orderId);
            return;
        }

        long price = update.getPriceNanos();
        switch (update.getAction()) {
            case ADD:
                side.merge(price, update.getQuantityNanos(), Math::addExact);
                break;
            case MODIFY:
                setOrRemove(side, price, update.getQuantityNanos());
                break;
            case DELETE:
                if (side.remove(price) == null) {
                    throw new IllegalArgumentException("delete references an unknown price level");
                }
                break;
            case TRADE:
                subtract(side, price, update.getQuantityNanos());
                break;
            default:
                throw new IllegalArgumentException("unsupported L2 action: " + update.getAction());
        }
    }

    private static void applyOrderUpdate(Book book, TreeMap<Long, Long> side, L2Update update, String orderId) {
        long price = update.getPriceNanos();
        OrderState current = book.orders.get(orderId);
        switch (update.getAction()) {
            case ADD:
                if (current != null) {
                    throw new IllegalArgumentException("add reuses an active order id");
                }
                book.orders.put(orderId, new OrderState(update.getSide(), price, update.getQuantityNanos()));
                side.merge(price, update.getQuantityNanos(), Math::addExact);
                break;
            case MODIFY:
                requireOrder(current, orderId);
                moveQuantity(book, current, update.getSide(), price, update.getQuantityNanos());
                break;
            case DELETE:
                requireOrder(current, orderId);
                subtractLevel(book.side(current.side), current.price, current.quantity);
                book.orders.remove(orderId);
                break;
            case TRADE:
                requireOrder(current, orderId);
                if (update.getQuantityNanos() > current.quantity) {
                    throw new IllegalArgumentException("trade exceeds active order quantity");
                }
                current.quantity -= update.getQuantityNanos();
                subtractLevel(book.side(current.side), current.price, update.getQuantityNanos());
                if (current.quantity == 0) {
                    book.orders.remove(orderId);
                }
                break;
            default:
                throw new IllegalArgumentException("unsupported order action: " + update.getAction());
        }
    }

    private static void moveQuantity(Book book, OrderState current, Side newSide, long newPrice, long newQuantity) {
        subtractLevel(book.side(current.side), current.price, current.quantity);
        current.side = newSide;
        current.price = newPrice;
        current.quantity = newQuantity;
        book.side(newSide).merge(newPrice, newQuantity, Math::addExact);
    }

    private static void requireOrder(OrderState order, String orderId) {
        if (order == null) {
            throw new IllegalArgumentException("update references an unknown order id: " + orderId);
        }
    }

    private static void setOrRemove(TreeMap<Long, Long> side, long price, long quantity) {
        if (quantity == 0) {
            side.remove(price);
        } else {
            side.put(price, quantity);
        }
    }

    private static void subtract(TreeMap<Long, Long> side, long price, long quantity) {
        Long current = side.get(price);
        if (current == null || current < quantity) {
            throw new IllegalArgumentException("trade references insufficient price-level quantity");
        }
        subtractLevel(side, price, quantity);
    }

    private static void subtractLevel(TreeMap<Long, Long> side, long price, long quantity) {
        Long current = side.get(price);
        if (current == null || current < quantity) {
            throw new IllegalArgumentException("price-level quantity would become negative");
        }
        setOrRemove(side, price, current - quantity);
    }

    private static final class Book {
        private final TreeMap<Long, Long> bids = new TreeMap<>();
        private final TreeMap<Long, Long> asks = new TreeMap<>();
        private final Map<String, OrderState> orders = new HashMap<>();

        private TreeMap<Long, Long> side(Side side) {
            return side == Side.BID ? bids : asks;
        }
    }

    private static final class OrderState {
        private Side side;
        private long price;
        private long quantity;

        private OrderState(Side side, long price, long quantity) {
            this.side = side;
            this.price = price;
            this.quantity = quantity;
        }
    }
}
