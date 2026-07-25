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

/**
 * Hermetic unit tests for the controller-free sizing surface of
 * {@link RectangleDecomposer}. A rectangle decomposes into four corner vertices,
 * two triangles (six triangle indices) and a four-segment outline (eight wire
 * indices); the buffer-filling methods that need the object's geometry go
 * through GraphicController and are out of scope.
 */
public class RectangleDecomposerTest {

    @Test
    public void dataSizeIsFourCorners() {
        assertEquals(4, RectangleDecomposer.getDataSize());
    }

    @Test
    public void indicesSizeIsSixForTwoTriangles() {
        assertEquals(6, RectangleDecomposer.getIndicesSize());
        // Two triangles, three indices each.
        assertEquals(0, RectangleDecomposer.getIndicesSize() % 3);
    }

    @Test
    public void wireIndicesSizeIsEightForFourSegments() {
        assertEquals(8, RectangleDecomposer.getWireIndicesSize());
        // Four line segments, two endpoints each.
        assertEquals(0, RectangleDecomposer.getWireIndicesSize() % 2);
    }
}
