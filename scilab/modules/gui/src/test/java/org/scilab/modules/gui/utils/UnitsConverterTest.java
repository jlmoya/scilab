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

package org.scilab.modules.gui.utils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import org.junit.jupiter.api.Test;
import org.scilab.modules.gui.utils.UnitsConverter.UicontrolUnits;

/**
 * Hermetic unit tests for {@link UnitsConverter}.
 *
 * <p>Only the parts of the class that carry no dependency on the native Scilab
 * runtime are exercised here:</p>
 * <ul>
 *   <li>{@link UnitsConverter#stringToUnitsEnum(String)} — a pure keyword lookup;</li>
 *   <li>the {@link UnitsConverter.UicontrolUnits} enum and the protected unit
 *       keyword constants (the string contract shared with Scilab scripts);</li>
 *   <li>{@link UnitsConverter#convertFromPixel(int, UnitsConverter.UicontrolUnits, org.scilab.modules.gui.widget.Widget, boolean)}
 *       on its {@code uicontrol == null} short-circuit, which returns before any
 *       native call.</li>
 * </ul>
 *
 * <p>The four remaining conversion helpers ({@code convertFromPoint},
 * {@code convertToPoint}, {@code convertToPixel} and {@code convertPositionToPixels},
 * plus {@code convertFromPixel} with a non-null widget) unconditionally reach into
 * {@code GraphicController.getController()} and
 * {@code CallScilabBridge.getScreenResolution()}; they require the native runtime
 * and a live SwingView widget, so they are intentionally left uncovered by this
 * hermetic suite.</p>
 */
class UnitsConverterTest {

    // ---- stringToUnitsEnum: the recognised keywords ---------------------

    @Test
    void pointsKeywordMapsToPoints() {
        assertSame(UicontrolUnits.POINTS, UnitsConverter.stringToUnitsEnum("points"));
    }

    @Test
    void normalizedKeywordMapsToNormalized() {
        assertSame(UicontrolUnits.NORMALIZED, UnitsConverter.stringToUnitsEnum("normalized"));
    }

    @Test
    void inchesKeywordMapsToInches() {
        assertSame(UicontrolUnits.INCHES, UnitsConverter.stringToUnitsEnum("inches"));
    }

    @Test
    void centimetersKeywordMapsToCentimeters() {
        assertSame(UicontrolUnits.CENTIMETERS, UnitsConverter.stringToUnitsEnum("centimeters"));
    }

    @Test
    void pixelsKeywordMapsToPixels() {
        assertSame(UicontrolUnits.PIXELS, UnitsConverter.stringToUnitsEnum("pixels"));
    }

    // ---- stringToUnitsEnum: the unrecognised inputs ---------------------

    @Test
    void unknownKeywordReturnsNull() {
        assertNull(UnitsConverter.stringToUnitsEnum("furlongs"));
    }

    @Test
    void emptyKeywordReturnsNull() {
        assertNull(UnitsConverter.stringToUnitsEnum(""));
    }

    /**
     * The lookup is done with {@code String.compareTo}, so it is case sensitive:
     * the upper-cased forms are not recognised.
     */
    @Test
    void wrongCaseKeywordReturnsNull() {
        assertNull(UnitsConverter.stringToUnitsEnum("Points"));
        assertNull(UnitsConverter.stringToUnitsEnum("PIXELS"));
    }

    /**
     * Characterization: {@code style.compareTo(...)} dereferences the argument,
     * so a null keyword throws rather than returning null.
     */
    @Test
    void nullKeywordThrowsNpe() {
        assertThrows(NullPointerException.class, () -> UnitsConverter.stringToUnitsEnum(null));
    }

    // ---- The unit keyword constants -------------------------------------

    /**
     * The keyword strings are the contract with Scilab (the literal values the
     * {@code units} property takes), so pin them. The constants are
     * package-visible ({@code protected}) and reachable from this same-package
     * test.
     */
    @Test
    void unitKeywordConstantsHaveTheScilabContractValues() {
        assertEquals("points", UnitsConverter.__GO_UI_POINTS_UNITS__);
        assertEquals("normalized", UnitsConverter.__GO_UI_NORMALIZED_UNITS__);
        assertEquals("inches", UnitsConverter.__GO_UI_INCHES_UNITS__);
        assertEquals("centimeters", UnitsConverter.__GO_UI_CENTIMETERS_UNITS__);
        assertEquals("pixels", UnitsConverter.__GO_UI_PIXELS_UNITS__);
    }

    /**
     * Ties the constants to the parser: every declared keyword constant parses
     * back to the matching enum value.
     */
    @Test
    void everyKeywordConstantParsesToItsEnumValue() {
        assertSame(UicontrolUnits.POINTS, UnitsConverter.stringToUnitsEnum(UnitsConverter.__GO_UI_POINTS_UNITS__));
        assertSame(UicontrolUnits.NORMALIZED, UnitsConverter.stringToUnitsEnum(UnitsConverter.__GO_UI_NORMALIZED_UNITS__));
        assertSame(UicontrolUnits.INCHES, UnitsConverter.stringToUnitsEnum(UnitsConverter.__GO_UI_INCHES_UNITS__));
        assertSame(UicontrolUnits.CENTIMETERS, UnitsConverter.stringToUnitsEnum(UnitsConverter.__GO_UI_CENTIMETERS_UNITS__));
        assertSame(UicontrolUnits.PIXELS, UnitsConverter.stringToUnitsEnum(UnitsConverter.__GO_UI_PIXELS_UNITS__));
    }

    // ---- The UicontrolUnits enum ----------------------------------------

    @Test
    void enumDeclaresFiveUnitsInOrder() {
        assertArrayEquals(
            new UicontrolUnits[] {
                UicontrolUnits.POINTS, UicontrolUnits.NORMALIZED, UicontrolUnits.INCHES,
                UicontrolUnits.CENTIMETERS, UicontrolUnits.PIXELS
            },
            UicontrolUnits.values());
        assertEquals(0, UicontrolUnits.POINTS.ordinal());
        assertEquals(4, UicontrolUnits.PIXELS.ordinal());
    }

    @Test
    void enumValueOfRoundTripsAndRejectsUnknown() {
        for (UicontrolUnits u : UicontrolUnits.values()) {
            assertSame(u, UicontrolUnits.valueOf(u.name()));
        }
        assertThrows(IllegalArgumentException.class, () -> UicontrolUnits.valueOf("METERS"));
    }

    // ---- convertFromPixel: the null-uicontrol short-circuit -------------

    /**
     * With a null widget the method returns the pixel value unchanged (widened to
     * double) for every target unit, never touching the native runtime.
     */
    @Test
    void convertFromPixelWithNullUicontrolReturnsValueUnchangedForEveryUnit() {
        for (UicontrolUnits u : UicontrolUnits.values()) {
            assertEquals(123.0, UnitsConverter.convertFromPixel(123, u, null, true), 0.0,
                         "null uicontrol must short-circuit for unit " + u);
            assertEquals(123.0, UnitsConverter.convertFromPixel(123, u, null, false), 0.0,
                         "widthAsRef must not matter for the null-uicontrol path, unit " + u);
        }
    }

    @Test
    void convertFromPixelWithNullUicontrolHandlesZeroAndNegative() {
        assertEquals(0.0, UnitsConverter.convertFromPixel(0, UicontrolUnits.PIXELS, null, true), 0.0);
        assertEquals(-7.0, UnitsConverter.convertFromPixel(-7, UicontrolUnits.INCHES, null, false), 0.0);
    }

    // ---- Class shape ----------------------------------------------------

    @Test
    void classIsFinal() {
        assertTrue(Modifier.isFinal(UnitsConverter.class.getModifiers()));
    }

    /**
     * Characterization: although {@code UnitsConverter} exposes only static
     * helpers, it never hides its constructor, so it ships the implicit
     * <em>public</em> no-arg constructor and can be instantiated (unlike the sibling
     * utility classes such as {@code PositionConverter}, whose constructors throw).
     */
    @Test
    void constructorIsPublicUnlikeATypicalUtilityClass() throws Exception {
        Constructor<UnitsConverter> ctor = UnitsConverter.class.getDeclaredConstructor();
        assertTrue(Modifier.isPublic(ctor.getModifiers()));
        assertNotNull(ctor.newInstance());
    }
}
