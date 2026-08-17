package com.akshit.marketdata.feed;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class NasdaqItchBinaryFileProcessorTest {
    @TempDir
    private Path tempDir;

    @Test
    void processesLengthPrefixedBinaryCapture() throws IOException {
        Path input = tempDir.resolve("itch-window.bin");
        byte[] add = addOrder(99L, 20, "NVDA", 500_0000);
        byte[] delete = deleteOrder(99L);
        try (DataOutputStream output = new DataOutputStream(Files.newOutputStream(input))) {
            output.writeShort(add.length);
            output.write(add);
            output.writeShort(delete.length);
            output.write(delete);
        }

        ParsedFeedStats stats = new NasdaqItchBinaryFileProcessor().process(input);

        assertEquals(2, stats.rawMessages());
        assertEquals(2, stats.normalizedEvents());
        assertEquals(1, stats.deletes());
        assertEquals(2, stats.normalizedEventsByInstrument().get("NVDA"));
    }

    private static byte[] addOrder(long reference, int shares, String symbol, int price) {
        ByteBuffer buffer = ByteBuffer.allocate(36).order(ByteOrder.BIG_ENDIAN);
        buffer.put((byte) 'A').putShort((short) 1).putShort((short) 2);
        buffer.put(new byte[] {0, 0, 0, 0, 0, 1});
        buffer.putLong(reference).put((byte) 'B').putInt(shares);
        byte[] stock = new byte[8];
        byte[] symbolBytes = symbol.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(symbolBytes, 0, stock, 0, symbolBytes.length);
        buffer.put(stock).putInt(price);
        return buffer.array();
    }

    private static byte[] deleteOrder(long reference) {
        ByteBuffer buffer = ByteBuffer.allocate(19).order(ByteOrder.BIG_ENDIAN);
        buffer.put((byte) 'D').putShort((short) 1).putShort((short) 2);
        buffer.put(new byte[] {0, 0, 0, 0, 0, 2});
        buffer.putLong(reference);
        return buffer.array();
    }
}
