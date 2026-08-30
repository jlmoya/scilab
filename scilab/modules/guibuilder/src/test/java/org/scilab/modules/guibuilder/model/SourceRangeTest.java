package org.scilab.modules.guibuilder.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class SourceRangeTest {

    @Test
    public void lengthIsEndMinusStart() {
        assertEquals(5, new SourceRange(10, 15).length());
    }

    @Test
    public void anEmptyRangeIsLegalBecauseAnInsertionPointIsOne() {
        assertEquals(0, new SourceRange(7, 7).length());
    }

    @Test
    public void rangesAreHalfOpenSoTouchingRangesDoNotOverlap() {
        // [0,5) and [5,10) are adjacent. If these counted as overlapping, two
        // edits to neighbouring properties would be rejected for no reason.
        assertFalse(new SourceRange(0, 5).overlaps(new SourceRange(5, 10)));
        assertTrue(new SourceRange(0, 6).overlaps(new SourceRange(5, 10)));
    }

    @Test
    public void containsUsesTheSameHalfOpenRule() {
        SourceRange r = new SourceRange(3, 6);
        assertTrue(r.contains(3));
        assertTrue(r.contains(5));
        assertFalse(r.contains(6));
    }

    @Test
    public void negativeOrInvertedRangesAreRejectedAtConstruction() {
        assertThrows(IllegalArgumentException.class, () -> new SourceRange(-1, 4));
        assertThrows(IllegalArgumentException.class, () -> new SourceRange(9, 4));
    }
}
