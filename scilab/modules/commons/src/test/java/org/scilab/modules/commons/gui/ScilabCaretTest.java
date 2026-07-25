/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Scilab
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 *
 */

package org.scilab.modules.commons.gui;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Rectangle;

import javax.swing.JTextField;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link ScilabCaret}.
 *
 * <p>A {@link ScilabCaret} is only <em>constructed</em> around a text component
 * (never installed/painted), which is valid headless. These tests focus on the
 * pure colour-blending arithmetic, the {@code mustAdjustVisibility} flag, and the
 * (defect-worthy) fact that the selection colours are stored in static fields.
 */
public class ScilabCaretTest {

    /** A caret needs a text component whose selection colour is non-null. */
    private static ScilabCaret newCaret() {
        JTextField editor = new JTextField();
        editor.setSelectionColor(Color.CYAN);
        return new ScilabCaret(editor);
    }

    @Test
    public void constructorProducesACaretWithVisibilityAdjustmentOn() {
        ScilabCaret caret = newCaret();
        assertNotNull(caret);
        assertTrue(caret.getMustAdjustVisibility(), "mustAdjustVisibility defaults to true");
    }

    @Test
    public void mustAdjustVisibilityRoundTrips() {
        ScilabCaret caret = newCaret();
        caret.setMustAdjustVisibility(false);
        assertFalse(caret.getMustAdjustVisibility());
        caret.setMustAdjustVisibility(true);
        assertTrue(caret.getMustAdjustVisibility());
    }

    @Test
    public void explicitInactiveColourIsStoredVerbatim() {
        ScilabCaret caret = newCaret();
        caret.setSelectionColor(Color.RED, Color.BLUE);
        assertEquals(Color.RED, caret.getSelectionColor());
        assertEquals(Color.BLUE, caret.getInactiveSelectionColor());
    }

    @Test
    public void nullInactiveColourIsBlendedFromBlackActive() {
        ScilabCaret caret = newCaret();
        // 0.6*0 + 0.4*192 = 76.8 -> round -> 77 for each channel.
        caret.setSelectionColor(Color.BLACK, null);
        Color inactive = caret.getInactiveSelectionColor();
        assertNotNull(inactive);
        assertEquals(77, inactive.getRed());
        assertEquals(77, inactive.getGreen());
        assertEquals(77, inactive.getBlue());
    }

    @Test
    public void nullInactiveColourIsBlendedFromWhiteActive() {
        ScilabCaret caret = newCaret();
        // 0.6*255 + 0.4*192 = 153 + 76.8 = 229.8 -> round -> 230 for each channel.
        caret.setSelectionColor(Color.WHITE, null);
        Color inactive = caret.getInactiveSelectionColor();
        assertEquals(230, inactive.getRed());
        assertEquals(230, inactive.getGreen());
        assertEquals(230, inactive.getBlue());
    }

    @Test
    public void selectionColoursAreSharedStaticStateAcrossInstances() {
        // Characterizes a design quirk: the (in)active selection colours live in
        // static fields, so setting them through one caret is visible through another.
        ScilabCaret a = newCaret();
        ScilabCaret b = newCaret();

        a.setSelectionColor(Color.RED, Color.BLUE);

        assertEquals(Color.RED, b.getSelectionColor());
        assertEquals(Color.BLUE, b.getInactiveSelectionColor());
    }

    // ----------------------------------------------------------------- damage(Rectangle)

    // The caret is constructed but never install()ed, so DefaultCaret.getComponent() is null and
    // the repaint() fired at the end of damage()/adjustVisibility() is a guarded no-op - which is
    // exactly what keeps these geometry paths headless-safe. ScilabCaret extends java.awt.Rectangle
    // (via DefaultCaret), so x/y/width/height are the public inherited fields asserted below.

    @Test
    public void damageInOverwriteModeCopiesXYHeightFromTheRectangle() {
        ScilabCaret caret = newCaret();
        caret.setOverwriteMode(true);

        caret.damage(new Rectangle(11, 22, 33, 44));

        assertEquals(11, caret.x, "x is taken from r.x");
        assertEquals(22, caret.y, "y is taken from r.y");
        assertEquals(44, caret.height, "height is taken from r.height");
        // The overwrite branch deliberately does NOT touch width (the solid block is sized in
        // paint() from the font metrics), so it stays at the Rectangle default.
        assertEquals(0, caret.width, "the overwrite damage() must leave width untouched");
    }

    @Test
    public void damageInOverwriteModeIgnoresANullRectangle() {
        ScilabCaret caret = newCaret();
        caret.setOverwriteMode(true);
        caret.damage(new Rectangle(5, 6, 7, 8));   // establish a known geometry

        caret.damage(null);                        // null-guard: must be a no-op, never an NPE

        assertEquals(5, caret.x);
        assertEquals(6, caret.y);
        assertEquals(8, caret.height);
    }

    @Test
    public void damageOutsideOverwriteModeDelegatesToDefaultCaret() {
        ScilabCaret caret = newCaret();
        // overwriteMode defaults to false -> super.damage(r) runs. It positions x with a
        // look-and-feel-dependent offset (not asserted), but always copies y and height verbatim.
        assertDoesNotThrow(() -> caret.damage(new Rectangle(100, 200, 10, 20)));
        assertEquals(200, caret.y, "DefaultCaret.damage copies r.y verbatim");
        assertEquals(20, caret.height, "DefaultCaret.damage copies r.height verbatim");
    }

    // ----------------------------------------------------------------- adjustVisibility(Rectangle)

    @Test
    public void adjustVisibilityIsASilentNoOpWhenSuppressed() {
        ScilabCaret caret = newCaret();
        caret.setMustAdjustVisibility(false);
        // With adjustment suppressed the override returns before calling super (which would need a
        // realized, installed component), so this is safe and inert headless.
        assertDoesNotThrow(() -> caret.adjustVisibility(new Rectangle(0, 0, 1, 1)));
    }
}
