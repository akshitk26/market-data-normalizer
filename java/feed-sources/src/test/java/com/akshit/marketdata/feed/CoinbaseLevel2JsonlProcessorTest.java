package com.akshit.marketdata.feed;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class CoinbaseLevel2JsonlProcessorTest {
    @TempDir
    private Path tempDir;

    @Test
    void processesJsonlCaptureIntoNormalizedStats() throws IOException {
        Path fixture = tempDir.resolve("coinbase-level2-doc-sample.jsonl");
        try (InputStream inputStream = CoinbaseLevel2JsonlProcessorTest.class
                .getResourceAsStream("/coinbase-level2-doc-sample.jsonl")) {
            assertFalse(inputStream == null, "Fixture resource should exist");
            Files.copy(inputStream, fixture);
        }

        CoinbaseLevel2JsonlProcessor processor = new CoinbaseLevel2JsonlProcessor(new CoinbaseLevel2Parser());

        ParsedFeedStats stats = processor.process(fixture);

        assertEquals(3, stats.rawMessages());
        assertEquals(4, stats.normalizedEvents());
        assertEquals(1, stats.snapshots());
        assertEquals(3, stats.l2Updates());
        assertEquals(1, stats.deletes());
        assertEquals(4, stats.normalizedEventsByInstrument().get("BTC-USD"));
    }
}
