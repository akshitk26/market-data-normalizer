package com.akshit.marketdata.feed;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class GeminiFixJsonlProcessorTest {
    @TempDir
    private Path tempDir;

    @Test
    void processesSnapshotAndIncrementalGeminiMessagesWithoutReplay() throws IOException {
        Path input = tempDir.resolve("gemini.fix.jsonl");
        Files.write(input, Arrays.asList(
                "8=FIX.4.4|9=108|35=W|34=2|49=GEMINI|52=20180425-17:51:40.787|56=TEST|55=BTCUSD|262=2|268=1|269=0|270=8490.07|271=1|10=075|",
                "8=FIX.4.4|9=125|35=X|34=3|49=GEMINI|52=20180809-15:59:16.698|56=TEST|262=2|268=1|279=2|269=0|55=BTCUSD|270=7544.94|10=107|"),
                StandardCharsets.UTF_8);

        ParsedFeedStats stats = new GeminiFixJsonlProcessor(new FixTagValueMarketDataParser()).process(input);

        assertEquals(2, stats.rawMessages());
        assertEquals(2, stats.normalizedEvents());
        assertEquals(1, stats.snapshots());
        assertEquals(1, stats.l2Updates());
        assertEquals(1, stats.deletes());
        assertEquals(2, stats.normalizedEventsByInstrument().get("BTCUSD"));
    }
}
