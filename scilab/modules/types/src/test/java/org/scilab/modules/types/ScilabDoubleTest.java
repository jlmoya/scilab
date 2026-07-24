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

package org.scilab.modules.types;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link ScilabDouble}, covering the real/complex split,
 * element access, the serialized column-major complex form, and the Scilab-literal
 * {@code toString} (integers, decimals, %inf and %nan).
 */
public class ScilabDoubleTest {

    private static final double EPS = 0.0;

    @Test
    public void defaultConstructorIsEmptyRealAndZeroSized() {
        ScilabDouble d = new ScilabDouble();
        assertTrue(d.isEmpty());
        assertTrue(d.isReal());
        assertEquals(0, d.getHeight());
        assertEquals(0, d.getWidth());
        assertEquals("[]", d.toString());
        assertEquals(ScilabTypeEnum.sci_matrix, d.getType());
        assertFalse(d.isReference());
    }

    @Test
    public void realScalarConstructor() {
        ScilabDouble d = new ScilabDouble(42.0);
        assertFalse(d.isEmpty());
        assertTrue(d.isReal());
        assertEquals(1, d.getHeight());
        assertEquals(1, d.getWidth());
        assertEquals(42.0, d.getRealElement(0, 0), EPS);
    }

    @Test
    public void complexScalarConstructor() {
        ScilabDouble d = new ScilabDouble(3.0, 4.0);
        assertFalse(d.isReal());
        assertEquals(3.0, d.getRealElement(0, 0), EPS);
        assertEquals(4.0, d.getImaginaryElement(0, 0), EPS);
        assertArrayEquals(new double[] {3.0, 4.0}, d.getElement(0, 0), EPS);
    }

    @Test
    public void realMatrixConstructorAndDimensions() {
        double[][] a = {{1.0, 2.0, 3.0}, {4.0, 5.0, 6.0}};
        ScilabDouble d = new ScilabDouble(a);
        assertEquals(2, d.getHeight());
        assertEquals(3, d.getWidth());
        assertTrue(d.isReal());
        assertEquals(6.0, d.getRealElement(1, 2), EPS);
    }

    @Test
    public void nullRealPartConstructorIsEmpty() {
        ScilabDouble d = new ScilabDouble((double[][]) null);
        assertTrue(d.isEmpty());
        assertEquals(0, d.getHeight());
    }

    @Test
    public void namedConstructorCarriesVarNameAndSwap() {
        double[][] a = {{7.0}};
        ScilabDouble d = new ScilabDouble("v", a, null, true);
        assertEquals("v", d.getVarName());
        assertTrue(d.isSwaped());
        assertTrue(d.isReal());
    }

    @Test
    public void setElementMutatesRealAndImaginary() {
        ScilabDouble d = new ScilabDouble(new double[][] {{1.0}}, new double[][] {{2.0}});
        d.setElement(0, 0, 9.0, 8.0);
        assertEquals(9.0, d.getRealElement(0, 0), EPS);
        assertEquals(8.0, d.getImaginaryElement(0, 0), EPS);
        d.setRealElement(0, 0, 5.0);
        assertEquals(5.0, d.getRealElement(0, 0), EPS);
    }

    @Test
    public void equalsForRealMatrices() {
        ScilabDouble a = new ScilabDouble(new double[][] {{1.0, 2.0}, {3.0, 4.0}});
        ScilabDouble b = new ScilabDouble(new double[][] {{1.0, 2.0}, {3.0, 4.0}});
        ScilabDouble c = new ScilabDouble(new double[][] {{1.0, 2.0}, {3.0, 9.0}});
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }

    @Test
    public void equalsForComplexAndAcrossRealness() {
        ScilabDouble complexA = new ScilabDouble(1.0, 2.0);
        ScilabDouble complexB = new ScilabDouble(1.0, 2.0);
        ScilabDouble complexC = new ScilabDouble(1.0, 7.0);
        assertEquals(complexA, complexB);
        assertNotEquals(complexA, complexC);
        // A real and a complex value are never equal, even with matching real parts.
        assertNotEquals(new ScilabDouble(1.0), complexA);
    }

    @Test
    public void twoEmptyDoublesAreEqual() {
        assertEquals(new ScilabDouble(), new ScilabDouble());
        assertNotEquals(new ScilabDouble(), "not a double");
    }

    @Test
    public void differentDimensionsAreNotEqual() {
        ScilabDouble a = new ScilabDouble(new double[][] {{1.0, 2.0}});
        ScilabDouble b = new ScilabDouble(new double[][] {{1.0}, {2.0}});
        assertNotEquals(a, b);
    }

    @Test
    public void getSerializedComplexMatrixIsColumnMajorRealsThenImags() {
        // 2x2 complex: reals {{1,2},{3,4}}, imags {{5,6},{7,8}}.
        double[][] re = {{1.0, 2.0}, {3.0, 4.0}};
        double[][] im = {{5.0, 6.0}, {7.0, 8.0}};
        ScilabDouble d = new ScilabDouble(re, im);
        double[] s = d.getSerializedComplexMatrix();
        // size = 4; first 4 are reals column-major, next 4 the imaginaries column-major.
        assertArrayEquals(new double[] {1.0, 3.0, 2.0, 4.0, 5.0, 7.0, 6.0, 8.0}, s, EPS);
    }

    @Test
    public void getSerializedObjectShapeReflectsRealness() {
        Object[] real = (Object[]) new ScilabDouble(new double[][] {{1.0}}).getSerializedObject();
        assertEquals(1, real.length);
        Object[] complex = (Object[]) new ScilabDouble(1.0, 2.0).getSerializedObject();
        assertEquals(2, complex.length);
    }

    @Test
    public void toStringScalarIntegerValued() {
        assertEquals("5", new ScilabDouble(5.0).toString());
        assertEquals("-3", new ScilabDouble(-3.0).toString());
    }

    @Test
    public void toStringScalarDecimal() {
        assertEquals("2.5", new ScilabDouble(2.5).toString());
    }

    @Test
    public void toStringScalarInfinitiesAndNaN() {
        assertEquals("%inf", new ScilabDouble(Double.POSITIVE_INFINITY).toString());
        assertEquals("-%inf", new ScilabDouble(Double.NEGATIVE_INFINITY).toString());
        assertEquals("%nan", new ScilabDouble(Double.NaN).toString());
    }

    @Test
    public void toStringRealMatrix() {
        ScilabDouble d = new ScilabDouble(new double[][] {{1.0, 2.0}, {3.0, 4.0}});
        assertEquals("[1, 2 ; 3, 4]", d.toString());
    }

    @Test
    public void toStringComplexScalarWithinMatrixBrackets() {
        // A complex scalar is not the special real-scalar case, so it is bracketed.
        assertEquals("[3 + 4 * %i]", new ScilabDouble(3.0, 4.0).toString());
    }
}
