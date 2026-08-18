package com.akshit.marketdata.feed;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class CoinbaseToGeminiFixTranslatorTest {
    private final CoinbaseToGeminiFixTranslator translator = new CoinbaseToGeminiFixTranslator();

    @Test
    void convertsLiveShapeSnapshotToFixSnapshot() {
        String json = "{\"type\":\"snapshot\",\"product_id\":\"BTC-USD\","
                + "\"time\":\"2026-08-18T12:00:00.123Z\","
                + "\"bids\":[[\"100.10\",\"2.5\"]],"
                + "\"asks\":[[\"100.20\",\"1.25\"]]}";

        String fix = translator.translate(json, 2, "LOCAL-CLIENT", "local-1");

        assertEquals("W", GeminiFixMessageCodec.field(fix, 35));
        assertEquals("2", GeminiFixMessageCodec.field(fix, 34));
        assertEquals("BTCUSD", GeminiFixMessageCodec.field(fix, 55));
        assertEquals("2", GeminiFixMessageCodec.field(fix, 268));
        assertEquals(List.of("0", "1"), GeminiFixMessageCodec.fields(fix, 269));
        assertEquals("100.10", GeminiFixMessageCodec.field(fix, 270));
        assertEquals("LOCAL-CLIENT", GeminiFixMessageCodec.field(fix, 56));
    }

    @Test
    void convertsAbsoluteLevelChangesToFixIncrementalActions() {
        String json = "{\"type\":\"l2update\",\"product_id\":\"ETH-USD\","
                + "\"time\":\"2026-08-18T12:00:00.123Z\",\"changes\":["
                + "[\"buy\",\"2000.10\",\"0\"],"
                + "[\"sell\",\"2000.20\",\"1.5\"]]}";

        String fix = translator.translate(json, 3, "LOCAL-CLIENT", "local-2");

        assertEquals("X", GeminiFixMessageCodec.field(fix, 35));
        assertEquals(List.of("2", "1"), GeminiFixMessageCodec.fields(fix, 279));
        assertEquals(List.of("0", "1"), GeminiFixMessageCodec.fields(fix, 269));
        assertEquals("2", GeminiFixMessageCodec.field(fix, 268));
    }

    @Test
    void ignoresSubscriptionAcknowledgements() {
        assertNull(translator.translate("{\"type\":\"subscriptions\"}", 2, "LOCAL-CLIENT", "local-1"));
    }
}
