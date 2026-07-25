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

package org.scilab.modules.console.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;

import javax.swing.Icon;
import javax.swing.JLabel;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.scilab.modules.console.utils.ScilabSpecialTextUtilities.SpecialIcon;

/**
 * Hermetic unit tests for {@link ScilabSpecialTextUtilities}.
 *
 * <p>The LaTeX/MathML compilation paths load extra jars via the native
 * {@code LoadClassPath} and are out of scope. What is hermetically testable is
 * (1) the {@link SpecialIcon} wrapper — a thin {@link Icon} decorator that
 * records a "depth" and delegates painting/sizing — and (2) the non-markup
 * branches of {@link ScilabSpecialTextUtilities#setText}, which never reach a
 * compiler: {@code null}/short/plain strings yield no icon, and an existing
 * {@code SpecialIcon} on the target is cleared. All Swing objects are only
 * constructed, never shown, which is headless-safe.
 */
public class ScilabSpecialTextUtilitiesTest {

    /** A deterministic Icon that records the last paint call. */
    private static final class StubIcon implements Icon {
        final int w;
        final int h;
        boolean painted;
        int lastX = Integer.MIN_VALUE;
        int lastY = Integer.MIN_VALUE;
        Graphics lastGraphics;

        StubIcon(int w, int h) {
            this.w = w;
            this.h = h;
        }

        public int getIconWidth() {
            return w;
        }

        public int getIconHeight() {
            return h;
        }

        public void paintIcon(Component c, Graphics g, int x, int y) {
            painted = true;
            lastGraphics = g;
            lastX = x;
            lastY = y;
        }
    }

    // --- SpecialIcon --------------------------------------------------------

    @Test
    public void specialIconDelegatesWidthAndHeightToTheWrappedIcon() {
        SpecialIcon icon = new SpecialIcon(new StubIcon(7, 11));
        assertEquals(7, icon.getIconWidth());
        assertEquals(11, icon.getIconHeight());
    }

    @Test
    public void theSingleArgumentConstructorLeavesDepthAtZero() {
        SpecialIcon icon = new SpecialIcon(new StubIcon(1, 1));
        assertEquals(0, icon.getIconDepth());
    }

    @Test
    public void theTwoArgumentConstructorStoresTheDepth() {
        SpecialIcon icon = new SpecialIcon(new StubIcon(1, 1), 5);
        assertEquals(5, icon.getIconDepth());
    }

    @Test
    public void paintIconDelegatesWithTheSameCoordinatesAndGraphics() {
        StubIcon stub = new StubIcon(3, 3);
        SpecialIcon icon = new SpecialIcon(stub);
        Graphics g = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB).createGraphics();
        try {
            icon.paintIcon(null, g, 3, 4);
        } finally {
            g.dispose();
        }
        assertTrue(stub.painted);
        assertEquals(3, stub.lastX);
        assertEquals(4, stub.lastY);
        assertSame(g, stub.lastGraphics);
    }

    // --- setText: non-markup (compiler-free) branches -----------------------

    @Test
    public void setTextReturnsFalseForNullText() {
        assertFalse(ScilabSpecialTextUtilities.setText(new JLabel(), null));
    }

    @Test
    public void setTextReturnsFalseForEmptyText() {
        assertFalse(ScilabSpecialTextUtilities.setText(new JLabel(), ""));
    }

    @Test
    public void setTextReturnsFalseForASingleCharacter() {
        // The length must be > 1 for any markup detection to even be attempted.
        assertFalse(ScilabSpecialTextUtilities.setText(new JLabel(), "$"));
    }

    @Test
    public void setTextReturnsFalseAndSetsNoIconForPlainText() {
        JLabel label = new JLabel();
        assertFalse(ScilabSpecialTextUtilities.setText(label, "hello"));
        assertNull(label.getIcon());
    }

    @Test
    public void setTextOnPlainTextClearsAPreviouslySetSpecialIcon() {
        // A SpecialIcon left over from an earlier LaTeX/MathML render must be
        // erased when the text is no longer markup.
        JLabel label = new JLabel();
        SpecialIcon previous = new SpecialIcon(new StubIcon(2, 2));
        label.setIcon(previous);
        assertSame(previous, label.getIcon());

        boolean rendered = ScilabSpecialTextUtilities.setText(label, "plain text");
        assertFalse(rendered);
        assertNull(label.getIcon());
    }

    @Test
    public void setTextOnPlainTextLeavesAnUnrelatedNonSpecialIconUntouched() {
        // Characterization: only SpecialIcons are cleared; an ordinary icon set by
        // some other code path is deliberately left in place.
        JLabel label = new JLabel();
        Icon ordinary = new StubIcon(2, 2);
        label.setIcon(ordinary);

        boolean rendered = ScilabSpecialTextUtilities.setText(label, "plain text");
        assertFalse(rendered);
        assertSame(ordinary, label.getIcon());
    }

    // --- real compiler paths (jlatexmath + jeuclid, headless) ---------------
    //
    // compileLaTeXExpression / compileMathMLExpression first call the native
    // LoadClassPath.loadOnUse to add the rendering jar to the classpath. Here the
    // jars (jlatexmath, jeuclid-core) are ALREADY on the test classpath, so the
    // only thing to neutralise is that native call: the "loaded" latches are
    // flipped to true by reflection so the public methods go straight to the
    // inner compilers, which are pure Java over headless Graphics2D/font metrics.

    @BeforeEach
    public void resetLoadLatches() throws Exception {
        setLatches(false, false, null);
    }

    @AfterEach
    public void clearLoadLatches() throws Exception {
        setLatches(false, false, null);
    }

    @Test
    public void compileLaTeXExpressionWrapsARealTeXIconForAValidFormula() throws Exception {
        setBoolean("loadedLaTeX", true);
        Icon icon = ScilabSpecialTextUtilities.compileLaTeXExpression("x^2 + 1", 15);
        assertNotNull(icon);
        assertTrue(icon instanceof SpecialIcon, "a compiled LaTeX icon is always a SpecialIcon");
        assertTrue(icon.getIconWidth() > 0, "a rendered formula must have a positive width");
        assertTrue(icon.getIconHeight() > 0, "a rendered formula must have a positive height");
    }

    @Test
    public void aLargerFontSizeYieldsATallerLaTeXIcon() throws Exception {
        setBoolean("loadedLaTeX", true);
        Icon small = ScilabSpecialTextUtilities.compileLaTeXExpression("x", 10);
        Icon big = ScilabSpecialTextUtilities.compileLaTeXExpression("x", 40);
        assertTrue(big.getIconHeight() > small.getIconHeight(),
                   "a 4x font size must produce a visibly taller icon");
    }

    @Test
    public void anInvalidLaTeXFormulaStillReturnsASpecialIconWrappingNoInnerIcon() throws Exception {
        // Characterization: the ParseException is swallowed inside LaTeXCompiler.compile,
        // leaving the inner icon null, yet a (null-wrapping) SpecialIcon is still returned.
        setBoolean("loadedLaTeX", true);
        Icon icon = ScilabSpecialTextUtilities.compileLaTeXExpression("\\nosuchcommand@@@", 15);
        assertNotNull(icon);
        assertTrue(icon instanceof SpecialIcon);
        assertEquals(0, ((SpecialIcon) icon).getIconDepth());
    }

    @Test
    public void compilePartialLaTeXExpressionReturnsARawIconWhenAlreadyLoaded() throws Exception {
        setBoolean("loadedLaTeX", true);
        Icon icon = ScilabSpecialTextUtilities.compilePartialLaTeXExpression("x", 15);
        assertNotNull(icon);
        // compilePartial returns the raw jlatexmath icon, NOT wrapped in a SpecialIcon.
        assertFalse(icon instanceof SpecialIcon);
        assertTrue(icon.getIconWidth() > 0);
    }

    @Test
    public void compilePartialLaTeXExpressionShortCircuitsToNullWhileALoadIsAlreadyInFlight() throws Exception {
        // loadedLaTeX is false but a loader thread already exists, so the method must
        // NOT spawn a second one and must return null. Using a pre-seeded (unstarted)
        // dummy thread keeps this deterministic — no background loader is launched.
        Thread dummy = new Thread();
        setLatches(false, false, dummy);
        Icon icon = ScilabSpecialTextUtilities.compilePartialLaTeXExpression("x", 15);
        assertNull(icon);
        assertSame(dummy, loadJLM(), "no new loader thread must be spawned");
    }

    @Test
    public void compileMathMLExpressionRendersAValidExpressionToASpecialIcon() throws Exception {
        setBoolean("loadedMathML", true);
        Icon icon = ScilabSpecialTextUtilities.compileMathMLExpression("<mi>x</mi>", 12);
        assertNotNull(icon);
        assertTrue(icon instanceof SpecialIcon);
        assertTrue(icon.getIconWidth() > 0);
        assertTrue(icon.getIconHeight() > 0);
    }

    @Test
    public void compileMathMLExpressionAcceptsAnAlreadyWrappedMathmlRoot() throws Exception {
        // Exercises the branch where the string already starts with <mathml> and so
        // is NOT wrapped a second time.
        setBoolean("loadedMathML", true);
        Icon icon = ScilabSpecialTextUtilities.compileMathMLExpression("<mathml><mi>y</mi></mathml>", 12);
        assertNotNull(icon);
        assertTrue(icon instanceof SpecialIcon);
    }

    @Test
    public void compileMathMLExpressionReturnsNullForMalformedXml() throws Exception {
        // A mismatched tag makes the SAX parse fail; the exception is caught and null returned.
        setBoolean("loadedMathML", true);
        assertNull(ScilabSpecialTextUtilities.compileMathMLExpression("<mi>x", 12));
    }

    @Test
    public void compileMathMLExpressionHonoursAnExplicitColorArgument() throws Exception {
        setBoolean("loadedMathML", true);
        Icon icon = ScilabSpecialTextUtilities.compileMathMLExpression("<mi>z</mi>", 14, Color.RED);
        assertNotNull(icon);
        assertTrue(icon instanceof SpecialIcon);
    }

    @Test
    public void setTextRendersALaTeXMarkupStringAndSetsASpecialIconOnTheComponent() throws Exception {
        setBoolean("loadedLaTeX", true);
        JLabel label = new JLabel();
        label.setFont(new Font("Dialog", Font.PLAIN, 12)); // deterministic, no L&F dependency
        boolean rendered = ScilabSpecialTextUtilities.setText(label, "$x^2$");
        assertTrue(rendered, "a $...$ string must be recognised and rendered as LaTeX");
        assertTrue(label.getIcon() instanceof SpecialIcon);
    }

    @Test
    public void setTextRendersAMathMLMarkupStringAndSetsASpecialIconOnTheComponent() throws Exception {
        setBoolean("loadedMathML", true);
        JLabel label = new JLabel();
        label.setFont(new Font("Dialog", Font.PLAIN, 12));
        boolean rendered = ScilabSpecialTextUtilities.setText(label, "<mi>x</mi>");
        assertTrue(rendered, "a <...> string must be recognised and rendered as MathML");
        assertTrue(label.getIcon() instanceof SpecialIcon);
    }

    // --- reflection helpers for the private static load latches -------------

    private static void setLatches(boolean latex, boolean mathml, Thread loadJLM) throws Exception {
        setBoolean("loadedLaTeX", latex);
        setBoolean("loadedMathML", mathml);
        Field f = ScilabSpecialTextUtilities.class.getDeclaredField("loadJLM");
        f.setAccessible(true);
        f.set(null, loadJLM);
    }

    private static void setBoolean(String name, boolean value) throws Exception {
        Field f = ScilabSpecialTextUtilities.class.getDeclaredField(name);
        f.setAccessible(true);
        f.setBoolean(null, value);
    }

    private static Thread loadJLM() throws Exception {
        Field f = ScilabSpecialTextUtilities.class.getDeclaredField("loadJLM");
        f.setAccessible(true);
        return (Thread) f.get(null);
    }
}
