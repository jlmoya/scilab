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

package org.scilab.modules.renderer.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.scilab.modules.renderer.utils.DatatipCommon.Segment;

/**
 * Hermetic unit tests for {@link DatatipCommon.Segment}, the pure value
 * holder for a datatip line segment. The enclosing {@link DatatipCommon}
 * class also has controller/renderer-backed helpers, but Segment itself
 * is self-contained arithmetic and runs without any Scilab engine.
 */
class DatatipCommonTest {

    private static final double EPS = 1e-12;

    @Test
    void defaultConstructorLeavesEverythingAtZero() {
        Segment s = new Segment();
        assertEquals(0, s.pointIndex);
        assertEquals(0.0, s.x0, EPS);
        assertEquals(0.0, s.x1, EPS);
        assertEquals(0.0, s.y0, EPS);
        assertEquals(0.0, s.y1, EPS);
        assertEquals(0.0, s.z0, EPS);
        assertEquals(0.0, s.z1, EPS);
        assertEquals(0.0, s.norm2(), EPS);
        assertEquals(0.0, s.norm(), EPS);
    }

    @Test
    void parameterisedConstructorUsesTheXxYyZzArgumentOrder() {
        // Signature is Segment(index, x0, x1, y0, y1, z0, z1): the two
        // endpoints are interleaved per-axis, NOT (x0,y0,z0,x1,y1,z1).
        Segment s = new Segment(7, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0);
        assertEquals(7, s.pointIndex);
        assertEquals(1.0, s.x0, EPS);
        assertEquals(2.0, s.x1, EPS);
        assertEquals(3.0, s.y0, EPS);
        assertEquals(4.0, s.y1, EPS);
        assertEquals(5.0, s.z0, EPS);
        assertEquals(6.0, s.z1, EPS);
    }

    @Test
    void norm2IsTheSquaredEuclideanLength() {
        // deltas (1,1,1) -> 3
        Segment s = new Segment(0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0);
        assertEquals(3.0, s.norm2(), EPS);
    }

    @Test
    void normIsTheEuclideanLength() {
        // classic 3-4-0 -> length 5 along x and y only
        Segment s = new Segment(0, 0.0, 3.0, 0.0, 4.0, 0.0, 0.0);
        assertEquals(25.0, s.norm2(), EPS);
        assertEquals(5.0, s.norm(), EPS);
    }

    @Test
    void normReflectsMutatedPublicFields() {
        Segment s = new Segment();
        s.x0 = 0.0;
        s.x1 = 0.0;
        s.y0 = 0.0;
        s.y1 = 0.0;
        s.z0 = -2.0;
        s.z1 = 4.0;
        // only z differs by 6
        assertEquals(36.0, s.norm2(), EPS);
        assertEquals(6.0, s.norm(), EPS);
    }
}
