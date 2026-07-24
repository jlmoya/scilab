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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hermetic unit tests for {@link Rotation}, a unit-quaternion rotation.
 */
public class RotationTest {

    private static final double EPS = 1e-9;

    @Test
    public void defaultConstructorIsIdentity() {
        Rotation r = new Rotation();
        assertTrue(r.isIdentity());
        assertArrayEquals(new double[] {1, 0, 0, 0}, r.getData(), EPS);
    }

    @Test
    public void identityRotationLeavesVectorsUnchanged() {
        Rotation r = new Rotation();
        Vector3d v = new Vector3d(1, 2, 3);
        assertTrue(v.equals(r.conjugate(v)));
    }

    @Test
    public void ninetyDegreesAboutZMapsXToMinusY() {
        // In this quaternion/matrix convention conjugate() rotates (1,0,0) to (0,-1,0).
        Rotation r = new Rotation(Math.PI / 2, new Vector3d(0, 0, 1));
        Vector3d rotated = r.conjugate(new Vector3d(1, 0, 0));
        assertTrue(new Vector3d(0, -1, 0).equals(rotated), "actual: " + rotated);
    }

    @Test
    public void inverseUndoesTheRotation() {
        Rotation r = new Rotation(Math.PI / 2, new Vector3d(0, 0, 1));
        Vector3d rotated = r.conjugate(new Vector3d(1, 0, 0));
        Vector3d back = r.getInverse().conjugate(rotated);
        assertTrue(new Vector3d(1, 0, 0).equals(back), "actual: " + back);
    }

    @Test
    public void conjugateInverseIsTheInverseOfConjugate() {
        Rotation r = new Rotation(0.7, new Vector3d(1, 2, 3));
        Vector3d v = new Vector3d(4, -5, 6);
        assertTrue(v.equals(r.conjugateInverse(r.conjugate(v))));
    }

    @Test
    public void getDegreeRotationZeroIsIdentity() {
        Rotation r = Rotation.getDegreeRotation(0, new Vector3d(0, 0, 1));
        assertTrue(r.isIdentity());
    }

    @Test
    public void getDegreeRotationThreeSixtyIsIdentity() {
        Rotation r = Rotation.getDegreeRotation(360, new Vector3d(0, 1, 0));
        assertTrue(r.isIdentity());
    }

    @Test
    public void getDegreeRotationOneEightyAboutZFlipsX() {
        Rotation r = Rotation.getDegreeRotation(180, new Vector3d(0, 0, 1));
        assertFalse(r.isIdentity());
        Vector3d rotated = r.conjugate(new Vector3d(1, 0, 0));
        assertTrue(new Vector3d(-1, 0, 0).equals(rotated), "actual: " + rotated);
    }

    @Test
    public void rotationMatrixIsCloned() {
        Rotation r = new Rotation();
        double[] m = r.getRotationMatrix();
        m[0] = 42;
        assertNotSame(m, r.getRotationMatrix());
        assertEquals(1.0, r.getRotationMatrix()[0], EPS);
    }

    @Test
    public void powerTwoOfNinetyDegreesIsOneEighty() {
        Rotation r = new Rotation(Math.PI / 2, new Vector3d(0, 0, 1));
        Vector3d rotated = r.power(2).conjugate(new Vector3d(1, 0, 0));
        assertTrue(new Vector3d(-1, 0, 0).equals(rotated), "actual: " + rotated);
    }

    @Test
    public void powerOfIdentityIsIdentity() {
        // For a near-identity rotation (sin < 0.001) power() short-circuits to identity.
        assertTrue(new Rotation().power(5).isIdentity());
    }

    @Test
    public void arrayConstructorWithBadLengthFallsBackToIdentity() {
        // A malformed array (not length 4) yields the identity quaternion.
        Rotation r = new Rotation(new double[] {1, 2, 3});
        assertTrue(r.isIdentity());
        assertTrue(new Rotation(new float[] {0f}).isIdentity());
    }

    @Test
    public void equalsAndHashCodeForIdenticalQuaternions() {
        Rotation a = new Rotation();
        Rotation b = new Rotation();
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertEquals(a, a);
    }

    @Test
    public void aRealRotationIsNotEqualToIdentity() {
        Rotation r = new Rotation(Math.PI / 2, new Vector3d(0, 0, 1));
        assertFalse(r.equals(new Rotation()));
        assertFalse(r.equals("not a rotation"));
    }

    @Test
    public void getDataAsFloatArrayMatchesGetData() {
        Rotation r = new Rotation(0.3, new Vector3d(1, 1, 0));
        double[] d = r.getData();
        float[] f = r.getDataAsFloatArray();
        for (int i = 0; i < 4; i++) {
            assertEquals((float) d[i], f[i], 0f);
        }
    }
}
