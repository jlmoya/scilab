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

package org.scilab.forge.scirenderer.shapes.appearance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hermetic unit tests for {@link Color}, the scirenderer color that extends
 * {@link java.awt.Color} with float-valued component accessors.
 */
public class ColorTest {

    private static final float EPS = 1e-4f;

    @Test
    public void extendsAwtColor() {
        assertTrue(new Color(0f, 0f, 0f) instanceof java.awt.Color);
    }

    @Test
    public void whiteComponentsAreMaxedOut() {
        Color white = new Color(1f, 1f, 1f);
        assertEquals(255, white.getRed());
        assertEquals(255, white.getGreen());
        assertEquals(255, white.getBlue());
        assertEquals(255, white.getAlpha(), "opaque by default");
        assertEquals(1.0f, white.getRedAsFloat(), 0f);
        assertEquals(1.0f, white.getGreenAsFloat(), 0f);
        assertEquals(1.0f, white.getBlueAsFloat(), 0f);
        assertEquals(1.0f, white.getAlphaAsFloat(), 0f);
    }

    @Test
    public void blackComponentsAreZero() {
        Color black = new Color(0f, 0f, 0f);
        assertEquals(0, black.getRed());
        assertEquals(0.0f, black.getRedAsFloat(), 0f);
    }

    @Test
    public void alphaComponentIsHonored() {
        Color halfAlpha = new Color(1f, 0f, 0f, 0.5f);
        // 0.5f rounds to the byte 128; the float accessor is therefore 128/255, not exactly 0.5.
        assertEquals(128, halfAlpha.getAlpha());
        assertEquals(128 / 255f, halfAlpha.getAlphaAsFloat(), 0f);
    }

    @Test
    public void copyConstructorPreservesComponents() {
        Color source = new Color(0.2f, 0.4f, 0.6f, 0.8f);
        Color copy = new Color(source);
        assertEquals(source.getRed(), copy.getRed());
        assertEquals(source.getGreen(), copy.getGreen());
        assertEquals(source.getBlue(), copy.getBlue());
        assertEquals(source.getAlpha(), copy.getAlpha());
        assertEquals(source, copy);
    }

    @Test
    public void defaultConstructorMatchesTheDefaultColor() {
        // The no-arg constructor copies the (0.2, 0.3, 0.4) default color.
        assertEquals(new Color(0.2f, 0.3f, 0.4f), new Color());
    }

    @Test
    public void floatAccessorsAreTheByteComponentsOver255() {
        Color c = new Color(0.5f, 0.25f, 0.75f);
        assertEquals(c.getRed() / 255f, c.getRedAsFloat(), 0f);
        assertEquals(c.getGreen() / 255f, c.getGreenAsFloat(), 0f);
        assertEquals(c.getBlue() / 255f, c.getBlueAsFloat(), 0f);
    }
}
