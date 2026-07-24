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

package org.scilab.forge.scirenderer.tranformations;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

/**
 * Hermetic unit tests for {@link Vector4d}, an immutable homogeneous 4D vector.
 */
public class Vector4dTest {

    @Test
    public void getDataReturnsTheFourComponents() {
        Vector4d v = new Vector4d(1, 2, 3, 4);
        assertArrayEquals(new double[] {1, 2, 3, 4}, v.getData(), 0.0);
    }

    @Test
    public void getDataReturnsADefensiveCopy() {
        Vector4d v = new Vector4d(1, 2, 3, 4);
        double[] first = v.getData();
        first[0] = 99;
        // Mutating the returned array must not corrupt the vector.
        assertArrayEquals(new double[] {1, 2, 3, 4}, v.getData(), 0.0);
        assertNotSame(first, v.getData());
    }

    @Test
    public void toStringFormat() {
        assertEquals("[1.0, 2.0, 3.0, 4.0]", new Vector4d(1, 2, 3, 4).toString());
    }
}
