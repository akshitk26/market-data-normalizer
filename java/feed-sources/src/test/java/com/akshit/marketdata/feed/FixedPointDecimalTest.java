package com.akshit.marketdata.feed;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class FixedPointDecimalTest {
    @Test
    void parsesAndPadsFractionalDigits() {
        assertEquals(1_250_000_000L, FixedPointDecimal.toNanos("1.25"));
        assertEquals(1L, FixedPointDecimal.toNanos("0.000000001"));
        assertEquals(-2_500_000_000L, FixedPointDecimal.toNanos("-2.5"));
    }

    @Test
    void rejectsLossyPrecision() {
        assertThrows(IllegalArgumentException.class, () -> FixedPointDecimal.toNanos("1.0000000001"));
    }

    @Test
    void rejectsMalformedDecimalWithoutDigits() {
        assertThrows(IllegalArgumentException.class, () -> FixedPointDecimal.toNanos("."));
    }
}
