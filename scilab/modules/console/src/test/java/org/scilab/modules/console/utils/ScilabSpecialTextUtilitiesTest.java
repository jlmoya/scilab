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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.image.BufferedImage;

import javax.swing.Icon;
import javax.swing.JLabel;

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
}
