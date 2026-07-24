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

package org.scilab.modules.graphic_objects.figure;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import org.scilab.modules.graphic_objects.graphicObject.GraphicObject.UpdateStatus;

/**
 * Hermetic unit tests for {@link ColorMap}: a colour table whose data is a
 * column-major [R..., G..., B...] Double array, with Scilab-index colour
 * lookup (including the special negative indices) and channel clamping.
 */
public class ColorMapTest {

    private static final float EPS = 1e-6f;

    /** Two-colour map: red channel {0.1,0.2}, green {0.3,0.4}, blue {0.5,0.6}. */
    private static ColorMap twoColours() {
        ColorMap cm = new ColorMap();
        cm.setData(new Double[] {0.1, 0.2, 0.3, 0.4, 0.5, 0.6});
        return cm;
    }

    @Test
    public void defaultMapIsEmpty() {
        ColorMap cm = new ColorMap();
        assertEquals(0, cm.getSize());
        assertEquals(0, cm.getData().length);
    }

    @Test
    public void setDataStoresWholeMultipleOfThree() {
        ColorMap cm = twoColours();
        assertEquals(UpdateStatus.Success, cm.setData(new Double[] {0.0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.9}));
        assertEquals(3, cm.getSize());
        assertEquals(9, cm.getData().length);
    }

    @Test
    public void setDataTruncatesToMultipleOfThree() {
        ColorMap cm = new ColorMap();
        // Length 7 -> 7 - (7 % 3) = 6 stored.
        assertEquals(UpdateStatus.Success, cm.setData(new Double[] {0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7}));
        assertEquals(6, cm.getData().length);
        assertEquals(2, cm.getSize());
    }

    @Test
    public void setDataWithIdenticalContentReportsNoChange() {
        ColorMap cm = twoColours();
        // A distinct array instance with equal values must still be NoChange.
        assertEquals(UpdateStatus.NoChange, cm.setData(new Double[] {0.1, 0.2, 0.3, 0.4, 0.5, 0.6}));
    }

    @Test
    public void getDataReturnsDefensiveCopy() {
        ColorMap cm = twoColours();
        Double[] snapshot = cm.getData();
        snapshot[0] = 99.0;
        assertEquals(0.1, cm.getData()[0], EPS);
    }

    @Test
    public void scilabColourLookupReadsColumnMajorChannels() {
        ColorMap cm = twoColours();
        // Scilab index 1 -> first colour: (R0,G0,B0) = (0.1, 0.3, 0.5).
        assertArrayEquals(new float[] {0.1f, 0.3f, 0.5f}, cm.getScilabColor(1), EPS);
        // Scilab index 2 -> second colour: (R1,G1,B1) = (0.2, 0.4, 0.6).
        assertArrayEquals(new float[] {0.2f, 0.4f, 0.6f}, cm.getScilabColor(2), EPS);
    }

    @Test
    public void indexZeroIsTreatedAsBlackSentinel() {
        // index 0 is remapped to -1, which is the black sentinel.
        assertArrayEquals(new float[] {0f, 0f, 0f}, twoColours().getScilabColor(0), EPS);
    }

    @Test
    public void negativeSentinelsResolveToBlackAndWhite() {
        ColorMap cm = twoColours();
        assertArrayEquals(new float[] {0f, 0f, 0f}, cm.getScilabColor(-1), EPS);  // black
        assertArrayEquals(new float[] {1f, 1f, 1f}, cm.getScilabColor(-2), EPS);  // white
        assertArrayEquals(new float[] {0f, 0f, 0f}, cm.getScilabColor(-5), EPS);  // < -2 -> black
    }

    @Test
    public void oneAndTwoPastLastIndexWrapToBlackAndWhite() {
        ColorMap cm = twoColours(); // last Scilab index = 2
        // index 3 -> 2-3 = -1 -> black; index 4 -> 2-4 = -2 -> white.
        assertArrayEquals(new float[] {0f, 0f, 0f}, cm.getScilabColor(3), EPS);
        assertArrayEquals(new float[] {1f, 1f, 1f}, cm.getScilabColor(4), EPS);
    }

    @Test
    public void channelValuesAreClampedToOne() {
        ColorMap cm = new ColorMap();
        // Red channel value 2.0 for the single colour must clamp to 1.0.
        cm.setData(new Double[] {2.0, 0.25, 0.5});
        assertArrayEquals(new float[] {1f, 0.25f, 0.5f}, cm.getScilabColor(1), EPS);
    }

    @Test
    public void copyConstructorReproducesData() {
        ColorMap src = twoColours();
        ColorMap copy = new ColorMap(src);
        assertEquals(src.getSize(), copy.getSize());
        assertArrayEquals(src.getData(), copy.getData());
        assertArrayEquals(new float[] {0.2f, 0.4f, 0.6f}, copy.getScilabColor(2), EPS);
    }
}
