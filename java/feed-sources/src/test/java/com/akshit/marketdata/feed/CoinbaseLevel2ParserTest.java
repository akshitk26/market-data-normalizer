package com.akshit.marketdata.feed;

import com.akshit.marketdata.proto.Action;
import com.akshit.marketdata.proto.MarketDataEnvelope;
import com.akshit.marketdata.proto.Side;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CoinbaseLevel2ParserTest {
    private final CoinbaseLevel2Parser parser = new CoinbaseLevel2Parser();

    @Test
    void parsesCoinbaseDocumentationSnapshot() throws IOException {
        List<String> lines = readFixtureLines();

        List<MarketDataEnvelope> events = parser.parse(lines.get(0));

        assertEquals(1, events.size());
        MarketDataEnvelope event = events.get(0);
        assertEquals(CoinbaseLevel2Parser.SOURCE_FEED, event.getSourceFeed());
        assertEquals("BTC-USD", event.getInstrument());
        assertTrue(event.hasBookSnapshot());
        assertEquals(1, event.getBookSnapshot().getBidsCount());
        assertEquals(1, event.getBookSnapshot().getAsksCount());
        assertEquals(10_101_100_000_000L, event.getBookSnapshot().getBids(0).getPriceNanos());
        assertEquals(450_541_400L, event.getBookSnapshot().getBids(0).getQuantityNanos());
    }

    @Test
    void parsesCoinbaseDocumentationSingleUpdate() throws IOException {
        List<String> lines = readFixtureLines();

        List<MarketDataEnvelope> events = parser.parse(lines.get(1));

        assertEquals(1, events.size());
        MarketDataEnvelope event = events.get(0);
        assertEquals("BTC-USD", event.getInstrument());
        assertTrue(event.hasL2Update());
        assertEquals(Side.BID, event.getL2Update().getSide());
        assertEquals(Action.MODIFY, event.getL2Update().getAction());
        assertEquals(10_101_800_000_000L, event.getL2Update().getPriceNanos());
        assertEquals(162_567_000L, event.getL2Update().getQuantityNanos());
        assertTrue(event.getEventTimeNs() > 0);
    }

    @Test
    void mapsZeroSizeUpdateToDelete() throws IOException {
        List<String> lines = readFixtureLines();

        List<MarketDataEnvelope> events = parser.parse(lines.get(2));

        assertEquals(2, events.size());
        assertEquals(Action.DELETE, events.get(0).getL2Update().getAction());
        assertEquals(0, events.get(0).getL2Update().getQuantityNanos());
        assertEquals(Action.MODIFY, events.get(1).getL2Update().getAction());
        assertEquals(1_000_000_000L, events.get(1).getL2Update().getQuantityNanos());
    }

    @Test
    void ignoresSubscriptionAcks() {
        List<MarketDataEnvelope> events = parser.parse("{\"type\":\"subscriptions\",\"channels\":[]}");

        assertTrue(events.isEmpty());
    }

    private static List<String> readFixtureLines() throws IOException {
        InputStream inputStream = CoinbaseLevel2ParserTest.class
                .getResourceAsStream("/coinbase-level2-doc-sample.jsonl");
        assertFalse(inputStream == null, "Fixture resource should exist");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.toCollection(ArrayList::new));
        }
    }
}
