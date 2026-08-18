package com.akshit.marketdata.feed;

import com.akshit.marketdata.proto.Action;
import com.akshit.marketdata.proto.MarketDataEnvelope;
import com.akshit.marketdata.proto.Side;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class NasdaqItchBinaryParserTest {
    @Test
    void parsesOrderLifecycleIntoNormalizedEvents() {
        NasdaqItchBinaryParser parser = new NasdaqItchBinaryParser(LocalDate.of(2026, 6, 12));

        MarketDataEnvelope add = parser.parse(addOrder(42L, 'B', 100, "AAPL", 123_4567)).get(0);
        MarketDataEnvelope cancel = parser.parse(cancelOrder(42L, 25)).get(0);
        MarketDataEnvelope execute = parser.parse(executeOrder(42L, 75)).get(0);

        assertEquals(Action.ADD, add.getL2Update().getAction());
        assertEquals(Side.BID, add.getL2Update().getSide());
        assertEquals("AAPL", add.getInstrument());
        assertEquals(2, add.getSequenceNumber());
        assertEquals(123_456_700_000L, add.getL2Update().getPriceNanos());
        assertEquals(100_000_000_000L, add.getL2Update().getQuantityNanos());
        assertTrue(add.getEventTimeNs() > 0);

        assertEquals(Action.MODIFY, cancel.getL2Update().getAction());
        assertEquals(75_000_000_000L, cancel.getL2Update().getQuantityNanos());
        assertEquals(Action.TRADE, execute.getL2Update().getAction());
        assertEquals(75_000_000_000L, execute.getL2Update().getQuantityNanos());
        assertTrue(parser.parse(deleteOrder(42L)).isEmpty());
    }

    @Test
    void parsesDeleteAndReplaceMessages() {
        NasdaqItchBinaryParser parser = new NasdaqItchBinaryParser();
        parser.parse(addOrder(7L, 'S', 10, "MSFT", 300_0000));

        List<MarketDataEnvelope> replace = parser.parse(replaceOrder(7L, 8L, 12, 301_0000));

        assertEquals(2, replace.size());
        assertEquals(Action.DELETE, replace.get(0).getL2Update().getAction());
        assertEquals(Action.ADD, replace.get(1).getL2Update().getAction());
        assertEquals(12_000_000_000L, replace.get(1).getL2Update().getQuantityNanos());
        assertEquals(301_000_000_000L, replace.get(1).getL2Update().getPriceNanos());

        MarketDataEnvelope delete = parser.parse(deleteOrder(8L)).get(0);
        assertEquals(Action.DELETE, delete.getL2Update().getAction());
        assertTrue(parser.parse(deleteOrder(8L)).isEmpty());
    }

    @Test
    void parsesCrossTradeWithEightByteShareCount() {
        NasdaqItchBinaryParser parser = new NasdaqItchBinaryParser();
        ByteBuffer message = ByteBuffer.allocate(40).order(ByteOrder.BIG_ENDIAN);
        message.put((byte) 'Q').putShort((short) 1).putShort((short) 2)
                .put(new byte[] {0, 0, 0, 0, 0, 1})
                .putLong(3_000_000_000L)
                .put("AAPL    ".getBytes(StandardCharsets.US_ASCII))
                .putInt(123_4567)
                .putLong(77L)
                .put((byte) 'O');

        MarketDataEnvelope event = parser.parse(flip(message)).get(0);

        assertEquals("AAPL", event.getInstrument());
        assertEquals(3_000_000_000_000_000_000L, event.getL2Update().getQuantityNanos());
        assertEquals(Side.SIDE_UNSPECIFIED, event.getL2Update().getSide());
    }

    private static ByteBuffer addOrder(long reference, char side, int shares, String symbol, int price) {
        ByteBuffer buffer = header('A', 36).putLong(reference).put((byte) side).putInt(shares);
        putText(buffer, symbol, 8);
        buffer.putInt(price);
        return flip(buffer);
    }

    private static ByteBuffer cancelOrder(long reference, int shares) {
        return flip(header('X', 23).putLong(reference).putInt(shares));
    }

    private static ByteBuffer executeOrder(long reference, int shares) {
        return flip(header('E', 31).putLong(reference).putInt(shares).putLong(999L));
    }

    private static ByteBuffer deleteOrder(long reference) {
        return flip(header('D', 19).putLong(reference));
    }

    private static ByteBuffer replaceOrder(long original, long replacement, int shares, int price) {
        return flip(header('U', 35).putLong(original).putLong(replacement).putInt(shares).putInt(price));
    }

    private static ByteBuffer header(char type, int expectedLength) {
        ByteBuffer buffer = ByteBuffer.allocate(expectedLength).order(ByteOrder.BIG_ENDIAN);
        buffer.put((byte) type).putShort((short) 1).putShort((short) 2);
        buffer.put(new byte[] {0, 0, 0, 0, 0, 1});
        return buffer;
    }

    private static void putText(ByteBuffer buffer, String value, int length) {
        byte[] bytes = new byte[length];
        byte[] source = value.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(source, 0, bytes, 0, Math.min(source.length, length));
        buffer.put(bytes);
    }

    private static ByteBuffer flip(ByteBuffer buffer) {
        buffer.flip();
        return buffer;
    }
}
