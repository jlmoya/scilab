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

package org.scilab.modules.graph.shape;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Rectangle;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.mxgraph.util.mxConstants;
import com.mxgraph.view.mxCellState;

/**
 * Hermetic unit tests for {@link SvgShape#getImageBounds}, the pure-geometry
 * half of the SVG label shape (the {@code paintShape} half needs a live
 * {@link com.mxgraph.canvas.mxGraphics2DCanvas} and is not exercised here).
 *
 * {@code getImageBounds} rotates the cell rectangle about its own centre by the
 * {@code STYLE_ROTATION} style value and returns the axis-aligned bounding box.
 * The {@code canvas} argument is never dereferenced by the method, so a
 * {@code null} canvas keeps the test fully off any rendering surface.
 */
public class SvgShapeTest {

    /** A 30x40 cell rooted at (10,20); its centre is (25,40). */
    private static mxCellState stateWithRotation(Object rotation) {
        mxCellState state = new mxCellState();
        state.setX(10);
        state.setY(20);
        state.setWidth(30);
        state.setHeight(40);

        Map<String, Object> style = new HashMap<String, Object>();
        if (rotation != null) {
            style.put(mxConstants.STYLE_ROTATION, rotation);
        }
        state.setStyle(style);
        return state;
    }

    @Test
    public void unsetRotationYieldsTheRawRectangleBounds() {
        SvgShape shape = new SvgShape();
        Rectangle bounds = shape.getImageBounds(null, stateWithRotation(null));

        assertEquals(new Rectangle(10, 20, 30, 40), bounds);
    }

    @Test
    public void zeroRotationYieldsTheRawRectangleBounds() {
        SvgShape shape = new SvgShape();
        Rectangle bounds = shape.getImageBounds(null, stateWithRotation("0"));

        assertEquals(new Rectangle(10, 20, 30, 40), bounds);
    }

    @Test
    public void ninetyDegreeRotationSwapsWidthAndHeightAroundTheCentre() {
        // Rotating a 30x40 rectangle 90 degrees about centre (25,40) gives a
        // 40x30 box still centred on (25,40): x=5, y=25.
        SvgShape shape = new SvgShape();
        Rectangle bounds = shape.getImageBounds(null, stateWithRotation("90"));

        assertEquals(new Rectangle(5, 25, 40, 30), bounds);
    }

    @Test
    public void oneHundredEightyDegreeRotationMapsTheRectangleOntoItself() {
        // A 180 degree turn about the centre maps every corner to the opposite
        // corner, so the axis-aligned bounds are unchanged.
        SvgShape shape = new SvgShape();
        Rectangle bounds = shape.getImageBounds(null, stateWithRotation("180"));

        assertEquals(new Rectangle(10, 20, 30, 40), bounds);
    }

    @Test
    public void rotationIsTruncatedToAnIntegerNumberOfDegrees() {
        // getImageBounds casts the style value to int, so 90.9 is treated as 90.
        SvgShape shape = new SvgShape();
        Rectangle bounds = shape.getImageBounds(null, stateWithRotation("90.9"));

        assertEquals(new Rectangle(5, 25, 40, 30), bounds);
    }
}
