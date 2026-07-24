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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;

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
}
