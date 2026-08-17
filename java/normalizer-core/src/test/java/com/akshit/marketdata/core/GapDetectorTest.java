package com.akshit.marketdata.core;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GapDetectorTest {
    @Test
    void detectsMissingRangeWhenSequenceJumps() {
        GapDetector detector = new GapDetector(1);

        assertFalse(detector.observe(1).isPresent());
        assertFalse(detector.observe(2).isPresent());
        assertFalse(detector.observe(3).isPresent());

        Optional<SequenceGap> gap = detector.observe(7);

        assertTrue(gap.isPresent());
        assertEquals(4, gap.get().missingFrom());
        assertEquals(6, gap.get().missingTo());
        assertEquals(7, gap.get().observedSequence());
        assertEquals(8, detector.expectedNextSequence());
    }
}
