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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link ScilabPolynomial}.
 */
public class ScilabPolynomialTest {

    @Test
    public void defaultConstructorIsEmpty() {
        ScilabPolynomial p = new ScilabPolynomial();
        assertTrue(p.isEmpty());
        assertEquals(0, p.getHeight());
        assertEquals(0, p.getWidth());
        assertEquals("[]", p.toString());
        assertEquals(ScilabTypeEnum.sci_poly, p.getType());
        assertFalse(p.isReference());
        assertFalse(p.isSwaped());
    }

    @Test
    public void singlePolynomialDefaultsToVariableX() {
        ScilabPolynomial p = new ScilabPolynomial(new double[] {1.0, 2.0, 3.0});
        assertFalse(p.isEmpty());
        assertTrue(p.isReal());
        assertEquals(1, p.getHeight());
        assertEquals(1, p.getWidth());
        assertEquals("x", p.getPolyVarName());
        assertArrayEqualsCoeffs(new double[] {1.0, 2.0, 3.0}, p.getRealPart()[0][0]);
    }

    @Test
    public void polynomialVariableNameIsConfigurable() {
        ScilabPolynomial p = new ScilabPolynomial(new double[] {1.0, 2.0}, "s");
        assertEquals("s", p.getPolyVarName());
        p.setPolyVarName("z");
        assertEquals("z", p.getPolyVarName());
    }

    @Test
    public void complexPolynomialIsNotReal() {
        ScilabPolynomial p = new ScilabPolynomial(new double[] {1.0, 2.0}, new double[] {3.0, 4.0});
        assertFalse(p.isReal());
        assertFalse(p.isEmpty());
        assertEquals(3.0, p.getImaginaryPart()[0][0][0], 0.0);
    }

    @Test
    public void settersReplaceParts() {
        ScilabPolynomial p = new ScilabPolynomial(new double[] {1.0});
        p.setRealPart(new double[][][] {{{5.0, 6.0}}});
        assertEquals(6.0, p.getRealPart()[0][0][1], 0.0);
        assertTrue(p.isReal());
        p.setImaginaryPart(new double[][][] {{{7.0}}});
        assertFalse(p.isReal());
    }

    @Test
    public void namedConstructorCarriesMetadata() {
        double[][][] re = {{{1.0, 2.0}}};
        ScilabPolynomial p = new ScilabPolynomial("P", "q", re, null, true);
        assertEquals("P", p.getVarName());
        assertEquals("q", p.getPolyVarName());
        assertTrue(p.isSwaped());
        assertTrue(p.isReal());
    }

    @Test
    public void equalsRequiresSamePolyVarAndCoefficients() {
        ScilabPolynomial a = new ScilabPolynomial(new double[] {1.0, 2.0}, "x");
        ScilabPolynomial b = new ScilabPolynomial(new double[] {1.0, 2.0}, "x");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());

        // Same coefficients, different polynomial variable => not equal.
        ScilabPolynomial differentVar = new ScilabPolynomial(new double[] {1.0, 2.0}, "y");
        assertNotEquals(a, differentVar);

        // Same variable, different coefficients => not equal.
        ScilabPolynomial differentCoeffs = new ScilabPolynomial(new double[] {1.0, 9.0}, "x");
        assertNotEquals(a, differentCoeffs);

        assertNotEquals(a, "not a polynomial");
    }

    @Test
    public void equalsForComplexPolynomials() {
        ScilabPolynomial a = new ScilabPolynomial(new double[] {1.0}, new double[] {2.0});
        ScilabPolynomial b = new ScilabPolynomial(new double[] {1.0}, new double[] {2.0});
        ScilabPolynomial c = new ScilabPolynomial(new double[] {1.0}, new double[] {5.0});
        assertEquals(a, b);
        assertNotEquals(a, c);
    }

    @Test
    public void toStringRendersPolyConstructorCall() {
        ScilabPolynomial p = new ScilabPolynomial(new double[] {1.0, 2.0}, "x");
        assertEquals("[poly([1.0, 2.0], \"x\", \"coeff\")]", p.toString());
    }

    private static void assertArrayEqualsCoeffs(double[] expected, double[] actual) {
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], actual[i], 0.0);
        }
    }
}
