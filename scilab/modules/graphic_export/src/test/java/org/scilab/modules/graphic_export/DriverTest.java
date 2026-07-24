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

package org.scilab.modules.graphic_export;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Hermetic unit tests for {@link Driver}'s pure static string logic:
 * {@code getDriver}, {@code setDriver}, {@code setPath} and
 * {@code isImageRendering}.
 *
 * SCOPE / hermeticity note: {@code setDriver} only reaches
 * {@code Export.getType(...)} for a token that is NOT one of the built-in
 * pseudo-drivers "x11" / "rec" / "null". Java's short-circuit {@code &&}
 * evaluation means every case tested here stops before that call, so the
 * heavyweight {@code Export} class (batik/fop/scirenderer/JoGL) is never
 * loaded. The image-format branch of {@code setDriver} and the
 * {@code setDefaultVisitor}/{@code end} methods need a live figure + renderer
 * and are intentionally out of scope.
 *
 * {@code Driver} keeps its selected driver in a mutable static field, so
 * {@link #reset()} re-establishes a known baseline before each test to make
 * the suite order-independent.
 */
public class DriverTest {

    @BeforeEach
    public void reset() {
        // "rec" short-circuits before Export.getType -> stays hermetic.
        Driver.setDriver("Rec");
    }

    @Test
    public void baselineDriverIsRec() {
        assertEquals("Rec", Driver.getDriver());
    }

    @Test
    public void setDriverAcceptsX11AndStoresIt() {
        assertTrue(Driver.setDriver("x11"));
        assertEquals("x11", Driver.getDriver());
    }

    @Test
    public void setDriverAcceptsRec() {
        assertTrue(Driver.setDriver("rec"));
        assertEquals("rec", Driver.getDriver());
    }

    @Test
    public void setDriverAcceptsNull() {
        assertTrue(Driver.setDriver("null"));
        assertEquals("null", Driver.getDriver());
    }

    @Test
    public void setDriverGuardIsCaseInsensitiveButPreservesOriginalCasing() {
        // "X11" lower-cases to a recognised pseudo-driver so it is accepted,
        // yet the ORIGINAL spelling is what gets stored (documents real behaviour).
        assertTrue(Driver.setDriver("X11"));
        assertEquals("X11", Driver.getDriver());

        assertTrue(Driver.setDriver("REC"));
        assertEquals("REC", Driver.getDriver());
    }

    @Test
    public void isImageRenderingIsFalseForRecBaseline() {
        assertFalse(Driver.isImageRendering());
    }

    @Test
    public void isImageRenderingIsFalseForX11CaseInsensitively() {
        Driver.setDriver("X11");
        assertFalse(Driver.isImageRendering());
        Driver.setDriver("x11");
        assertFalse(Driver.isImageRendering());
    }

    @Test
    public void isImageRenderingIsFalseForNull() {
        Driver.setDriver("null");
        assertFalse(Driver.isImageRendering());
    }

    @Test
    public void setPathDoesNotAffectTheSelectedDriver() {
        Driver.setDriver("x11");
        Driver.setPath("/tmp/whatever.png");
        assertEquals("x11", Driver.getDriver());
        // setPath must not flip the driver's image-rendering classification.
        assertFalse(Driver.isImageRendering());
    }
}
