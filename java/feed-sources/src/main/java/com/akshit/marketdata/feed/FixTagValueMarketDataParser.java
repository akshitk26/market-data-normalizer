package com.akshit.marketdata.feed;

import com.akshit.marketdata.core.MultiMessageFeedParser;
import com.akshit.marketdata.proto.MarketDataEnvelope;
import com.akshit.marketdata.proto.Action;
import com.akshit.marketdata.proto.BookSnapshot;
import com.akshit.marketdata.proto.L2Update;
import com.akshit.marketdata.proto.PriceLevel;
import com.akshit.marketdata.proto.Side;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class FixTagValueMarketDataParser implements MultiMessageFeedParser<String> {
    public static final String SOURCE_FEED = "gemini-fix-market-data";
    private static final DateTimeFormatter FIX_TIME_FORMATTER = new DateTimeFormatterBuilder()
            .appendPattern("yyyyMMdd-HH:mm:ss")
            .optionalStart()
            .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
            .optionalEnd()
            .toFormatter();

    @Override
    public List<MarketDataEnvelope> parse(String rawMessage) {
        if (rawMessage == null || rawMessage.trim().isEmpty()) {
            throw new IllegalArgumentException("FIX message must not be empty");
        }

        List<FixField> fields = parseFields(rawMessage);
        String messageType = required(fields, 35);
        if ("W".equals(messageType)) {
            return parseSnapshot(fields);
        }
        if ("X".equals(messageType)) {
            return parseIncremental(fields);
        }
        return new ArrayList<>();
    }

    private List<MarketDataEnvelope> parseSnapshot(List<FixField> fields) {
        String defaultInstrument = required(fields, 55);
        long sequence = optionalLong(fields, 34);
        long eventTimeNs = optionalTimeNs(fields, 52);
        BookSnapshot.Builder snapshot = BookSnapshot.newBuilder();
        int level = 0;
        for (Map<Integer, String> entry : entryGroups(fields)) {
            String instrument = entry.getOrDefault(55, defaultInstrument);
            String entryType = entry.get(269);
            if (entryType == null || "2".equals(entryType)) {
                continue;
            }
            PriceLevel priceLevel = PriceLevel.newBuilder()
                    .setPriceNanos(decimalToNanos(required(entry, 270)))
                    .setQuantityNanos(decimalToNanos(required(entry, 271)))
                    .setLevel(++level)
                    .build();
            if ("0".equals(entryType)) {
                snapshot.addBids(priceLevel);
            } else if ("1".equals(entryType)) {
                snapshot.addAsks(priceLevel);
            } else {
                throw new IllegalArgumentException("Unsupported Gemini FIX MDEntryType: " + entryType);
            }
        }

        return singleton(baseEnvelope(defaultInstrument, sequence, eventTimeNs)
                .setBookSnapshot(snapshot)
                .build());
    }

    private List<MarketDataEnvelope> parseIncremental(List<FixField> fields) {
        long sequence = optionalLong(fields, 34);
        long eventTimeNs = optionalTimeNs(fields, 52);
        List<MarketDataEnvelope> events = new ArrayList<>();
        String defaultInstrument = optional(fields, 55);
        for (Map<Integer, String> entry : entryGroups(fields)) {
            String instrument = entry.getOrDefault(55, defaultInstrument);
            if (instrument == null || instrument.isEmpty()) {
                throw new IllegalArgumentException("Gemini FIX market-data entry is missing tag 55 Symbol");
            }
            String entryType = required(entry, 269);
            if (!"0".equals(entryType) && !"1".equals(entryType) && !"2".equals(entryType)) {
                continue;
            }
            Action action = action(entry.getOrDefault(279, "0"));
            String quantity = entry.getOrDefault(271, "0");
            L2Update.Builder update = L2Update.newBuilder()
                    .setSide(side(entryType, entry.get(9002)))
                    .setAction("2".equals(entryType) ? Action.TRADE : action)
                    .setPriceNanos(decimalToNanos(required(entry, 270)))
                    .setQuantityNanos(decimalToNanos(quantity))
                    .setLevel(0);
            long entryTimeNs = optionalTimeNs(entry, 273, eventTimeNs);
            events.add(baseEnvelope(instrument, sequence, entryTimeNs)
                    .setL2Update(update)
                    .build());
        }
        return events;
    }

    private static List<Map<Integer, String>> entryGroups(List<FixField> fields) {
        List<Map<Integer, String>> groups = new ArrayList<>();
        Map<Integer, String> current = null;
        for (FixField field : fields) {
            if (field.tag == 279 || (field.tag == 269 && current != null && current.containsKey(269))) {
                current = new HashMap<>();
                groups.add(current);
            }
            if (field.tag == 269 && current == null) {
                current = new HashMap<>();
                groups.add(current);
            }
            if (current != null) {
                current.put(field.tag, field.value);
            }
        }
        return groups;
    }

    private static List<FixField> parseFields(String rawMessage) {
        String normalized = rawMessage.replace('|', '\u0001').trim();
        String[] rawFields = normalized.split("\\u0001");
        List<FixField> fields = new ArrayList<>(rawFields.length);
        for (String rawField : rawFields) {
            if (rawField.isEmpty()) {
                continue;
            }
            int separator = rawField.indexOf('=');
            if (separator <= 0) {
                throw new IllegalArgumentException("Malformed FIX field: " + rawField);
            }
            try {
                fields.add(new FixField(
                        Integer.parseInt(rawField.substring(0, separator)),
                        rawField.substring(separator + 1)));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid FIX tag: " + rawField, e);
            }
        }
        return fields;
    }

    private static MarketDataEnvelope.Builder baseEnvelope(String instrument, long sequence, long eventTimeNs) {
        MarketDataEnvelope.Builder envelope = MarketDataEnvelope.newBuilder()
                .setSourceFeed(SOURCE_FEED)
                .setInstrument(instrument)
                .setReceiveTimeNs(System.currentTimeMillis() * 1_000_000L);
        if (sequence > 0) {
            envelope.setSequenceNumber(sequence);
        }
        if (eventTimeNs > 0) {
            envelope.setEventTimeNs(eventTimeNs);
        }
        return envelope;
    }

    private static Side side(String entryType, String makerSide) {
        if ("0".equals(entryType)) {
            return Side.BID;
        }
        if ("1".equals(entryType)) {
            return Side.ASK;
        }
        if ("2".equals(entryType) && "1".equals(makerSide)) {
            return Side.BID;
        }
        if ("2".equals(entryType) && "2".equals(makerSide)) {
            return Side.ASK;
        }
        return Side.SIDE_UNSPECIFIED;
    }

    private static Action action(String value) {
        switch (value) {
            case "0":
                return Action.ADD;
            case "1":
                return Action.MODIFY;
            case "2":
                return Action.DELETE;
            default:
                throw new IllegalArgumentException("Unsupported Gemini FIX MDUpdateAction: " + value);
        }
    }

    private static long optionalLong(List<FixField> fields, int tag) {
        String value = optional(fields, tag);
        return value == null ? 0 : Long.parseLong(value);
    }

    private static long optionalTimeNs(List<FixField> fields, int tag) {
        String value = optional(fields, tag);
        if (value == null) {
            return 0;
        }
        LocalDateTime timestamp = LocalDateTime.parse(value, FIX_TIME_FORMATTER);
        Instant instant = timestamp.toInstant(ZoneOffset.UTC);
        return instant.getEpochSecond() * 1_000_000_000L + instant.getNano();
    }

    private static long optionalTimeNs(Map<Integer, String> fields, int tag, long fallback) {
        String value = fields.get(tag);
        if (value == null || value.isEmpty()) {
            return fallback;
        }
        LocalDateTime timestamp = LocalDateTime.parse(value, FIX_TIME_FORMATTER);
        Instant instant = timestamp.toInstant(ZoneOffset.UTC);
        return instant.getEpochSecond() * 1_000_000_000L + instant.getNano();
    }

    private static String required(List<FixField> fields, int tag) {
        String value = optional(fields, tag);
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("Gemini FIX message missing required tag " + tag);
        }
        return value;
    }

    private static String required(Map<Integer, String> fields, int tag) {
        String value = fields.get(tag);
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("Gemini FIX entry missing required tag " + tag);
        }
        return value;
    }

    private static String optional(List<FixField> fields, int tag) {
        for (FixField field : fields) {
            if (field.tag == tag) {
                return field.value;
            }
        }
        return null;
    }

    private static long decimalToNanos(String decimal) {
        return FixedPointDecimal.toNanos(decimal);
    }

    private static List<MarketDataEnvelope> singleton(MarketDataEnvelope event) {
        List<MarketDataEnvelope> events = new ArrayList<>(1);
        events.add(event);
        return events;
    }

    private static final class FixField {
        private final int tag;
        private final String value;

        private FixField(int tag, String value) {
            this.tag = tag;
            this.value = value;
        }
    }
}
