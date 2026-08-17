package com.akshit.marketdata.feed;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GeminiFixMessageCodecTest {
    @Test
    void buildsAndReadsFramedFixMessage() throws Exception {
        String message = GeminiFixMessageCodec.build(Arrays.asList(
                "35=0",
                "34=1",
                "49=SENDER",
                "52=20260817-12:00:00.000",
                "56=GEMINI"));

        assertTrue(message.startsWith("8=FIX.4.4\u00019="));
        assertEquals(message, GeminiFixMessageCodec.read(
                new ByteArrayInputStream(message.getBytes(StandardCharsets.US_ASCII))));
        assertEquals("0", GeminiFixMessageCodec.field(message, 35));
        assertEquals("1", GeminiFixMessageCodec.field(message, 34));
    }

    @Test
    void rejectsChecksumMismatch() throws Exception {
        String message = GeminiFixMessageCodec.build(Arrays.asList("35=0", "34=1"));
        int checksumIndex = message.lastIndexOf("10=");
        String corrupt = message.substring(0, checksumIndex) + "10=999\u0001";

        assertThrows(java.io.IOException.class, () -> GeminiFixMessageCodec.read(
                new ByteArrayInputStream(corrupt.getBytes(StandardCharsets.US_ASCII))));
    }
}
