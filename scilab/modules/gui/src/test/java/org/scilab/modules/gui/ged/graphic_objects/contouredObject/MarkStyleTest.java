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

package org.scilab.modules.gui.ged.graphic_objects.contouredObject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Hermetic unit tests for the {@link MarkStyle} enum, the GED's palette of the
 * fifteen mark glyphs drawn when a graphic object's {@code mark_mode} is "on".
 *
 * <p>Every rendering happens onto an off-screen {@link BufferedImage}, so the
 * tests run headless and without the native runtime. A notable design trait is
 * that {@code MarkStyle} keeps its {@code Graphics2D} and its foreground/
 * background colours in <em>static</em> fields shared by all constants; the
 * suite both exercises that behaviour and pins it. {@link #resetSharedState()}
 * re-initialises the shared fields before each test so the tests are
 * order-independent.</p>
 */
class MarkStyleTest {

    /** The constants in their declared order == the Scilab mark_style indices. */
    private static final MarkStyle[] IN_INDEX_ORDER = {
        MarkStyle.CIRCLE_SOLID, MarkStyle.PLUS, MarkStyle.CROSS, MarkStyle.CIRCLE_PLUS,
        MarkStyle.DIAMOND_SOLID, MarkStyle.DIAMOND, MarkStyle.UP, MarkStyle.DOWN,
        MarkStyle.DIAMOND_PLUS, MarkStyle.CIRCLE, MarkStyle.ASTERISK, MarkStyle.SQUARE,
        MarkStyle.FORWARD, MarkStyle.BACKWARD, MarkStyle.STAR
    };

    @BeforeEach
    void resetSharedState() {
        // The Graphics2D and colours live in static fields shared by every
        // constant, so reset them to a known, valid state before each test.
        BufferedImage img = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
        MarkStyle.CIRCLE_SOLID.setGraphics2D(img.createGraphics());
        MarkStyle.CIRCLE_SOLID.setForeground(Color.BLACK);
        MarkStyle.CIRCLE_SOLID.setBackground(Color.WHITE);
    }

    // ---- The enum-index contract ----------------------------------------

    /**
     * The GED maps Scilab's numeric {@code mark_style} property onto this enum by
     * ordinal, so both the count and the order are a hard contract.
     */
    @Test
    void valuesAreExactlyTheFifteenStylesInIndexOrder() {
        assertEquals(15, MarkStyle.values().length);
        assertArrayEquals(IN_INDEX_ORDER, MarkStyle.values());
    }

    @Test
    void ordinalsMatchTheDocumentedMarkStyleIndices() {
        assertEquals(0, MarkStyle.CIRCLE_SOLID.ordinal());
        assertEquals(1, MarkStyle.PLUS.ordinal());
        assertEquals(2, MarkStyle.CROSS.ordinal());
        assertEquals(11, MarkStyle.SQUARE.ordinal());
        assertEquals(14, MarkStyle.STAR.ordinal());
    }

    @Test
    void valueOfRoundTripsEveryConstantName() {
        for (MarkStyle m : MarkStyle.values()) {
            assertSame(m, MarkStyle.valueOf(m.name()));
        }
    }

    @Test
    void valueOfRejectsAnUnknownName() {
        assertThrows(IllegalArgumentException.class, () -> MarkStyle.valueOf("NOT_A_MARK"));
    }

    // ---- getMarkStyle: returns the supplied context, never throws --------

    /**
     * Every constant's {@code getMarkStyle()} override must draw its glyph onto
     * the supplied {@link Graphics2D} and hand that same context back — verified
     * for all fifteen so each drawing branch is exercised on a real (off-screen)
     * graphics context.
     */
    @Test
    void everyConstantRendersAndReturnsTheGivenGraphics() {
        for (MarkStyle m : MarkStyle.values()) {
            BufferedImage img = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            try {
                m.setGraphics2D(g);
                assertSame(g, m.getMarkStyle(), m + " must return the Graphics2D it was given");
            } finally {
                g.dispose();
            }
        }
    }

    // ---- Shared static state (white-box characterization) ---------------

    /**
     * White-box: the {@code Graphics2D} lives in a static field, so a context set
     * through one constant is observed by a different one.
     */
    @Test
    void graphicsContextIsSharedStaticallyAcrossConstants() {
        BufferedImage img = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            MarkStyle.CIRCLE_SOLID.setGraphics2D(g);
            // A completely different constant sees the same shared context.
            assertSame(g, MarkStyle.STAR.getMarkStyle());
        } finally {
            g.dispose();
        }
    }

    /**
     * Characterization: there is no null-guard — {@code getMarkStyle()}
     * dereferences the shared static context immediately, so a null context is a
     * {@link NullPointerException}.
     */
    @Test
    void getMarkStyleWithNullGraphicsThrows() {
        MarkStyle.CIRCLE_SOLID.setGraphics2D(null);
        assertThrows(NullPointerException.class, () -> MarkStyle.CIRCLE_SOLID.getMarkStyle());
    }

    // ---- Colour usage (behavioural, via rendered pixels) ----------------

    /**
     * SQUARE fills its interior with the background colour and outlines it with
     * the foreground colour. Rendering with distinct colours and reading pixels
     * back proves both setters feed the drawing and which role each colour plays.
     */
    @Test
    void squareFillsWithBackgroundAndOutlinesWithForeground() {
        BufferedImage img = renderSquareWith(Color.RED, Color.BLUE);

        // fillRect(6,2,12,12) fills x in [6,17], y in [2,13]; (12,8) is interior
        // and clear of every outline edge -> background fill colour.
        assertEquals(Color.RED.getRGB(), img.getRGB(12, 8));
        // drawRect(6,2,12,12) right edge is at x=18, one pixel beyond the fill,
        // so (18,8) is pure outline -> foreground colour.
        assertEquals(Color.BLUE.getRGB(), img.getRGB(18, 8));
        // A pixel outside the mark keeps the white canvas.
        assertEquals(Color.WHITE.getRGB(), img.getRGB(0, 0));
    }

    /**
     * White-box: the colours are static too. Configuring them through one
     * constant (PLUS) governs how a different constant (SQUARE) paints.
     */
    @Test
    void foregroundAndBackgroundAreSharedStaticallyAcrossConstants() {
        BufferedImage img = new BufferedImage(24, 24, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, 24, 24);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

            // Colours configured through PLUS...
            MarkStyle.PLUS.setBackground(Color.RED);
            MarkStyle.PLUS.setForeground(Color.GREEN);
            // ...drive the rendering done through SQUARE.
            MarkStyle.SQUARE.setGraphics2D(g);
            MarkStyle.SQUARE.getMarkStyle();
        } finally {
            g.dispose();
        }
        assertEquals(Color.RED.getRGB(), img.getRGB(12, 8));
        assertEquals(Color.GREEN.getRGB(), img.getRGB(18, 8));
    }

    // ---- helper ---------------------------------------------------------

    private static BufferedImage renderSquareWith(Color background, Color foreground) {
        BufferedImage img = new BufferedImage(24, 24, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, 24, 24);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            MarkStyle.SQUARE.setGraphics2D(g);
            MarkStyle.SQUARE.setBackground(background);
            MarkStyle.SQUARE.setForeground(foreground);
            MarkStyle.SQUARE.getMarkStyle();
        } finally {
            g.dispose();
        }
        return img;
    }
}
