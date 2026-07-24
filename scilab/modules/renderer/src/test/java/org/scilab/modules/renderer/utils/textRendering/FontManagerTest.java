/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Jose Moya
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 *
 */

package org.scilab.modules.renderer.utils.textRendering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Font;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link FontManager}.
 *
 * <p>The heart of the class is a pair of pure piecewise-linear converters
 * ({@code scilabSizeToAwtSize} / {@code awtSizeToScilabSize}) that map
 * Scilab's font-size index (0..5) onto AWT point sizes and back; those are
 * plain arithmetic and need no display. The singleton's font list is
 * exercised too: it initialises from logical AWT fonts (Monospaced / Serif /
 * SansSerif) which resolve under {@code java.awt.headless=true}, so the
 * index clamping and list-mutation helpers run without a live GUI.
 *
 * <p>The singleton's {@code sciFonts} list is process-global mutable state,
 * so every test re-seeds it via {@code initializeFontManager()} first.
 */
class FontManagerTest {

    /** The documented seed list size (styles 0..10). */
    private static final int SEEDED_FONT_COUNT = 11;

    private static final double EPS = 1e-4;

    @BeforeEach
    void reseedSingletonFontList() {
        FontManager.getSciFontManager().initializeFontManager();
    }

    // ----- pure size converters -------------------------------------------

    @Test
    void scilabSizeToAwtSizeMatchesTheDocumentedEquivalenceTable() {
        // (0 => 8, 1 => 10, 2 => 12, 3 => 14, 4 => 18, 5 => 24)
        assertEquals(8.0, FontManager.scilabSizeToAwtSize(0.0), EPS);
        assertEquals(10.0, FontManager.scilabSizeToAwtSize(1.0), EPS);
        assertEquals(12.0, FontManager.scilabSizeToAwtSize(2.0), EPS);
        assertEquals(14.0, FontManager.scilabSizeToAwtSize(3.0), EPS);
        assertEquals(18.0, FontManager.scilabSizeToAwtSize(4.0), EPS);
        assertEquals(24.0, FontManager.scilabSizeToAwtSize(5.0), EPS);
    }

    @Test
    void scilabSizeToAwtSizeIsPiecewiseLinearBetweenKnots() {
        // First segment (x < 3): 2x + 8.
        assertEquals(13.0, FontManager.scilabSizeToAwtSize(2.5), EPS);
        // Second segment (3 <= x < 4): 4x + 2.
        assertEquals(16.0, FontManager.scilabSizeToAwtSize(3.5), EPS);
        // Third segment (4 <= x < 5): 6x - 6.
        assertEquals(21.0, FontManager.scilabSizeToAwtSize(4.5), EPS);
        // Fourth segment (x >= 5): 10x - 26.
        assertEquals(34.0, FontManager.scilabSizeToAwtSize(6.0), EPS);
    }

    @Test
    void scilabSizeToAwtSizeIsContinuousAtEachKnot() {
        // Approaching a knot from below must meet the next segment's value.
        assertEquals(FontManager.scilabSizeToAwtSize(3.0),
                     FontManager.scilabSizeToAwtSize(2.9999), 1e-3);
        assertEquals(FontManager.scilabSizeToAwtSize(4.0),
                     FontManager.scilabSizeToAwtSize(3.9999), 1e-3);
        assertEquals(FontManager.scilabSizeToAwtSize(5.0),
                     FontManager.scilabSizeToAwtSize(4.9999), 1e-3);
    }

    @Test
    void scilabSizeToAwtSizeExtrapolatesBelowZeroWithTheFirstSegment() {
        // Characterises the unclamped behaviour: 2x + 8 for x = -1 -> 6.
        assertEquals(6.0, FontManager.scilabSizeToAwtSize(-1.0), EPS);
    }

    @Test
    void awtSizeToScilabSizeInvertsTheEquivalenceTable() {
        assertEquals(0.0, FontManager.awtSizeToScilabSize(8.0f), EPS);
        assertEquals(1.0, FontManager.awtSizeToScilabSize(10.0f), EPS);
        assertEquals(2.0, FontManager.awtSizeToScilabSize(12.0f), EPS);
        assertEquals(3.0, FontManager.awtSizeToScilabSize(14.0f), EPS);
        assertEquals(4.0, FontManager.awtSizeToScilabSize(18.0f), EPS);
        assertEquals(5.0, FontManager.awtSizeToScilabSize(24.0f), EPS);
    }

    @Test
    void sizeConvertersRoundTripAcrossEveryScilabIndex() {
        for (int k = 0; k <= 5; k++) {
            float awt = FontManager.scilabSizeToAwtSize(k);
            assertEquals(k, FontManager.awtSizeToScilabSize(awt), EPS,
                         "round trip failed at scilab index " + k);
        }
    }

    // ----- singleton & font-list surface ----------------------------------

    @Test
    void singletonAccessorAlwaysReturnsTheSameInstance() {
        FontManager a = FontManager.getSciFontManager();
        FontManager b = FontManager.getSciFontManager();
        assertNotNull(a);
        assertSame(a, b);
    }

    @Test
    void freshlySeededManagerHoldsTheDocumentedElevenFonts() {
        assertEquals(SEEDED_FONT_COUNT,
                     FontManager.getSciFontManager().getSizeInstalledFontsName());
    }

    @Test
    void getFontFromIndexClampsOutOfRangeIndicesToTheEndpoints() {
        FontManager fm = FontManager.getSciFontManager();
        Font first = fm.getFontFromIndex(0);
        Font last = fm.getFontFromIndex(SEEDED_FONT_COUNT - 1);
        assertNotNull(first);
        assertNotNull(last);
        // Negative -> first entry; beyond the end -> last entry.
        assertSame(first, fm.getFontFromIndex(-1));
        assertSame(first, fm.getFontFromIndex(-999));
        assertSame(last, fm.getFontFromIndex(SEEDED_FONT_COUNT));
        assertSame(last, fm.getFontFromIndex(10_000));
    }

    @Test
    void getFontFromIndexWithSizeDerivesToTheConvertedAwtSize() {
        // Scilab size 3 -> AWT 14pt; the derived font must carry that size.
        Font derived = FontManager.getSciFontManager().getFontFromIndex(0, 3.0);
        assertEquals(14.0f, derived.getSize2D(), 1e-3);
    }

    @Test
    void addFontAppendsAndReturnsTheNewIndex() {
        FontManager fm = FontManager.getSciFontManager();
        int before = fm.getSizeInstalledFontsName();
        int index = fm.addFont(new Font("Serif", Font.PLAIN, 1));
        assertEquals(before, index, "addFont returns the index of the appended font");
        assertEquals(before + 1, fm.getSizeInstalledFontsName());
    }

    @Test
    void changeFontInPlaceReplacesAnExistingSlotWithoutGrowing() {
        FontManager fm = FontManager.getSciFontManager();
        int before = fm.getSizeInstalledFontsName();
        Font replacement = new Font("Serif", Font.ITALIC, 1);
        int index = fm.changeFont(0, replacement);
        assertEquals(0, index);
        assertEquals(before, fm.getSizeInstalledFontsName(), "in-place change must not grow the list");
        assertSame(replacement, fm.getFontFromIndex(0));
    }

    @Test
    void changeFontAtTheEndAppendsAsANewFont() {
        FontManager fm = FontManager.getSciFontManager();
        int size = fm.getSizeInstalledFontsName();
        Font added = new Font("SansSerif", Font.PLAIN, 1);
        int index = fm.changeFont(size, added);
        assertEquals(size, index);
        assertEquals(size + 1, fm.getSizeInstalledFontsName());
        assertSame(added, fm.getFontFromIndex(size));
    }

    @Test
    void changeFontBeyondTheEndPadsWithDefaultFontsThenAppends() {
        FontManager fm = FontManager.getSciFontManager();
        int size = fm.getSizeInstalledFontsName();
        int target = size + 2;
        Font added = new Font("SansSerif", Font.BOLD, 1);
        int index = fm.changeFont(target, added);
        assertEquals(target, index);
        // Slots [size, target) are padded, and the new font lands at target.
        assertEquals(target + 1, fm.getSizeInstalledFontsName());
        assertSame(added, fm.getFontFromIndex(target));
    }

    @Test
    void getInstalledFontsNameEncodesStyleAsANameSuffix() {
        // Seed order fixes: index 3 = Serif italic, 4 = Serif bold,
        // 5 = Serif bold-italic. The suffix comes from the Font style bits,
        // which are platform-independent.
        String[] names = FontManager.getSciFontManager().getInstalledFontsName();
        assertEquals(SEEDED_FONT_COUNT, names.length);
        for (String n : names) {
            assertNotNull(n);
        }
        assertTrue(names[3].endsWith(" Italic"), "italic-only slot: " + names[3]);
        assertTrue(!names[3].endsWith(" Bold Italic"), "italic-only must not be bold: " + names[3]);
        assertTrue(names[4].endsWith(" Bold"), "bold-only slot: " + names[4]);
        assertTrue(names[5].endsWith(" Bold Italic"), "bold-italic slot: " + names[5]);
    }
}
