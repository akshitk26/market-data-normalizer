package com.akshit.marketdata.feed;

import com.akshit.marketdata.proto.Action;
import com.akshit.marketdata.proto.MarketDataEnvelope;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class NasdaqItchBinaryFileProcessor {
    private final LocalDate sessionDate;

    public NasdaqItchBinaryFileProcessor() {
        this(null);
    }

    public NasdaqItchBinaryFileProcessor(LocalDate sessionDate) {
        this.sessionDate = sessionDate;
    }

    public ParsedFeedStats process(Path path) throws IOException {
        NasdaqItchBinaryParser parser = new NasdaqItchBinaryParser(sessionDate);
        int rawMessages = 0;
        int normalizedEvents = 0;
        int snapshots = 0;
        int l2Updates = 0;
        int deletes = 0;
        Map<String, Integer> statsByInstrument = new LinkedHashMap<>();

        try (DataInputStream input = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(path)))) {
            while (true) {
                int length;
                try {
                    length = input.readUnsignedShort();
                } catch (EOFException e) {
                    break;
                }
                byte[] raw = new byte[length];
                input.readFully(raw);
                rawMessages++;
                List<MarketDataEnvelope> events = parser.parse(ByteBuffer.wrap(raw));
                for (MarketDataEnvelope event : events) {
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
        return new ParsedFeedStats(rawMessages, normalizedEvents, snapshots, l2Updates, deletes, statsByInstrument);
    }
}
