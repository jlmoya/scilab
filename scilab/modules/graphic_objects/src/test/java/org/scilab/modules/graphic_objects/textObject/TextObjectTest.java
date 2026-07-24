/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Scilab Enterprises
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 *
 */

package org.scilab.modules.graphic_objects.textObject;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import org.scilab.modules.graphic_objects.graphicObject.Visitor;
import org.scilab.modules.graphic_objects.graphicObject.GraphicObject.UpdateStatus;

import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_FONT_SIZE__;
import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_FONT_STYLE__;
import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_TEXT_STRINGS__;
import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_TEXT_ARRAY_DIMENSIONS__;

/**
 * Hermetic unit tests for the abstract {@link TextObject}, exercised through a
 * tiny concrete subclass. Covers the row-major FormattedText array, its
 * dimensioning, and the "all cells share one font" get/set contract.
 */
public class TextObjectTest {

    private static final double EPS = 1e-12;

    /** Minimal concrete TextObject so the abstract base can be instantiated. */
    private static final class TestableTextObject extends TextObject {
        @Override
        public void accept(Visitor visitor) { /* no-op for tests */ }
        @Override
        public Integer getType() {
            return 0;
        }
    }

    @Test
    public void defaultsAreOneByOneEmptyAndConsideredEmpty() {
        TestableTextObject t = new TestableTextObject();
        assertArrayEquals(new Integer[] {1, 1}, t.getTextArrayDimensions());
        assertArrayEquals(new String[] {""}, t.getTextStrings());
        assertTrue(t.isEmpty());
    }

    @Test
    public void nonEmptyStringMakesObjectNotEmpty() {
        TestableTextObject t = new TestableTextObject();
        t.setTextStrings(new String[] {"hi"});
        assertFalse(t.isEmpty());
        assertArrayEquals(new String[] {"hi"}, t.getTextStrings());
    }

    @Test
    public void resizingRecreatesCellsAndPropagatesFirstCellFont() {
        TestableTextObject t = new TestableTextObject();
        // Change the (single) font, then grow the array: the new cells must
        // inherit the first cell's font values.
        t.setFontStyle(3);
        t.setFontSize(9.0);
        assertEquals(UpdateStatus.Success, t.setTextArrayDimensions(new Integer[] {2, 2}));

        assertArrayEquals(new Integer[] {2, 2}, t.getTextArrayDimensions());
        assertEquals(4, t.getTextStrings().length);
        assertArrayEquals(new String[] {"", "", "", ""}, t.getTextStrings());
        assertEquals(Integer.valueOf(3), t.getFontStyle());
        assertEquals(9.0, t.getFontSize(), EPS);
    }

    @Test
    public void setTextStringsFillsEveryCell() {
        TestableTextObject t = new TestableTextObject();
        t.setTextArrayDimensions(new Integer[] {1, 3});
        t.setTextStrings(new String[] {"a", "b", "c"});
        assertArrayEquals(new String[] {"a", "b", "c"}, t.getTextStrings());
    }

    @Test
    public void setTextInterpretersReusesLastWhenFewerSuppliedThanCells() {
        TestableTextObject t = new TestableTextObject();
        t.setTextArrayDimensions(new Integer[] {1, 3});
        // Only one interpreter supplied for 3 cells: Math.min(i, iSize-1) clamps
        // to the last supplied value, so every cell becomes "latex".
        t.setTextInterpreters(new String[] {"latex"});
        assertArrayEquals(new String[] {"latex", "latex", "latex"}, t.getTextInterpreters());
    }

    @Test
    public void interpretersDefaultToAuto() {
        TestableTextObject t = new TestableTextObject();
        t.setTextArrayDimensions(new Integer[] {1, 2});
        assertArrayEquals(new String[] {"auto", "auto"}, t.getTextInterpreters());
    }

    @Test
    public void getTextReturnsIndependentCopies() {
        TestableTextObject t = new TestableTextObject();
        t.setTextStrings(new String[] {"orig"});
        FormattedText[] snapshot = t.getText();
        snapshot[0].setText("mutated");
        // Mutating the returned copy must not corrupt internal state.
        assertArrayEquals(new String[] {"orig"}, t.getTextStrings());
    }

    @Test
    public void fontGettersReflectFirstCellDefaults() {
        TestableTextObject t = new TestableTextObject();
        // Font defaults: style 6, size 1.0, color -1, fractional true.
        assertEquals(Integer.valueOf(6), t.getFontStyle());
        assertEquals(1.0, t.getFontSize(), EPS);
        assertEquals(Integer.valueOf(-1), t.getFontColor());
        assertTrue(t.getFontFractional());
    }

    @Test
    public void fontSettersApplyToAllCells() {
        TestableTextObject t = new TestableTextObject();
        t.setTextArrayDimensions(new Integer[] {1, 2});
        t.setFontColor(7);
        t.setFontFractional(false);
        // getFontColor/getFontFractional read cell 0, but the setters wrote every
        // cell -- verify via a resize that copies cell 0 into fresh cells too.
        assertEquals(Integer.valueOf(7), t.getFontColor());
        assertFalse(t.getFontFractional());
    }

    @Test
    public void propertyDispatchRoundTripsForFontSizeAndStrings() {
        TestableTextObject t = new TestableTextObject();

        Object sizeKey = t.getPropertyFromName(__GO_FONT_SIZE__);
        assertEquals(UpdateStatus.Success, t.setProperty(sizeKey, Double.valueOf(13.5)));
        assertEquals(13.5, (Double) t.getProperty(sizeKey), EPS);

        Object styleKey = t.getPropertyFromName(__GO_FONT_STYLE__);
        t.setProperty(styleKey, Integer.valueOf(4));
        assertEquals(Integer.valueOf(4), t.getProperty(styleKey));

        Object stringsKey = t.getPropertyFromName(__GO_TEXT_STRINGS__);
        t.setProperty(stringsKey, new String[] {"z"});
        assertArrayEquals(new String[] {"z"}, (String[]) t.getProperty(stringsKey));
    }

    @Test
    public void propertyDispatchRoundTripsForArrayDimensions() {
        TestableTextObject t = new TestableTextObject();
        Object dimKey = t.getPropertyFromName(__GO_TEXT_ARRAY_DIMENSIONS__);
        t.setProperty(dimKey, new Integer[] {2, 1});
        assertArrayEquals(new Integer[] {2, 1}, (Integer[]) t.getProperty(dimKey));
    }

    @Test
    public void setFontAppliesSharedFontInstanceValues() {
        TestableTextObject t = new TestableTextObject();
        Font f = new Font();
        f.setColor(42);
        assertEquals(UpdateStatus.Success, t.setFont(f));
        assertEquals(Integer.valueOf(42), t.getFontColor());
    }
}
