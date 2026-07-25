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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link ScilabSparse}: the compressed-row representation
 * (nbItemRow / colPos / realPart), scalar and matrix constructors, (i,j) element
 * lookup over the compressed layout, the full-matrix reconstruction, validity
 * checking, equality (including the "trailing zeros" nbItemRow comparison), the
 * Scilab-literal {@code toString}, and an Externalizable serialization round-trip.
 */
public class ScilabSparseTest {

    private static final double EPS = 0.0;

    @Test
    public void defaultConstructorIsEmpty() {
        ScilabSparse s = new ScilabSparse();
        assertTrue(s.isEmpty());
        assertTrue(s.isReal());
        assertEquals(0, s.getHeight());
        assertEquals(0, s.getWidth());
        assertEquals(0, s.getNbNonNullItems());
        assertEquals("[]", s.toString());
        assertEquals(ScilabTypeEnum.sci_sparse, s.getType());
        assertFalse(s.isReference());
        assertFalse(s.isSwaped());
    }

    @Test
    public void realScalarConstructorPopulatesCompressedArrays() {
        ScilabSparse s = new ScilabSparse(5.0);
        assertFalse(s.isEmpty());
        assertTrue(s.isReal());
        assertEquals(1, s.getHeight());
        assertEquals(1, s.getWidth());
        assertEquals(1, s.getNbNonNullItems());
        assertArrayEquals(new int[] {1}, s.getNbItemRow());
        assertArrayEquals(new int[] {0}, s.getColPos());
        // getScilabColPos is 1-based.
        assertArrayEquals(new int[] {1}, s.getScilabColPos());
        assertEquals(5.0, s.getRealElement(0), EPS);
        assertEquals(5.0, s.getRealElement(0, 0), EPS);
    }

    @Test
    public void zeroScalarConstructorStaysEmpty() {
        // A zero has no non-null item, so nothing is stored.
        ScilabSparse s = new ScilabSparse(0.0);
        assertTrue(s.isEmpty());
        assertEquals(0, s.getNbNonNullItems());
    }

    @Test
    public void complexScalarConstructor() {
        ScilabSparse s = new ScilabSparse(3.0, 4.0);
        assertFalse(s.isReal());
        assertEquals(3.0, s.getRealElement(0), EPS);
        assertEquals(4.0, s.getImaginaryElement(0), EPS);
        assertArrayEquals(new double[] {3.0, 4.0}, s.getElement(0), EPS);
        assertArrayEquals(new double[] {3.0, 4.0}, s.getElement(0, 0), EPS);
    }

    @Test
    public void realMatrixConstructorBuildsCompressedForm() {
        // {{1,0,2},{0,3,0}} -> nbItemRow [2,1], colPos [0,2,1], realPart [1,2,3].
        double[][] data = {{1.0, 0.0, 2.0}, {0.0, 3.0, 0.0}};
        ScilabSparse s = new ScilabSparse(data);
        assertEquals(2, s.getHeight());
        assertEquals(3, s.getWidth());
        assertEquals(3, s.getNbNonNullItems());
        assertArrayEquals(new int[] {2, 1}, s.getNbItemRow());
        assertArrayEquals(new int[] {0, 2, 1}, s.getColPos());
        assertArrayEquals(new double[] {1.0, 2.0, 3.0}, s.getRealPart(), EPS);
    }

    @Test
    public void getRealElementByRowColWalksTheCompressedRows() {
        double[][] data = {{1.0, 0.0, 2.0}, {0.0, 3.0, 0.0}};
        ScilabSparse s = new ScilabSparse(data);
        assertEquals(1.0, s.getRealElement(0, 0), EPS);
        assertEquals(2.0, s.getRealElement(0, 2), EPS);
        assertEquals(3.0, s.getRealElement(1, 1), EPS);
        // Structural zeros return 0.
        assertEquals(0.0, s.getRealElement(0, 1), EPS);
        assertEquals(0.0, s.getRealElement(1, 0), EPS);
        assertEquals(0.0, s.getRealElement(1, 2), EPS);
    }

    @Test
    public void getFullRealPartReconstructsDenseMatrix() {
        double[][] data = {{1.0, 0.0, 2.0}, {0.0, 3.0, 0.0}};
        ScilabSparse s = new ScilabSparse(data);
        double[][] full = s.getFullRealPart();
        assertArrayEquals(new double[] {1.0, 0.0, 2.0}, full[0], EPS);
        assertArrayEquals(new double[] {0.0, 3.0, 0.0}, full[1], EPS);
    }

    @Test
    public void complexMatrixConstructorAndFullMatrix() {
        double[][] re = {{1.0, 0.0}, {0.0, 2.0}};
        double[][] im = {{0.0, 0.0}, {0.0, 5.0}};
        ScilabSparse s = new ScilabSparse(re, im);
        assertFalse(s.isReal());
        // Two non-zero cells: (0,0) real-only, (1,1) real+imag.
        assertEquals(2, s.getNbNonNullItems());
        assertEquals(2.0, s.getRealElement(1, 1), EPS);
        assertEquals(5.0, s.getImaginaryElement(1, 1), EPS);
        double[][][] fm = s.getFullMatrix();
        assertEquals(1.0, fm[0][0][0], EPS);
        assertEquals(2.0, fm[0][1][1], EPS);
        assertEquals(5.0, fm[1][1][1], EPS);
        // getElement(i,j) on a complex sparse returns [real, imag].
        assertArrayEquals(new double[] {2.0, 5.0}, s.getElement(1, 1), EPS);
        // A structural zero of a complex sparse returns [0, 0].
        assertArrayEquals(new double[] {0.0, 0.0}, s.getElement(0, 1), EPS);
    }

    @Test
    public void setRealElementByRowColOnlyTouchesExistingNonZeros() {
        double[][] data = {{1.0, 0.0}, {0.0, 3.0}};
        ScilabSparse s = new ScilabSparse(data);
        s.setRealElement(0, 0, 9.0);
        assertEquals(9.0, s.getRealElement(0, 0), EPS);
        // (0,1) is a structural zero: the setter is a no-op there, still zero.
        s.setRealElement(0, 1, 7.0);
        assertEquals(0.0, s.getRealElement(0, 1), EPS);
    }

    @Test
    public void positionalSettersAndGetters() {
        double[][] re = {{1.0}};
        double[][] im = {{2.0}};
        ScilabSparse s = new ScilabSparse(re, im);
        s.setElement(0, 5.0, 6.0);
        assertEquals(5.0, s.getRealElement(0), EPS);
        assertEquals(6.0, s.getImaginaryElement(0), EPS);
        s.setColPosElement(0, 0);
        assertEquals(0, s.getColPosElement(0));
        s.setNbItemElement(0, 1);
        assertEquals(1, s.getNbItemElement(0));
    }

    @Test
    public void checkedConstructorAcceptsAValidRepresentation() throws ScilabSparseException {
        // 2x2 with one item on each row at cols 0 and 1.
        ScilabSparse s = new ScilabSparse(2, 2, 2, new int[] {1, 1}, new int[] {0, 1}, new double[] {1.0, 1.0}, true);
        assertEquals(2, s.getNbNonNullItems());
        assertEquals(1.0, s.getRealElement(0, 0), EPS);
        assertEquals(1.0, s.getRealElement(1, 1), EPS);
    }

    @Test
    public void checkedConstructorRejectsTooManyItems() {
        // nbItem = 5 but the matrix only has rows*cols = 4 cells.
        assertThrows(ScilabSparseException.class, () ->
                     new ScilabSparse(2, 2, 5, new int[] {1, 1}, new int[] {0, 1}, new double[] {1.0, 1.0}, true));
    }

    @Test
    public void checkedConstructorRejectsColPosLengthMismatch() {
        // colPos length (1) must equal nbItem (2).
        assertThrows(ScilabSparseException.class, () ->
                     new ScilabSparse(2, 2, 2, new int[] {1, 1}, new int[] {0}, new double[] {1.0, 1.0}, true));
    }

    @Test
    public void checkValidityRejectsNbItemRowSumMismatch() {
        // nbItemRow sums to 3 but nbItem says 2.
        assertThrows(ScilabSparseException.class, () ->
                     ScilabSparse.checkValidity(2, 2, 2, new int[] {2, 1}, new int[] {0, 1}));
    }

    @Test
    public void checkValidityRejectsOutOfRangeColumn() {
        // colPos value 3 is >= cols (2).
        assertThrows(ScilabSparseException.class, () ->
                     ScilabSparse.checkValidity(2, 2, 1, new int[] {1, 0}, new int[] {3}));
    }

    @Test
    public void compareNbItemRowTreatsTrailingZerosAsEqual() {
        assertTrue(ScilabSparse.compareNbItemRow(new int[] {1, 2, 3}, new int[] {1, 2, 3}));
        assertTrue(ScilabSparse.compareNbItemRow(new int[] {1, 2, 3}, new int[] {1, 2, 3, 0, 0}));
        assertTrue(ScilabSparse.compareNbItemRow(new int[] {1, 2, 3, 0, 0}, new int[] {1, 2, 3}));
        assertFalse(ScilabSparse.compareNbItemRow(new int[] {1, 2, 3}, new int[] {1, 2, 4}));
        assertFalse(ScilabSparse.compareNbItemRow(new int[] {1, 2, 3}, new int[] {1, 2, 3, 1}));
    }

    @Test
    public void equalsAndHashCodeForRealSparse() {
        ScilabSparse a = new ScilabSparse(new double[][] {{1.0, 0.0}, {0.0, 3.0}});
        ScilabSparse b = new ScilabSparse(new double[][] {{1.0, 0.0}, {0.0, 3.0}});
        ScilabSparse c = new ScilabSparse(new double[][] {{1.0, 0.0}, {0.0, 9.0}});
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
        assertNotEquals(a, "not a sparse");
    }

    @Test
    public void twoEmptySparsesAreEqual() {
        assertEquals(new ScilabSparse(), new ScilabSparse());
    }

    @Test
    public void realAndComplexWithSameRealPartAreNotEqual() {
        ScilabSparse real = new ScilabSparse(3.0);
        ScilabSparse complex = new ScilabSparse(3.0, 4.0);
        assertNotEquals(real, complex);
    }

    @Test
    public void differentDimensionsAreNotEqual() {
        ScilabSparse a = new ScilabSparse(new double[][] {{1.0, 0.0}});
        ScilabSparse b = new ScilabSparse(new double[][] {{1.0}, {0.0}});
        assertNotEquals(a, b);
    }

    @Test
    public void getSerializedObjectShapeReflectsRealness() {
        Object[] real = (Object[]) new ScilabSparse(3.0).getSerializedObject();
        assertEquals(4, real.length);
        Object[] complex = (Object[]) new ScilabSparse(3.0, 4.0).getSerializedObject();
        assertEquals(5, complex.length);
    }

    @Test
    public void namedConstructorCarriesVarName() {
        ScilabSparse s = new ScilabSparse("sp", 1, 1, 1, new int[] {1}, new int[] {0}, new double[] {7.0}, null);
        assertEquals("sp", s.getVarName());
        assertTrue(s.isReal());
    }

    @Test
    public void toStringRealScalar() {
        assertEquals("sparse([1, 1], [5.0], [1, 1])", new ScilabSparse(5.0).toString());
    }

    @Test
    public void toStringComplexScalar() {
        assertEquals("sparse([1, 1], [3.0+4.0*%i], [1, 1])", new ScilabSparse(3.0, 4.0).toString());
    }

    @Test
    public void serializationRoundTripReal() throws Exception {
        ScilabSparse original = new ScilabSparse("v", 2, 2, 2, new int[] {1, 1}, new int[] {0, 1}, new double[] {1.0, 3.0}, null);
        ScilabSparse restored = roundTrip(original);
        assertEquals(original, restored);
        assertEquals("v", restored.getVarName());
        assertEquals(2, restored.getNbNonNullItems());
    }

    @Test
    public void serializationRoundTripComplex() throws Exception {
        ScilabSparse original = new ScilabSparse(3.0, 4.0);
        ScilabSparse restored = roundTrip(original);
        assertEquals(original, restored);
        assertFalse(restored.isReal());
        assertEquals(4.0, restored.getImaginaryElement(0), EPS);
    }

    private static ScilabSparse roundTrip(ScilabSparse in) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(in);
        }
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()))) {
            return (ScilabSparse) ois.readObject();
        }
    }
}
