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

package org.scilab.modules.graphic_objects.fec;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties;
import org.scilab.modules.graphic_objects.graphicObject.GraphicObject.UpdateStatus;

/**
 * Hermetic unit tests for the {@link Fec} graphic object: z-bounds, outside
 * colour and colour range, each a two-element vector.
 */
public class FecTest {

    @Test
    public void typeIsFec() {
        assertEquals(Integer.valueOf(GraphicObjectProperties.__GO_FEC__), new Fec().getType());
    }

    @Test
    public void constructorDefaults() {
        Fec f = new Fec();
        assertArrayEquals(new Double[] {0.0, 0.0}, f.getZBounds());
        assertArrayEquals(new Integer[] {0, 0}, f.getOutsideColor());
        assertArrayEquals(new Integer[] {0, 0}, f.getColorRange());
    }

    @Test
    public void zBoundsRoundTrips() {
        Fec f = new Fec();
        assertEquals(UpdateStatus.Success, f.setZBounds(new Double[] {-1.5, 2.5}));
        assertArrayEquals(new Double[] {-1.5, 2.5}, f.getZBounds());
    }

    @Test
    public void outsideColorRoundTrips() {
        Fec f = new Fec();
        assertEquals(UpdateStatus.Success, f.setOutsideColor(new Integer[] {3, 4}));
        assertArrayEquals(new Integer[] {3, 4}, f.getOutsideColor());
    }

    @Test
    public void colorRangeRoundTrips() {
        Fec f = new Fec();
        assertEquals(UpdateStatus.Success, f.setColorRange(new Integer[] {5, 6}));
        assertArrayEquals(new Integer[] {5, 6}, f.getColorRange());
    }

    @Test
    public void gettersReturnDefensiveCopies() {
        Fec f = new Fec();
        f.setZBounds(new Double[] {1.0, 2.0});
        Double[] fetched = f.getZBounds();
        fetched[0] = 99.0;
        // Mutating the returned array must not affect the stored bounds.
        assertEquals(Double.valueOf(1.0), f.getZBounds()[0]);
    }

    @Test
    public void inheritsClippableDefaults() {
        Fec f = new Fec();
        // Inherited from ClippableContouredObject: clipping is OFF (ordinal 0).
        assertEquals(Integer.valueOf(0), f.getClipState());
        assertFalse(f.getClipBoxSet());
    }
}
