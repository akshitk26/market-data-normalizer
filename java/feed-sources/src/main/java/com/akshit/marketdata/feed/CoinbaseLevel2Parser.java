package com.akshit.marketdata.feed;

import com.akshit.marketdata.core.MultiMessageFeedParser;
import com.akshit.marketdata.proto.Action;
import com.akshit.marketdata.proto.BookSnapshot;
import com.akshit.marketdata.proto.L2Update;
import com.akshit.marketdata.proto.MarketDataEnvelope;
import com.akshit.marketdata.proto.PriceLevel;
import com.akshit.marketdata.proto.Side;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CoinbaseLevel2Parser implements MultiMessageFeedParser<String> {
    public static final String SOURCE_FEED = "coinbase-level2-batch";

    private final ObjectMapper objectMapper;

    public CoinbaseLevel2Parser() {
        this(new ObjectMapper());
    }

    CoinbaseLevel2Parser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public List<MarketDataEnvelope> parse(String rawMessage) {
        try {
            JsonNode root = objectMapper.readTree(rawMessage);
            String type = requiredText(root, "type");
            if ("snapshot".equals(type)) {
                return Collections.singletonList(parseSnapshot(root));
            }
            if ("l2update".equals(type)) {
                return parseUpdate(root);
            }
            return Collections.emptyList();
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid Coinbase JSON message", e);
        }
    }

    private MarketDataEnvelope parseSnapshot(JsonNode root) {
        BookSnapshot.Builder snapshot = BookSnapshot.newBuilder();
        appendPriceLevels(snapshot, root.path("bids"), true);
        appendPriceLevels(snapshot, root.path("asks"), false);

        return baseEnvelope(root)
                .setBookSnapshot(snapshot)
                .build();
    }

    private List<MarketDataEnvelope> parseUpdate(JsonNode root) {
        JsonNode changes = root.path("changes");
        if (!changes.isArray()) {
            throw new IllegalArgumentException("Coinbase l2update message must include changes array");
        }

        List<MarketDataEnvelope> events = new ArrayList<>(changes.size());
        for (int index = 0; index < changes.size(); index++) {
            JsonNode change = changes.get(index);
            if (!change.isArray() || change.size() != 3) {
                throw new IllegalArgumentException("Coinbase change must be [side, price, size]");
            }

            String size = change.get(2).asText();
            L2Update update = L2Update.newBuilder()
                    .setSide(parseSide(change.get(0).asText()))
                    .setAction(isZero(size) ? Action.DELETE : Action.MODIFY)
                    .setPriceNanos(decimalToNanos(change.get(1).asText()))
                    .setQuantityNanos(decimalToNanos(size))
                    .setLevel(0)
                    .build();

            events.add(baseEnvelope(root)
                    .setL2Update(update)
                    .build());
        }
        return events;
    }

    private MarketDataEnvelope.Builder baseEnvelope(JsonNode root) {
        MarketDataEnvelope.Builder envelope = MarketDataEnvelope.newBuilder()
                .setSourceFeed(SOURCE_FEED)
                .setInstrument(requiredText(root, "product_id"))
                .setReceiveTimeNs(System.currentTimeMillis() * 1_000_000L);

        if (root.hasNonNull("sequence")) {
            envelope.setSequenceNumber(root.get("sequence").asLong());
        }
        if (root.hasNonNull("time")) {
            envelope.setEventTimeNs(instantToEpochNanos(Instant.parse(root.get("time").asText())));
        }
        return envelope;
    }

    private static void appendPriceLevels(BookSnapshot.Builder snapshot, JsonNode levels, boolean bid) {
        if (!levels.isArray()) {
            throw new IllegalArgumentException("Coinbase snapshot bids/asks must be arrays");
        }
        for (int index = 0; index < levels.size(); index++) {
            JsonNode level = levels.get(index);
            if (!level.isArray() || level.size() != 2) {
                throw new IllegalArgumentException("Coinbase snapshot level must be [price, size]");
            }

            PriceLevel priceLevel = PriceLevel.newBuilder()
                    .setPriceNanos(decimalToNanos(level.get(0).asText()))
                    .setQuantityNanos(decimalToNanos(level.get(1).asText()))
                    .setLevel(index + 1)
                    .build();

            if (bid) {
                snapshot.addBids(priceLevel);
            } else {
                snapshot.addAsks(priceLevel);
            }
        }
    }

    private static String requiredText(JsonNode root, String fieldName) {
        if (!root.hasNonNull(fieldName)) {
            throw new IllegalArgumentException("Coinbase message missing required field: " + fieldName);
        }
        return root.get(fieldName).asText();
    }

    private static Side parseSide(String coinbaseSide) {
        if ("buy".equals(coinbaseSide)) {
            return Side.BID;
        }
        if ("sell".equals(coinbaseSide)) {
            return Side.ASK;
        }
        throw new IllegalArgumentException("Unknown Coinbase side: " + coinbaseSide);
    }

    private static boolean isZero(String decimal) {
        return BigDecimal.ZERO.compareTo(new BigDecimal(decimal)) == 0;
    }

    private static long decimalToNanos(String decimal) {
        return new BigDecimal(decimal)
                .movePointRight(9)
                .setScale(0, RoundingMode.UNNECESSARY)
                .longValueExact();
    }

    private static long instantToEpochNanos(Instant instant) {
        return instant.getEpochSecond() * 1_000_000_000L + instant.getNano();
    }
}
