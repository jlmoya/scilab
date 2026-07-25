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

import java.awt.Dimension;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hermetic unit tests for {@link TransformationFactory} and the {@link Transformation}
 * implementations it produces (identity, translate, scale, rotation, orthographic, product).
 */
public class TransformationFactoryTest {

    private static final double[] IDENTITY = new double[] {
        1, 0, 0, 0,
        0, 1, 0, 0,
        0, 0, 1, 0,
        0, 0, 0, 1
    };

    @Test
    public void identityIsIdentityAndProjectsToItself() {
        Transformation id = TransformationFactory.getIdentity();
        assertTrue(id.isIdentity());
        assertArrayEquals(IDENTITY, id.getMatrix(), 0.0);
        Vector3d v = new Vector3d(3, 4, 5);
        assertSame(v, id.project(v));
        assertSame(v, id.unproject(v));
    }

    @Test
    public void zeroTranslationCollapsesToIdentity() {
        assertTrue(TransformationFactory.getTranslateTransformation(0, 0, 0).isIdentity());
    }

    @Test
    public void translationProjectsAndUnprojects() {
        Transformation t = TransformationFactory.getTranslateTransformation(1, 2, 3);
        assertFalse(t.isIdentity());
        assertTrue(new Vector3d(1, 2, 3).equals(t.project(new Vector3d(0, 0, 0))));
        assertTrue(new Vector3d(0, 0, 0).equals(t.unproject(new Vector3d(1, 2, 3))));
        double[] m = t.getMatrix();
        assertEquals(1.0, m[12], 0.0);
        assertEquals(2.0, m[13], 0.0);
        assertEquals(3.0, m[14], 0.0);
    }

    @Test
    public void unitScaleCollapsesToIdentity() throws DegenerateMatrixException {
        assertTrue(TransformationFactory.getScaleTransformation(1, 1, 1).isIdentity());
    }

    @Test
    public void scaleProjectsAndUnprojects() throws DegenerateMatrixException {
        Transformation s = TransformationFactory.getScaleTransformation(2, 3, 4);
        assertTrue(new Vector3d(2, 3, 4).equals(s.project(new Vector3d(1, 1, 1))));
        assertTrue(new Vector3d(1, 1, 1).equals(s.unproject(new Vector3d(2, 3, 4))));
    }

    @Test
    public void uniformScaleScalesAllAxes() throws DegenerateMatrixException {
        Transformation s = TransformationFactory.getScaleTransformation(5);
        assertTrue(new Vector3d(5, 5, 5).equals(s.project(new Vector3d(1, 1, 1))));
    }

    @Test
    public void zeroScaleFactorThrows() {
        assertThrows(DegenerateMatrixException.class,
                     () -> TransformationFactory.getScaleTransformation(0, 1, 1));
        assertThrows(DegenerateMatrixException.class,
                     () -> TransformationFactory.getScaleTransformation(0));
    }

    @Test
    public void rotationWithZeroAngleIsIdentity() throws DegenerateMatrixException {
        assertTrue(TransformationFactory.getRotationTransformation(0, 0, 0, 1).isIdentity());
    }

    @Test
    public void rotationAboutNullAxisThrows() {
        assertThrows(DegenerateMatrixException.class,
                     () -> TransformationFactory.getRotationTransformation(90, 0, 0, 0));
    }

    @Test
    public void ninetyDegreeRotationAboutZMapsXToMinusY() throws DegenerateMatrixException {
        // Matches Rotation's convention: project() sends (1,0,0) to (0,-1,0).
        Transformation r = TransformationFactory.getRotationTransformation(90, 0, 0, 1);
        Vector3d projected = r.project(new Vector3d(1, 0, 0));
        assertTrue(new Vector3d(0, -1, 0).equals(projected), "actual: " + projected);
    }

    @Test
    public void rotationFromIdentityQuaternionIsIdentity() {
        assertTrue(TransformationFactory.getRotationTransformation(new Rotation()).isIdentity());
    }

    @Test
    public void getMatrixReturnsADefensiveClone() {
        Transformation t = TransformationFactory.getTranslateTransformation(1, 2, 3);
        double[] m = t.getMatrix();
        m[12] = 99;
        assertNotSame(m, t.getMatrix());
        assertEquals(1.0, t.getMatrix()[12], 0.0);
    }

    @Test
    public void inverseTransformationSwapsMatrixAndInverse() {
        Transformation t = TransformationFactory.getTranslateTransformation(1, 2, 3);
        Transformation inv = t.getInverseTransformation();
        assertArrayEquals(t.getInverseMatrix(), inv.getMatrix(), 0.0);
        assertArrayEquals(t.getMatrix(), inv.getInverseMatrix(), 0.0);
    }

    @Test
    public void rightTimesIdentityReturnsOriginal() {
        Transformation t = TransformationFactory.getTranslateTransformation(1, 2, 3);
        assertSame(t, t.rightTimes(TransformationFactory.getIdentity()));
        assertSame(t, TransformationFactory.getIdentity().rightTimes(t));
    }

    @Test
    public void productOfTwoTranslationsAddsTranslations() {
        Transformation t = TransformationFactory.getTranslateTransformation(1, 0, 0)
                           .rightTimes(TransformationFactory.getTranslateTransformation(2, 0, 0));
        assertTrue(new Vector3d(3, 0, 0).equals(t.project(new Vector3d(0, 0, 0))));
    }

    @Test
    public void affineAppliesScaleThenTranslation() throws DegenerateMatrixException {
        Transformation t = TransformationFactory.getAffineTransformation(new Vector3d(2, 2, 2), new Vector3d(1, 1, 1));
        // 2 * (1,1,1) + (1,1,1) = (3,3,3)
        assertTrue(new Vector3d(3, 3, 3).equals(t.project(new Vector3d(1, 1, 1))));
    }

    @Test
    public void orthographicWithCanonicalBoundsIsIdentity() {
        // getOrthographic(left, right, bottom, top, near, far)
        Transformation ortho = TransformationFactory.getOrthographic(-1, 1, -1, 1, 1, -1);
        assertTrue(ortho.isIdentity());
        assertTrue(new Vector3d(2, 3, 4).equals(ortho.project(new Vector3d(2, 3, 4))));
    }

    @Test
    public void preferredAspectRatioSquashesTheWiderAxis() throws DegenerateMatrixException {
        Transformation t = TransformationFactory.getPreferredAspectRatioTransformation(new Dimension(200, 100), 1.0);
        // ratio = 2 > 1 => scale x by 1/ratio = 0.5, leave y and z.
        assertTrue(new Vector3d(1, 2, 2).equals(t.project(new Vector3d(2, 2, 2))));
    }

    @Test
    public void projectThenUnprojectRoundTripsForOrthographic() {
        Transformation ortho = TransformationFactory.getOrthographic(0, 4, 0, 2, 1, -3);
        Vector3d v = new Vector3d(1.5, 0.75, -0.5);
        assertTrue(v.equals(ortho.unproject(ortho.project(v))));
    }

    // ------------------------------------------------------------- perspective

    @Test
    public void perspectiveMatrixHasTheExpectedEntries() {
        // fov = 90 deg => tan(45) = 1 => f = 1. near = 1, far = 3.
        Transformation p = TransformationFactory.getPerspectiveTransformation(1, 3, 90);
        assertFalse(p.isIdentity());
        double[] m = p.getMatrix();
        assertEquals(1.0, m[0], 1e-9);    // f
        assertEquals(1.0, m[5], 1e-9);    // f
        assertEquals(-2.0, m[10], 1e-9);  // (far+near)/(near-far) = 4/-2
        assertEquals(-1.0, m[11], 1e-9);
        assertEquals(-3.0, m[14], 1e-9);  // 2*far*near/(near-far) = 6/-2
        assertEquals(0.0, m[15], 1e-9);
    }

    @Test
    public void perspectiveInverseMatrixHasTheExpectedEntries() {
        Transformation p = TransformationFactory.getPerspectiveTransformation(1, 3, 90);
        double[] inv = p.getInverseMatrix();
        assertEquals(1.0, inv[0], 1e-9);           // fInv
        assertEquals(1.0, inv[5], 1e-9);           // fInv
        assertEquals(-1.0 / 3.0, inv[11], 1e-9);   // (near-far)/(2*far*near) = -2/6
        assertEquals(-1.0, inv[14], 1e-9);
        assertEquals(2.0 / 3.0, inv[15], 1e-9);    // (near+far)/(2*far*near) = 4/6
    }

    @Test
    public void perspectiveMatrixIsADefensiveClone() {
        Transformation p = TransformationFactory.getPerspectiveTransformation(1, 3, 90);
        double[] m = p.getMatrix();
        m[0] = 42;
        assertNotSame(m, p.getMatrix());
        assertEquals(1.0, p.getMatrix()[0], 1e-9);
    }

    @Test
    public void perspectiveProjectDividesByW() {
        // For (0,0,-2): z' = -2*(-2) + (-3) = 1 ; w = -1*(-2) = 2 ; result z = 0.5.
        Transformation p = TransformationFactory.getPerspectiveTransformation(1, 3, 90);
        Vector3d projected = p.project(new Vector3d(0, 0, -2));
        assertEquals(0.0, projected.getX(), 1e-9);
        assertEquals(0.0, projected.getY(), 1e-9);
        assertEquals(0.5, projected.getZ(), 1e-9);
    }

    // ------------------------------------------------------- projectDirection

    @Test
    public void projectDirectionIgnoresPureTranslation() {
        Transformation t = TransformationFactory.getTranslateTransformation(10, 20, 30);
        Vector3d d = t.projectDirection(new Vector3d(1, 2, 3));
        assertTrue(new Vector3d(1, 2, 3).equals(d), "actual: " + d);
    }

    @Test
    public void projectDirectionAppliesScaleFactors() throws DegenerateMatrixException {
        Transformation s = TransformationFactory.getScaleTransformation(2, 3, 4);
        Vector3d d = s.projectDirection(new Vector3d(1, 1, 1));
        assertTrue(new Vector3d(2, 3, 4).equals(d), "actual: " + d);
    }

    // -------------------------------------------------------------- toString

    @Test
    public void toStringFormatsSixteenMatrixEntries() {
        String s = TransformationFactory.getIdentity().toString();
        assertTrue(s.startsWith("1.0, 0.0, 0.0, 0.0"), "actual: " + s);
        assertTrue(s.contains("\n"), "rows should be newline separated");
    }
}
