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

package org.scilab.modules.gui.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import javax.swing.Icon;

/**
 * Hermetic unit tests for {@link ColorBox}, the small {@link Icon} that paints a
 * filled colour square with a one-pixel black border.
 *
 * <p>The class has no accessor for its colour, so the fill/border behaviour is
 * verified by painting onto an off-screen {@link BufferedImage} and reading the
 * resulting pixels. {@code paintIcon} never touches its {@code Component}
 * argument, so {@code null} is passed for it. Everything here is pure AWT and
 * runs on a headless JVM without the Scilab native runtime.</p>
 */
public class ColorBoxTest {

    // ------------------------------------------------------------------
    // Construction, factory and the two Icon getters
    // ------------------------------------------------------------------

    @Test
    public void constructorStoresWidthAndHeight() {
        ColorBox box = new ColorBox(20, 10, Color.RED);
        assertEquals(20, box.getIconWidth());
        assertEquals(10, box.getIconHeight());
    }

    @Test
    public void factoryCreatesAnEquivalentInstance() {
        ColorBox box = ColorBox.createColorBox(7, 9, Color.BLUE);
        assertNotNull(box);
        assertEquals(7, box.getIconWidth());
        assertEquals(9, box.getIconHeight());
    }

    @Test
    public void factoryReturnsDistinctInstances() {
        ColorBox a = ColorBox.createColorBox(5, 5, Color.GREEN);
        ColorBox b = ColorBox.createColorBox(5, 5, Color.GREEN);
        assertNotSame(a, b);
    }

    @Test
    public void isAnIcon() {
        assertInstanceOf(Icon.class, new ColorBox(1, 1, Color.BLACK));
    }

    @Test
    public void zeroDimensionsAreReportedAsIs() {
        ColorBox box = new ColorBox(0, 0, Color.RED);
        assertEquals(0, box.getIconWidth());
        assertEquals(0, box.getIconHeight());
    }

    @Test
    public void negativeDimensionsAreNotValidated() {
        // Defect-characterisation: the constructor performs no argument validation.
        ColorBox box = new ColorBox(-4, -3, Color.RED);
        assertEquals(-4, box.getIconWidth());
        assertEquals(-3, box.getIconHeight());
    }

    @Test
    public void nullColorIsAcceptedByTheConstructor() {
        // Defect-characterisation: no null-check; the icon is still constructible
        // and its reported size is independent of the colour.
        ColorBox box = new ColorBox(12, 8, null);
        assertEquals(12, box.getIconWidth());
        assertEquals(8, box.getIconHeight());
    }

    // ------------------------------------------------------------------
    // paintIcon - fill colour, border colour and (x, y) placement
    // ------------------------------------------------------------------

    @Test
    public void paintIconFillsTheInteriorWithTheGivenColour() {
        BufferedImage img = whiteCanvas(50, 50);
        Graphics2D g = img.createGraphics();
        try {
            new ColorBox(20, 10, Color.RED).paintIcon(null, g, 5, 5);
        } finally {
            g.dispose();
        }

        // (15, 10) is well inside the 20x10 fill (x in [5,24], y in [5,14]) and
        // clear of every border line, so it must be the fill colour.
        assertEquals(Color.RED.getRGB(), img.getRGB(15, 10));
        // A pixel far outside the icon keeps the white background untouched.
        assertEquals(Color.WHITE.getRGB(), img.getRGB(45, 45));
    }

    @Test
    public void paintIconDrawsABlackBorderOnePixelBeyondTheFill() {
        BufferedImage img = whiteCanvas(50, 50);
        Graphics2D g = img.createGraphics();
        try {
            new ColorBox(20, 10, Color.RED).paintIcon(null, g, 5, 5);
        } finally {
            g.dispose();
        }

        // drawRect(5,5,20,10) outlines the rectangle whose corners are (5,5) and
        // (25,15). The bottom-right corner sits ONE pixel beyond the fill (which
        // stops at x=24, y=14) yet is painted black -> distinguishes border from
        // both fill and background.
        assertEquals(Color.BLACK.getRGB(), img.getRGB(25, 15));
        // The top-left corner is drawn black on top of the fill.
        assertEquals(Color.BLACK.getRGB(), img.getRGB(5, 5));
        // A point on the top edge is border-black even though it overlays the fill.
        assertEquals(Color.BLACK.getRGB(), img.getRGB(15, 5));
    }

    @Test
    public void paintIconHonoursTheXYOffset() {
        BufferedImage img = whiteCanvas(40, 40);
        Graphics2D g = img.createGraphics();
        try {
            new ColorBox(6, 6, Color.RED).paintIcon(null, g, 10, 20);
        } finally {
            g.dispose();
        }

        // Interior of the shifted box is red...
        assertEquals(Color.RED.getRGB(), img.getRGB(12, 22));
        // ...while the origin the box was NOT drawn at stays white.
        assertEquals(Color.WHITE.getRGB(), img.getRGB(1, 1));
    }

    private static BufferedImage whiteCanvas(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.dispose();
        return img;
    }
}
