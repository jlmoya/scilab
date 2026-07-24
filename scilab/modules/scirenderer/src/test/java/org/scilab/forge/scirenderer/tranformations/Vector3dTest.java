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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hermetic unit tests for {@link Vector3d}, the immutable 3D double vector used
 * throughout the renderer's geometry math.
 */
public class Vector3dTest {

    private static final double EPS = 1e-12;

    @Test
    public void componentConstructorAndGetters() {
        Vector3d v = new Vector3d(1.0, 2.0, 3.0);
        assertEquals(1.0, v.getX(), EPS);
        assertEquals(2.0, v.getY(), EPS);
        assertEquals(3.0, v.getZ(), EPS);
    }

    @Test
    public void arrayConstructorsReadFirstThreeComponents() {
        assertTrue(new Vector3d(new double[] {4, 5, 6}).equals(new Vector3d(4, 5, 6)));
        assertTrue(new Vector3d(new float[] {7f, 8f, 9f}).equals(new Vector3d(7, 8, 9)));
        assertTrue(new Vector3d(new Double[] {1.5, 2.5, 3.5}).equals(new Vector3d(1.5, 2.5, 3.5)));
    }

    @Test
    public void copyConstructorCopiesComponents() {
        Vector3d original = new Vector3d(1, 2, 3);
        Vector3d copy = new Vector3d(original);
        assertTrue(original.equals(copy));
        assertEquals(1.0, copy.getX(), EPS);
    }

    @Test
    public void plusAndMinusAreComponentWise() {
        Vector3d a = new Vector3d(1, 2, 3);
        Vector3d b = new Vector3d(10, 20, 30);
        assertTrue(new Vector3d(11, 22, 33).equals(a.plus(b)));
        assertTrue(new Vector3d(9, 18, 27).equals(b.minus(a)));
    }

    @Test
    public void timesScalesEveryComponent() {
        assertTrue(new Vector3d(2, 4, 6).equals(new Vector3d(1, 2, 3).times(2)));
    }

    @Test
    public void normOfThreeFourZeroIsFive() {
        Vector3d v = new Vector3d(3, 4, 0);
        assertEquals(5.0, v.getNorm(), EPS);
        assertEquals(25.0, v.getNorm2(), EPS);
    }

    @Test
    public void scalarProductIsDotProduct() {
        assertEquals(32.0, new Vector3d(1, 2, 3).scalar(new Vector3d(4, 5, 6)), EPS);
    }

    @Test
    public void normalizedHasUnitLength() {
        Vector3d n = new Vector3d(3, 4, 0).getNormalized();
        assertEquals(1.0, n.getNorm(), 1e-9);
        assertTrue(new Vector3d(0.6, 0.8, 0).equals(n));
    }

    @Test
    public void normalizingTheZeroVectorReturnsZero() {
        Vector3d n = new Vector3d(0, 0, 0).getNormalized();
        assertTrue(n.isZero());
    }

    @Test
    public void crossProductOfBasisVectors() {
        Vector3d cross = Vector3d.product(new Vector3d(1, 0, 0), new Vector3d(0, 1, 0));
        assertTrue(new Vector3d(0, 0, 1).equals(cross));
    }

    @Test
    public void determinantOfCanonicalBasisIsOne() {
        double d = Vector3d.det(new Vector3d(1, 0, 0), new Vector3d(0, 1, 0), new Vector3d(0, 0, 1));
        assertEquals(1.0, d, EPS);
    }

    @Test
    public void barycenterIsWeightedSum() {
        Vector3d bary = Vector3d.getBarycenter(new Vector3d(0, 0, 0), new Vector3d(10, 0, 0), 0.25, 0.75);
        assertTrue(new Vector3d(7.5, 0, 0).equals(bary));
    }

    @Test
    public void setXYZReturnNewVectorsLeavingOriginalUntouched() {
        Vector3d v = new Vector3d(1, 2, 3);
        assertTrue(new Vector3d(9, 2, 3).equals(v.setX(9)));
        assertTrue(new Vector3d(1, 9, 3).equals(v.setY(9)));
        assertTrue(new Vector3d(1, 2, 9).equals(v.setZ(9)));
        // Original is immutable.
        assertTrue(new Vector3d(1, 2, 3).equals(v));
    }

    @Test
    public void isZeroAndIsNearZero() {
        assertTrue(new Vector3d(0, 0, 0).isZero());
        assertFalse(new Vector3d(1, 0, 0).isZero());
        assertTrue(new Vector3d(1e-10, -1e-10, 0).isNearZero());
        assertFalse(new Vector3d(1e-3, 0, 0).isNearZero());
    }

    @Test
    public void getDataRoundTrips() {
        assertArrayEquals(new double[] {1, 2, 3}, new Vector3d(1, 2, 3).getData(), EPS);
        assertArrayEquals(new float[] {1, 2, 3}, new Vector3d(1, 2, 3).getDataAsFloatArray(), 0f);
    }

    @Test
    public void getDataAsFloatArraySizeFourAppendsHomogeneousOne() {
        assertArrayEquals(new float[] {1, 2, 3, 1}, new Vector3d(1, 2, 3).getDataAsFloatArray(4), 0f);
        // Any size other than 4 yields the plain 3-component array.
        assertArrayEquals(new float[] {1, 2, 3}, new Vector3d(1, 2, 3).getDataAsFloatArray(3), 0f);
    }

    @Test
    public void equalsUsesEpsilonTolerance() {
        Vector3d a = new Vector3d(1, 2, 3);
        Vector3d b = new Vector3d(1 + 1e-10, 2, 3);
        assertTrue(a.equals(b), "differences below 1e-9 are treated as equal");
        assertFalse(a.equals(new Vector3d(1.1, 2, 3)));
        assertFalse(a.equals("not a vector"));
    }

    @Test
    public void toStringFormat() {
        assertEquals("[1.0, 2.0, 3.0]", new Vector3d(1, 2, 3).toString());
    }

    @Test
    public void equalsHashCodeContractIsViolatedForNearButUnequalDoubles() {
        // Defect characterization: equals() compares with a 1e-9 tolerance while
        // hashCode() hashes the exact double bits. Two vectors that are equals()
        // therefore can (and here do) produce different hash codes, breaking the
        // Object.equals/hashCode contract. This test documents the current behavior.
        Vector3d a = new Vector3d(1, 2, 3);
        Vector3d b = new Vector3d(1 + 1e-10, 2, 3);
        assertTrue(a.equals(b));
        assertNotEquals(a.hashCode(), b.hashCode());
    }
}
