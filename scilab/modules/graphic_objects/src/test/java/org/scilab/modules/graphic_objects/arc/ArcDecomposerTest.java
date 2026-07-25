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

package org.scilab.modules.graphic_objects.arc;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Hermetic unit tests for the controller-free sizing surface of
 * {@link ArcDecomposer}. The vertex/index buffer-filling methods read the arc's
 * geometry through GraphicController and are out of scope; the three fixed-size
 * accessors below derive purely from the compile-time sector count and are safe
 * to pin. An arc is fanned into {@code NB_SECTORS} triangles from a centre
 * vertex, so the counts are internally consistent: with {@code D} data vertices
 * there are {@code D - 2} sectors, {@code 3·(D-2)} triangle indices and
 * {@code 2·(D-2)} wire indices.
 */
public class ArcDecomposerTest {

    @Test
    public void dataSizeIsSectorsPlusCentreAndFirstVertex() {
        // NB_SECTORS (64) + 2 = a centre vertex plus NB_SECTORS+1 rim vertices.
        assertEquals(66, ArcDecomposer.getDataSize());
    }

    @Test
    public void indicesSizeIsThreePerSector() {
        assertEquals(192, ArcDecomposer.getIndicesSize());
    }

    @Test
    public void wireIndicesSizeIsTwoPerSector() {
        assertEquals(128, ArcDecomposer.getWireIndicesSize());
    }

    @Test
    public void sizesAreMutuallyConsistent() {
        int sectors = ArcDecomposer.getDataSize() - 2;
        assertEquals(3 * sectors, ArcDecomposer.getIndicesSize());
        assertEquals(2 * sectors, ArcDecomposer.getWireIndicesSize());
    }
}
