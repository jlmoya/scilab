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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link XlFontManager}, the thin {@code xlfont}
 * facade that forwards every call to the {@link FontManager} singleton. The
 * facade shares that singleton's process-global font list, so each test
 * re-seeds it first. Font enumeration goes through {@code GraphicsEnvironment},
 * which resolves under {@code java.awt.headless=true}; no display is needed.
 */
class XlFontManagerTest {

    /** The documented seed list size (styles 0..10). */
    private static final int SEEDED_FONT_COUNT = 11;

    private XlFontManager xl;

    @BeforeEach
    void freshFacadeOverAReseededSingleton() {
        xl = new XlFontManager();
        xl.resetXlFontManager();
    }

    @Test
    void reportsTheSingletonsSeededInstalledCount() {
        assertEquals(SEEDED_FONT_COUNT, xl.getSizeInstalledFontsName());
        assertEquals(FontManager.getSciFontManager().getSizeInstalledFontsName(),
                     xl.getSizeInstalledFontsName());
    }

    @Test
    void availableFontNameCountMatchesTheArrayLengthAndTheSingleton() {
        int count = xl.getSizeAvailableFontsName();
        assertEquals(count, xl.getAvailableFontsName().length);
        assertEquals(FontManager.getSciFontManager().getSizeAvailableFontsName(), count);
    }

    @Test
    void isAvailableFontNameAgreesWithTheEnumeratedFamilies() {
        String[] families = xl.getAvailableFontsName();
        if (families.length > 0) {
            assertTrue(xl.isAvailableFontName(families[0]),
                       "an enumerated family must report as available: " + families[0]);
        }
        assertFalse(xl.isAvailableFontName("___definitely_not_a_font___"));
    }

    @Test
    void addFontGrowsTheSharedInstalledListAndReturnsTheNewIndex() {
        int before = xl.getSizeInstalledFontsName();
        int index = xl.addFont("Serif");
        assertEquals(before, index, "addFont returns the appended font's index");
        assertEquals(before + 1, xl.getSizeInstalledFontsName());
        // The facade and the singleton observe the very same list.
        assertEquals(FontManager.getSciFontManager().getSizeInstalledFontsName(),
                     xl.getSizeInstalledFontsName());
    }

    @Test
    void resetRestoresTheSeededCountAfterMutation() {
        xl.addFont("Serif");
        xl.addFont("SansSerif");
        assertEquals(SEEDED_FONT_COUNT + 2, xl.getSizeInstalledFontsName());
        xl.resetXlFontManager();
        assertEquals(SEEDED_FONT_COUNT, xl.getSizeInstalledFontsName());
    }

    @Test
    void changeFontReplacesAnExistingSlotInPlace() {
        int before = xl.getSizeInstalledFontsName();
        int index = xl.changeFont(0, "SansSerif");
        assertEquals(0, index);
        assertEquals(before, xl.getSizeInstalledFontsName(), "in-place change must not grow the list");
    }
}
