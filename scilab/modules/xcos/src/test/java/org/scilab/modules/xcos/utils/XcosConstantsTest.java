/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Scilab test coverage
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 *
 */

package org.scilab.modules.xcos.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Dimension;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import org.junit.jupiter.api.Test;
import org.scilab.modules.graph.utils.ScilabGraphConstants;
import org.scilab.modules.xcos.utils.XcosConstants.PaletteBlockSize;

/**
 * Hermetic unit tests for {@link XcosConstants} and its nested
 * {@link XcosConstants.PaletteBlockSize} enum. Both are pure Java (constants and
 * {@link java.awt.Dimension} arithmetic): no Scilab native runtime is touched.
 *
 * The expected icon width/height values were reproduced against the compiled
 * class; they exercise the {@code (int)(dimension * 0.65f)} truncation, whose
 * result is sensitive to {@code float} rounding and therefore worth pinning.
 */
public class XcosConstantsTest {

    /* ---- top-level constants ---- */

    @Test
    public void paletteConstantsHaveExpectedValues() {
        assertEquals(1.5, XcosConstants.PALETTE_BLOCK_ICON_RATIO, 0.0);
        assertEquals(5, XcosConstants.PALETTE_HMARGIN);
        assertEquals(5, XcosConstants.PALETTE_VMARGIN);
    }

    @Test
    public void miscellaneousConstantsHaveExpectedValues() {
        assertEquals(37, XcosConstants.MAX_CHAR_IN_STYLE);
        assertEquals(20, XcosConstants.HISTORY_LENGTH);
        assertEquals(150, XcosConstants.MAX_HITS);
    }

    @Test
    public void xcosEtcPathIsRelativeToInstallationRoot() {
        assertEquals("/modules/xcos/etc", XcosConstants.XCOS_ETC);
    }

    /* ---- class structure ---- */

    @Test
    public void classIsFinal() {
        assertTrue(Modifier.isFinal(XcosConstants.class.getModifiers()));
    }

    @Test
    public void classExtendsScilabGraphConstants() {
        assertTrue(ScilabGraphConstants.class.isAssignableFrom(XcosConstants.class));
    }

    @Test
    public void soleConstructorIsPrivate() throws NoSuchMethodException {
        // Static-singleton: the class documents that it must not be instantiated.
        Constructor<XcosConstants> ctor = XcosConstants.class.getDeclaredConstructor();
        assertTrue(Modifier.isPrivate(ctor.getModifiers()));
    }

    /* ---- PaletteBlockSize: enumeration shape ---- */

    @Test
    public void paletteBlockSizeDeclaresFiveOrderedSizes() {
        PaletteBlockSize[] values = PaletteBlockSize.values();
        assertEquals(5, values.length);
        assertSame(PaletteBlockSize.TINY, values[0]);
        assertSame(PaletteBlockSize.SMALL, values[1]);
        assertSame(PaletteBlockSize.NORMAL, values[2]);
        assertSame(PaletteBlockSize.LARGE, values[3]);
        assertSame(PaletteBlockSize.XLARGE, values[4]);
    }

    @Test
    public void valueOfRoundTripsEachName() {
        for (PaletteBlockSize s : PaletteBlockSize.values()) {
            assertSame(s, PaletteBlockSize.valueOf(s.name()));
        }
    }

    /* ---- PaletteBlockSize: font size ---- */

    @Test
    public void fontSizeMatchesPerSize() {
        assertEquals(10, PaletteBlockSize.TINY.getFontSize());
        assertEquals(11, PaletteBlockSize.SMALL.getFontSize());
        assertEquals(12, PaletteBlockSize.NORMAL.getFontSize());
        assertEquals(13, PaletteBlockSize.LARGE.getFontSize());
        // XLARGE deliberately reuses LARGE's font size in the source switch.
        assertEquals(13, PaletteBlockSize.XLARGE.getFontSize());
    }

    /* ---- PaletteBlockSize: block dimension ---- */

    @Test
    public void blockDimensionMatchesPerSize() {
        assertEquals(new Dimension(50, 50), PaletteBlockSize.TINY.getBlockDimension());
        assertEquals(new Dimension(75, 75), PaletteBlockSize.SMALL.getBlockDimension());
        assertEquals(new Dimension(100, 100), PaletteBlockSize.NORMAL.getBlockDimension());
        assertEquals(new Dimension(120, 120), PaletteBlockSize.LARGE.getBlockDimension());
        assertEquals(new Dimension(140, 140), PaletteBlockSize.XLARGE.getBlockDimension());
    }

    @Test
    public void blockDimensionsAreSquare() {
        for (PaletteBlockSize s : PaletteBlockSize.values()) {
            Dimension d = s.getBlockDimension();
            assertEquals(d.width, d.height, s + " should be square");
        }
    }

    /* ---- PaletteBlockSize: max icon width/height (65% of the frame, truncated) ---- */

    @Test
    public void maxIconHeightIs65PercentTruncated() {
        assertEquals(32, PaletteBlockSize.TINY.getMaxIconHeight());   // (int)(50  * 0.65f)
        assertEquals(48, PaletteBlockSize.SMALL.getMaxIconHeight());  // (int)(75  * 0.65f)
        assertEquals(65, PaletteBlockSize.NORMAL.getMaxIconHeight()); // (int)(100 * 0.65f)
        assertEquals(78, PaletteBlockSize.LARGE.getMaxIconHeight());  // (int)(120 * 0.65f)
        assertEquals(91, PaletteBlockSize.XLARGE.getMaxIconHeight()); // (int)(140 * 0.65f)
    }

    @Test
    public void maxIconWidthEqualsMaxIconHeightForSquareFrames() {
        for (PaletteBlockSize s : PaletteBlockSize.values()) {
            assertEquals(s.getMaxIconHeight(), s.getMaxIconWidth(), s.toString());
        }
    }

    @Test
    public void maxIconDimensionsStayBelowTheFrame() {
        for (PaletteBlockSize s : PaletteBlockSize.values()) {
            Dimension d = s.getBlockDimension();
            assertTrue(s.getMaxIconHeight() < d.height, s + " icon height must be inside the frame");
            assertTrue(s.getMaxIconWidth() < d.width, s + " icon width must be inside the frame");
        }
    }

    /* ---- PaletteBlockSize: next()/previous() navigation ---- */

    @Test
    public void nextWalksUpAndStopsAtNullOnTheLargest() {
        assertSame(PaletteBlockSize.SMALL, PaletteBlockSize.TINY.next());
        assertSame(PaletteBlockSize.NORMAL, PaletteBlockSize.SMALL.next());
        assertSame(PaletteBlockSize.LARGE, PaletteBlockSize.NORMAL.next());
        assertSame(PaletteBlockSize.XLARGE, PaletteBlockSize.LARGE.next());
        assertNull(PaletteBlockSize.XLARGE.next(), "no size larger than XLARGE");
    }

    @Test
    public void previousWalksDownAndStopsAtNullOnTheSmallest() {
        assertNull(PaletteBlockSize.TINY.previous(), "no size smaller than TINY");
        assertSame(PaletteBlockSize.TINY, PaletteBlockSize.SMALL.previous());
        assertSame(PaletteBlockSize.SMALL, PaletteBlockSize.NORMAL.previous());
        assertSame(PaletteBlockSize.NORMAL, PaletteBlockSize.LARGE.previous());
        assertSame(PaletteBlockSize.LARGE, PaletteBlockSize.XLARGE.previous());
    }

    @Test
    public void nextAndPreviousAreInverseWhereBothDefined() {
        for (PaletteBlockSize s : PaletteBlockSize.values()) {
            PaletteBlockSize up = s.next();
            if (up != null) {
                assertSame(s, up.previous(), s + ".next().previous() must return to " + s);
            }
            PaletteBlockSize down = s.previous();
            if (down != null) {
                assertSame(s, down.next(), s + ".previous().next() must return to " + s);
            }
        }
    }
}
