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

package org.scilab.modules.graphic_objects.rectangle;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties;
import org.scilab.modules.graphic_objects.graphicObject.GraphicObject.UpdateStatus;

/**
 * Hermetic unit tests for the {@link Rectangle} graphic object: upper-left
 * point plus width/height, and the inherited clipping state machine.
 */
public class RectangleTest {

    @Test
    public void typeIsRectangle() {
        assertEquals(Integer.valueOf(GraphicObjectProperties.__GO_RECTANGLE__), new Rectangle().getType());
    }

    @Test
    public void constructorDefaults() {
        Rectangle r = new Rectangle();
        assertEquals(Double.valueOf(0.0), r.getWidth());
        assertEquals(Double.valueOf(0.0), r.getHeight());
        assertArrayEquals(new Double[] {0.0, 0.0, 0.0}, r.getUpperLeftPoint());
    }

    @Test
    public void geometrySettersRoundTrip() {
        Rectangle r = new Rectangle();
        assertEquals(UpdateStatus.Success, r.setWidth(6.0));
        assertEquals(Double.valueOf(6.0), r.getWidth());
        assertEquals(UpdateStatus.Success, r.setHeight(8.0));
        assertEquals(Double.valueOf(8.0), r.getHeight());
        assertEquals(UpdateStatus.Success, r.setUpperLeftPoint(new Double[] {1.0, 2.0, 3.0}));
        assertArrayEquals(new Double[] {1.0, 2.0, 3.0}, r.getUpperLeftPoint());
    }

    @Test
    public void upperLeftPointGetterReturnsCopy() {
        Rectangle r = new Rectangle();
        r.setUpperLeftPoint(new Double[] {4.0, 5.0, 6.0});
        Double[] fetched = r.getUpperLeftPoint();
        assertEquals(3, fetched.length);
        fetched[1] = 99.0;
        assertEquals(Double.valueOf(5.0), r.getUpperLeftPoint()[1]);
    }

    @Test
    public void clipStateOnIsDowngradedToClipgrfUntilClipBoxIsSet() {
        Rectangle r = new Rectangle();
        assertEquals(Integer.valueOf(0), r.getClipState()); // OFF by default
        assertFalse(r.getClipBoxSet());

        // Requesting ON (2) before any clip box is set silently downgrades to
        // CLIPGRF (1) -- documented ClippableProperty behaviour.
        r.setClipState(2);
        assertEquals(Integer.valueOf(1), r.getClipState());

        // Once a clip box exists, ON sticks.
        r.setClipBox(new Double[] {0.0, 0.0, 1.0, 1.0});
        assertTrue(r.getClipBoxSet());
        r.setClipState(2);
        assertEquals(Integer.valueOf(2), r.getClipState());
    }

    @Test
    public void inheritedContouredSettersWork() {
        Rectangle r = new Rectangle();
        assertEquals(UpdateStatus.Success, r.setFillMode(true));
        assertTrue(r.getFillMode());
        assertEquals(UpdateStatus.NoChange, r.setFillMode(true));

        assertEquals(UpdateStatus.Success, r.setBackground(12));
        assertEquals(Integer.valueOf(12), r.getBackground());
    }
}
