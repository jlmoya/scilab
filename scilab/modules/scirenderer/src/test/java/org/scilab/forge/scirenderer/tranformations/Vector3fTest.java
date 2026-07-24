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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hermetic unit tests for {@link Vector3f}, the immutable single-precision 3D vector.
 */
public class Vector3fTest {

    private static final float EPS = 1e-6f;

    @Test
    public void gettersReturnConstructorArguments() {
        Vector3f v = new Vector3f(1f, 2f, 3f);
        assertEquals(1f, v.getX(), 0f);
        assertEquals(2f, v.getY(), 0f);
        assertEquals(3f, v.getZ(), 0f);
    }

    @Test
    public void asDoubleWidensComponents() {
        Vector3d d = new Vector3f(1f, 2f, 3f).asDouble();
        assertTrue(new Vector3d(1, 2, 3).equals(d));
    }

    @Test
    public void plusMinusNegateTimes() {
        Vector3f a = new Vector3f(1f, 2f, 3f);
        Vector3f b = new Vector3f(10f, 20f, 30f);

        Vector3f sum = a.plus(b);
        assertEquals(11f, sum.getX(), EPS);
        assertEquals(33f, sum.getZ(), EPS);

        Vector3f diff = b.minus(a);
        assertEquals(9f, diff.getX(), EPS);
        assertEquals(27f, diff.getZ(), EPS);

        Vector3f neg = a.negate();
        assertEquals(-1f, neg.getX(), EPS);
        assertEquals(-3f, neg.getZ(), EPS);

        Vector3f scaled = a.times(2f);
        assertEquals(2f, scaled.getX(), EPS);
        assertEquals(6f, scaled.getZ(), EPS);
    }

    @Test
    public void normAndNorm2() {
        Vector3f v = new Vector3f(3f, 4f, 0f);
        assertEquals(25f, v.getNorm2(), EPS);
        assertEquals(5f, v.getNorm(), EPS);
    }

    @Test
    public void scalarProduct() {
        assertEquals(32f, new Vector3f(1f, 2f, 3f).scalar(new Vector3f(4f, 5f, 6f)), EPS);
    }

    @Test
    public void normalizedHasUnitLength() {
        Vector3f n = new Vector3f(0f, 3f, 4f).getNormalized();
        assertEquals(1f, n.getNorm(), 1e-5f);
        assertEquals(0.6f, n.getY(), 1e-5f);
        assertEquals(0.8f, n.getZ(), 1e-5f);
    }

    @Test
    public void normalizingZeroReturnsZero() {
        Vector3f n = new Vector3f(0f, 0f, 0f).getNormalized();
        assertEquals(0f, n.getNorm(), 0f);
    }

    @Test
    public void crossProductOfBasisVectors() {
        Vector3f cross = Vector3f.product(new Vector3f(1f, 0f, 0f), new Vector3f(0f, 1f, 0f));
        assertEquals(0f, cross.getX(), EPS);
        assertEquals(0f, cross.getY(), EPS);
        assertEquals(1f, cross.getZ(), EPS);
    }

    @Test
    public void determinantOfCanonicalBasisIsOne() {
        float d = Vector3f.det(new Vector3f(1f, 0f, 0f), new Vector3f(0f, 1f, 0f), new Vector3f(0f, 0f, 1f));
        assertEquals(1f, d, EPS);
    }

    @Test
    public void barycenterIsWeightedSum() {
        Vector3f bary = Vector3f.getBarycenter(new Vector3f(0f, 0f, 0f), new Vector3f(8f, 0f, 0f), 0.25f, 0.75f);
        assertEquals(6f, bary.getX(), EPS);
    }

    @Test
    public void toStringFormat() {
        assertEquals("[1.0, 2.0, 3.0]", new Vector3f(1f, 2f, 3f).toString());
    }
}
