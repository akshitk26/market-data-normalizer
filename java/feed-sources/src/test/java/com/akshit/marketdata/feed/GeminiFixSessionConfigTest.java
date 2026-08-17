package com.akshit.marketdata.feed;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GeminiFixSessionConfigTest {
    @Test
    void loadsNonSecretSessionSettingsFromEnvironment() {
        Map<String, String> environment = new HashMap<>();
        environment.put("GEMINI_FIX_HOST", "fix.example.test");
        environment.put("GEMINI_FIX_PORT", "1234");
        environment.put("GEMINI_FIX_SENDER_COMP_ID", "CLIENT");
        environment.put("GEMINI_FIX_TARGET_COMP_ID", "GEMINI");
        environment.put("GEMINI_FIX_SYMBOLS", "BTCUSD, ETHUSD");
        environment.put("GEMINI_FIX_OUTPUT", "data/fix/live.jsonl");
        environment.put("GEMINI_FIX_SEQUENCE_FILE", "data/fix/sequence.properties");

        GeminiFixSessionConfig config = GeminiFixSessionConfig.fromEnvironment(environment);

        assertEquals("fix.example.test", config.host());
        assertEquals(1234, config.port());
        assertEquals(2, config.symbols().size());
        assertTrue(config.transportTls());
        assertEquals(Path.of("data/fix/live.jsonl"), config.output());
    }
}
