package com.akshit.marketdata.feed;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PlannedSourceParserTest {
    @Test
    void binanceParserIsExplicitlyNotWiredYet() {
        assertThrows(UnsupportedOperationException.class, () -> new BinanceDepthParser().parse("{}"));
    }

    @Test
    void fixParserNowIgnoresNonMarketDataMessageTypes() {
        assertTrue(new FixTagValueMarketDataParser().parse("35=0\u0001").isEmpty());
    }

    @Test
    void itchParserRejectsMalformedEmptyInput() {
        assertThrows(IllegalArgumentException.class, () -> new NasdaqItchBinaryParser().parse(ByteBuffer.allocate(0)));
    }
}
