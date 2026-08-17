package com.akshit.marketdata.feed;

import com.akshit.marketdata.core.MultiMessageFeedParser;
import com.akshit.marketdata.proto.Action;
import com.akshit.marketdata.proto.L2Update;
import com.akshit.marketdata.proto.MarketDataEnvelope;
import com.akshit.marketdata.proto.Side;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

public final class NasdaqItchBinaryParser implements MultiMessageFeedParser<ByteBuffer> {
    public static final String SOURCE_FEED = "nasdaq-totalview-itch-5.0";

    private final LocalDate sessionDate;
    private final Map<Long, OrderState> orders = new HashMap<>();
    private long normalizedSequence;

    public NasdaqItchBinaryParser() {
        this(null);
    }

    public NasdaqItchBinaryParser(LocalDate sessionDate) {
        this.sessionDate = sessionDate;
    }

    @Override
    public List<MarketDataEnvelope> parse(ByteBuffer rawMessage) {
        ByteBuffer message = rawMessage.duplicate().order(ByteOrder.BIG_ENDIAN);
        if (!message.hasRemaining()) {
            throw new IllegalArgumentException("ITCH message must not be empty");
        }

        byte messageType = message.get();
        switch (messageType) {
            case 'A':
            case 'F':
                return parseAdd(message, messageType);
            case 'E':
                return parseExecute(message, false);
            case 'C':
                return parseExecute(message, true);
            case 'X':
                return parseCancel(message);
            case 'D':
                return parseDelete(message);
            case 'U':
                return parseReplace(message);
            case 'P':
                return parseTrade(message);
            case 'Q':
                return parseCrossTrade(message);
            case 'R':
                parseStockDirectory(message);
                return empty();
            case 'S':
            case 'H':
            case 'L':
            case 'N':
            case 'V':
            case 'W':
            case 'Y':
                return empty();
            default:
                // Preserve forward compatibility with valid ITCH message types that do not change L2 state.
                return empty();
        }
    }

    private List<MarketDataEnvelope> parseAdd(ByteBuffer message, byte messageType) {
        skipCommonHeader(message);
        long orderReference = unsignedLong(message);
        Side side = parseSide(message.get());
        long shares = unsignedInt(message);
        String instrument = text(message, 8);
        long priceNanos = priceToNanos(unsignedInt(message));
        if (messageType == 'F') {
            text(message, 4);
        }

        OrderState order = new OrderState(orderReference, instrument, side, priceNanos, shares);
        orders.put(orderReference, order);
        return singleton(update(order.instrument, side, Action.ADD, priceNanos, shares, orderReference));
    }

    private List<MarketDataEnvelope> parseExecute(ByteBuffer message, boolean printablePrice) {
        skipCommonHeader(message);
        long orderReference = unsignedLong(message);
        long executedShares = unsignedInt(message);
        skip(message, 8);
        long executionPrice = 0;
        if (printablePrice) {
            message.get();
            executionPrice = priceToNanos(unsignedInt(message));
        }

        OrderState order = orders.get(orderReference);
        if (order == null) {
            return empty();
        }
        order.remainingShares = Math.max(0, order.remainingShares - executedShares);
        long price = executionPrice == 0 ? order.priceNanos : executionPrice;
        MarketDataEnvelope event = update(order.instrument, order.side, Action.TRADE, price, executedShares, orderReference);
        if (order.remainingShares == 0) {
            orders.remove(orderReference);
        }
        return singleton(event);
    }

    private List<MarketDataEnvelope> parseCancel(ByteBuffer message) {
        skipCommonHeader(message);
        long orderReference = unsignedLong(message);
        long canceledShares = unsignedInt(message);
        OrderState order = orders.get(orderReference);
        if (order == null) {
            return empty();
        }
        order.remainingShares = Math.max(0, order.remainingShares - canceledShares);
        Action action = order.remainingShares == 0 ? Action.DELETE : Action.MODIFY;
        MarketDataEnvelope event = update(
                order.instrument,
                order.side,
                action,
                order.priceNanos,
                order.remainingShares,
                orderReference);
        if (order.remainingShares == 0) {
            orders.remove(orderReference);
        }
        return singleton(event);
    }

    private List<MarketDataEnvelope> parseDelete(ByteBuffer message) {
        skipCommonHeader(message);
        long orderReference = unsignedLong(message);
        OrderState order = orders.remove(orderReference);
        if (order == null) {
            return empty();
        }
        return singleton(update(order.instrument, order.side, Action.DELETE, order.priceNanos, 0, orderReference));
    }

    private List<MarketDataEnvelope> parseReplace(ByteBuffer message) {
        skipCommonHeader(message);
        long originalReference = unsignedLong(message);
        long newReference = unsignedLong(message);
        long shares = unsignedInt(message);
        long priceNanos = priceToNanos(unsignedInt(message));
        OrderState original = orders.remove(originalReference);
        if (original == null) {
            return empty();
        }

        OrderState replacement = new OrderState(
                newReference,
                original.instrument,
                original.side,
                priceNanos,
                shares);
        orders.put(newReference, replacement);
        List<MarketDataEnvelope> events = new ArrayList<>(2);
        events.add(update(original.instrument, original.side, Action.DELETE, original.priceNanos, 0, originalReference));
        events.add(update(replacement.instrument, replacement.side, Action.ADD, replacement.priceNanos, shares, newReference));
        return events;
    }

    private List<MarketDataEnvelope> parseTrade(ByteBuffer message) {
        skipCommonHeader(message);
        long orderReference = unsignedLong(message);
        Side side = parseSide(message.get());
        long shares = unsignedInt(message);
        String instrument = text(message, 8);
        long priceNanos = priceToNanos(unsignedInt(message));
        skip(message, 8);
        return singleton(update(instrument, side, Action.TRADE, priceNanos, shares, orderReference));
    }

    private List<MarketDataEnvelope> parseCrossTrade(ByteBuffer message) {
        skipCommonHeader(message);
        long shares = unsignedLong(message);
        String instrument = text(message, 8);
        long priceNanos = priceToNanos(unsignedInt(message));
        skip(message, 8);
        message.get();
        return singleton(update(instrument, Side.SIDE_UNSPECIFIED, Action.TRADE, priceNanos, shares, 0));
    }

    private void parseStockDirectory(ByteBuffer message) {
        int stockLocate = skipCommonHeader(message);
        String instrument = text(message, 8);
        stockByLocate.put(stockLocate, instrument);
    }

    private final Map<Integer, String> stockByLocate = new HashMap<>();

    private int skipCommonHeader(ByteBuffer message) {
        requireRemaining(message, 10);
        int stockLocate = unsignedShort(message);
        skip(message, 2);
        lastTimestampNanos = unsignedLong48(message);
        return stockLocate;
    }

    private MarketDataEnvelope update(
            String instrument,
            Side side,
            Action action,
            long priceNanos,
            long quantity,
            long orderReference) {
        L2Update.Builder update = L2Update.newBuilder()
                .setSide(side)
                .setAction(action)
                .setPriceNanos(priceNanos)
                .setQuantityNanos(quantityToNanos(quantity))
                .setLevel(0);
        if (orderReference > 0) {
            update.setOrderId(Long.toUnsignedString(orderReference));
        }
        return envelope(instrument, update.build());
    }

    private MarketDataEnvelope envelope(String instrument, L2Update update) {
        MarketDataEnvelope.Builder envelope = MarketDataEnvelope.newBuilder()
                .setSourceFeed(SOURCE_FEED)
                .setInstrument(instrument == null ? "UNKNOWN" : instrument)
                .setSequenceNumber(++normalizedSequence)
                .setReceiveTimeNs(System.currentTimeMillis() * 1_000_000L)
                .setL2Update(update);
        long timestampNanos = lastTimestampNanos;
        if (sessionDate != null) {
            long sessionStart = sessionDate.atStartOfDay(ZoneId.of("America/New_York"))
                    .toEpochSecond() * 1_000_000_000L;
            envelope.setEventTimeNs(sessionStart + timestampNanos);
        }
        return envelope.build();
    }

    private long lastTimestampNanos;

    private static Side parseSide(byte value) {
        if (value == 'B') {
            return Side.BID;
        }
        if (value == 'S') {
            return Side.ASK;
        }
        throw new IllegalArgumentException("Unsupported Nasdaq ITCH side: " + (char) value);
    }

    private static String text(ByteBuffer message, int length) {
        requireRemaining(message, length);
        byte[] bytes = new byte[length];
        message.get(bytes);
        return new String(bytes, StandardCharsets.US_ASCII).trim();
    }

    private static long priceToNanos(long priceInTenThousandths) {
        return Math.multiplyExact(priceInTenThousandths, 100_000L);
    }

    private static long quantityToNanos(long shares) {
        return Math.multiplyExact(shares, 1_000_000_000L);
    }

    private static int unsignedShort(ByteBuffer message) {
        requireRemaining(message, 2);
        return Short.toUnsignedInt(message.getShort());
    }

    private static long unsignedInt(ByteBuffer message) {
        requireRemaining(message, 4);
        return Integer.toUnsignedLong(message.getInt());
    }

    private static long unsignedLong(ByteBuffer message) {
        requireRemaining(message, 8);
        return message.getLong();
    }

    private static long unsignedLong48(ByteBuffer message) {
        requireRemaining(message, 6);
        return ((long) Byte.toUnsignedInt(message.get()) << 40)
                | ((long) Byte.toUnsignedInt(message.get()) << 32)
                | ((long) Byte.toUnsignedInt(message.get()) << 24)
                | ((long) Byte.toUnsignedInt(message.get()) << 16)
                | ((long) Byte.toUnsignedInt(message.get()) << 8)
                | Byte.toUnsignedInt(message.get());
    }

    private static void skip(ByteBuffer message, int bytes) {
        requireRemaining(message, bytes);
        message.position(message.position() + bytes);
    }

    private static void requireRemaining(ByteBuffer message, int bytes) {
        if (message.remaining() < bytes) {
            throw new IllegalArgumentException("Truncated Nasdaq ITCH message");
        }
    }

    private static List<MarketDataEnvelope> singleton(MarketDataEnvelope event) {
        List<MarketDataEnvelope> events = new ArrayList<>(1);
        events.add(event);
        return events;
    }

    private static List<MarketDataEnvelope> empty() {
        return new ArrayList<>();
    }

    private static final class OrderState {
        private final String instrument;
        private final Side side;
        private final long priceNanos;
        private long remainingShares;

        private OrderState(long orderReference, String instrument, Side side, long priceNanos, long remainingShares) {
            this.instrument = instrument;
            this.side = side;
            this.priceNanos = priceNanos;
            this.remainingShares = remainingShares;
        }
    }
}
