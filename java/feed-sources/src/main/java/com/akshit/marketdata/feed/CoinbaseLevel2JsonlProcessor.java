package com.akshit.marketdata.feed;

import com.akshit.marketdata.proto.Action;
import com.akshit.marketdata.proto.MarketDataEnvelope;
import com.akshit.marketdata.core.NormalizedEventPipeline;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CoinbaseLevel2JsonlProcessor {
    private final CoinbaseLevel2Parser parser;
    private final NormalizedEventPipeline pipeline;

    public CoinbaseLevel2JsonlProcessor(CoinbaseLevel2Parser parser) {
        this(parser, new NormalizedEventPipeline());
    }

    public CoinbaseLevel2JsonlProcessor(CoinbaseLevel2Parser parser, NormalizedEventPipeline pipeline) {
        this.parser = parser;
        this.pipeline = pipeline;
    }

    public ParsedFeedStats process(Path path) throws IOException {
        int rawMessages = 0;
        int normalizedEvents = 0;
        int snapshots = 0;
        int l2Updates = 0;
        int deletes = 0;
        Map<String, Integer> statsByInstrument = new LinkedHashMap<>();

        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                rawMessages++;
                List<MarketDataEnvelope> events = parser.parse(line);
                for (MarketDataEnvelope event : events) {
                    pipeline.accept(event);
                    normalizedEvents++;
                    statsByInstrument.merge(event.getInstrument(), 1, Integer::sum);
                    if (event.hasBookSnapshot()) {
                        snapshots++;
                    }
                    if (event.hasL2Update()) {
                        l2Updates++;
                        if (event.getL2Update().getAction() == Action.DELETE) {
                            deletes++;
                        }
                    }
                }
            }
        }

        return new ParsedFeedStats(rawMessages, normalizedEvents, snapshots, l2Updates, deletes, statsByInstrument,
                pipeline.sequenceGaps(), pipeline.orderBookVerifier().report().desynchronizedEvents(),
                pipeline.orderBookVerifier().report().lastError());
    }

    public NormalizedEventPipeline pipeline() { return pipeline; }
}
