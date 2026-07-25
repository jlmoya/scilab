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

package org.scilab.modules.external_objects_java;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link ScilabJavaArray}, the module's pure array/collection
 * toolbox. Every method exercised here is a static, side-effect-free reflection helper
 * (boxing/unboxing, dimension reshaping, list &lt;-&gt; primitive-array conversion, and array
 * indexing). None of them touch native code or a running Scilab, so they are all
 * hermetic. Package-private overloads ({@code toOneDim(Object)}, {@code toBiDim},
 * {@code singleToOneDim}) are reachable because this test lives in the same package.
 */
public class ScilabJavaArrayTest {

    private static final double EPS = 0.0;

    /* ------------------------------------------------------- array type introspection */

    @Test
    public void getArrayBaseTypeDrillsThroughEveryDimension() {
        assertSame(double.class, ScilabJavaArray.getArrayBaseType(double[][][].class));
        assertSame(String.class, ScilabJavaArray.getArrayBaseType(String[].class));
    }

    @Test
    public void getArrayBaseTypeOfANonArrayIsTheTypeItself() {
        assertSame(int.class, ScilabJavaArray.getArrayBaseType(int.class));
        assertSame(String.class, ScilabJavaArray.getArrayBaseType(String.class));
    }

    @Test
    public void getArrayInfoReturnsBaseTypeAndDimensionCount() {
        Object[] info = ScilabJavaArray.getArrayInfo(double[][][].class);
        assertSame(double.class, info[0]);
        assertEquals(Integer.valueOf(3), info[1]);
    }

    @Test
    public void getArrayInfoOfANonArrayHasZeroDimensions() {
        Object[] info = ScilabJavaArray.getArrayInfo(String.class);
        assertSame(String.class, info[0]);
        assertEquals(Integer.valueOf(0), info[1]);
    }

    /* ------------------------------------------------------------------ toIntArray(double[]) */

    @Test
    public void toIntArrayTruncatesTowardZero() {
        // (int) cast truncates toward zero, it does not round.
        assertArrayEquals(new int[] {1, 2, -3, 0}, ScilabJavaArray.toIntArray(new double[] {1.9, 2.1, -3.9, 0.4}));
    }

    /* --------------------------------------------------------------- box / unbox 1-D arrays */

    @Test
    public void fromPrimitiveBoxesADoubleArray() {
        assertArrayEquals(new Double[] {1.0, 2.0, 3.0}, ScilabJavaArray.fromPrimitive(new double[] {1.0, 2.0, 3.0}));
    }

    @Test
    public void fromPrimitiveBoxesAnIntArray() {
        assertArrayEquals(new Integer[] {4, 5, 6}, ScilabJavaArray.fromPrimitive(new int[] {4, 5, 6}));
    }

    @Test
    public void fromPrimitiveBoxesABooleanArray() {
        assertArrayEquals(new Boolean[] {true, false, true}, ScilabJavaArray.fromPrimitive(new boolean[] {true, false, true}));
    }

    @Test
    public void toPrimitiveUnboxesAnIntegerArray() {
        assertArrayEquals(new int[] {7, 8, 9}, ScilabJavaArray.toPrimitive(new Integer[] {7, 8, 9}));
    }

    @Test
    public void toPrimitiveUnboxesADoubleArray() {
        assertArrayEquals(new double[] {1.5, 2.5}, ScilabJavaArray.toPrimitive(new Double[] {1.5, 2.5}), EPS);
    }

    @Test
    public void toPrimitiveUnboxesACharArray() {
        assertArrayEquals(new char[] {'a', 'z'}, ScilabJavaArray.toPrimitive(new Character[] {'a', 'z'}));
    }

    @Test
    public void boxAndUnboxRoundTripsAnIntArray() {
        int[] src = {10, -20, 30};
        assertArrayEquals(src, ScilabJavaArray.toPrimitive(ScilabJavaArray.fromPrimitive(src)));
    }

    /* ------------------------------------------------------ box / unbox N-D via Object dispatch */

    @Test
    public void toPrimitiveReshapesABoxedMatrixToPrimitive() {
        Object r = ScilabJavaArray.toPrimitive(new Double[][] {{1.0, 2.0}, {3.0, 4.0}});
        assertTrue(r instanceof double[][], "Double[][] must convert to double[][]");
        double[][] d = (double[][]) r;
        assertArrayEquals(new double[] {1.0, 2.0}, d[0], EPS);
        assertArrayEquals(new double[] {3.0, 4.0}, d[1], EPS);
    }

    @Test
    public void fromPrimitiveReshapesAPrimitiveMatrixToBoxed() {
        Object r = ScilabJavaArray.fromPrimitive(new double[][] {{5.0, 6.0}, {7.0, 8.0}});
        assertTrue(r instanceof Double[][], "double[][] must convert to Double[][]");
        Double[][] d = (Double[][]) r;
        assertArrayEquals(new Double[] {5.0, 6.0}, d[0]);
        assertArrayEquals(new Double[] {7.0, 8.0}, d[1]);
    }

    @Test
    public void toPrimitiveLeavesANonArrayUntouched() {
        // The public Object overload short-circuits when the argument is not an array.
        String s = "not-an-array";
        assertSame(s, ScilabJavaArray.toPrimitive((Object) s));
    }

    @Test
    public void fromPrimitiveLeavesAReferenceArrayUntouched() {
        // A String[] has no primitive mapping, so it is returned as-is.
        String[] a = {"x", "y"};
        assertSame(a, ScilabJavaArray.fromPrimitive((Object) a));
    }

    /* ------------------------------------------------------------------ flatten 2-D -> 1-D */

    @Test
    public void toOneDimFlattensADoubleMatrixRowMajor() {
        assertArrayEquals(new double[] {1, 2, 3, 4, 5, 6},
                          ScilabJavaArray.toOneDim(new double[][] {{1, 2, 3}, {4, 5, 6}}), EPS);
    }

    @Test
    public void toOneDimFlattensAnIntMatrixRowMajor() {
        assertArrayEquals(new int[] {1, 2, 3, 4}, ScilabJavaArray.toOneDim(new int[][] {{1, 2}, {3, 4}}));
    }

    @Test
    public void toOneDimOfAnEmptyMatrixIsAnEmptyArray() {
        assertEquals(0, ScilabJavaArray.toOneDim(new double[0][0]).length);
        assertEquals(0, ScilabJavaArray.toOneDim(new double[][] {{}}).length);
    }

    @Test
    public void toOneDimObjectDispatchHandlesPrimitiveMatrices() {
        Object r = ScilabJavaArray.toOneDim((Object) new double[][] {{1, 2}, {3, 4}});
        assertTrue(r instanceof double[]);
        assertArrayEquals(new double[] {1, 2, 3, 4}, (double[]) r, EPS);
    }

    @Test
    public void toOneDimObjectDispatchHandlesReferenceMatrices() {
        Object r = ScilabJavaArray.toOneDim((Object) new String[][] {{"a", "b"}, {"c", "d"}});
        assertTrue(r instanceof String[]);
        assertArrayEquals(new String[] {"a", "b", "c", "d"}, (String[]) r);
    }

    /* --------------------------------------------------------------------- reshape 1-D -> 2-D */

    @Test
    public void toBiDimWrapsAOneDimArrayInASingleRow() {
        Object r = ScilabJavaArray.toBiDim(new double[] {1, 2, 3});
        assertTrue(r instanceof double[][]);
        double[][] d = (double[][]) r;
        assertEquals(1, d.length);
        assertArrayEquals(new double[] {1, 2, 3}, d[0], EPS);
    }

    @Test
    public void singleToOneDimWrapsAScalarInAOneElementArray() {
        Object r = ScilabJavaArray.singleToOneDim(double.class, 42.0);
        assertTrue(r instanceof double[]);
        assertArrayEquals(new double[] {42.0}, (double[]) r, EPS);

        Object rs = ScilabJavaArray.singleToOneDim(String.class, "hi");
        assertTrue(rs instanceof String[]);
        assertArrayEquals(new String[] {"hi"}, (String[]) rs);
    }

    /* ---------------------------------------------------------- List -> primitive array */

    @Test
    public void toDoubleArrayUnpacksAList() {
        assertArrayEquals(new double[] {1.0, 2.0, 3.0},
                          ScilabJavaArray.toDoubleArray(Arrays.asList(1.0, 2.0, 3.0)), EPS);
    }

    @Test
    public void toIntArrayUnpacksAList() {
        assertArrayEquals(new int[] {1, 2, 3}, ScilabJavaArray.toIntArray(Arrays.asList(1, 2, 3)));
    }

    @Test
    public void toLongArrayUnpacksAList() {
        assertArrayEquals(new long[] {10L, 20L}, ScilabJavaArray.toLongArray(Arrays.asList(10L, 20L)));
    }

    @Test
    public void toBooleanArrayUnpacksAList() {
        assertArrayEquals(new boolean[] {true, false}, ScilabJavaArray.toBooleanArray(Arrays.asList(true, false)));
    }

    @Test
    public void toStringArrayUnpacksAList() {
        assertArrayEquals(new String[] {"a", "b"}, ScilabJavaArray.toStringArray(Arrays.asList("a", "b")));
    }

    @Test
    public void toCharArrayUnpacksAList() {
        assertArrayEquals(new char[] {'x', 'y'}, ScilabJavaArray.toCharArray(Arrays.asList('x', 'y')));
    }

    @Test
    public void listToArrayHelpersReturnEmptyArraysForEmptyLists() {
        assertEquals(0, ScilabJavaArray.toDoubleArray(Arrays.<Double>asList()).length);
        assertEquals(0, ScilabJavaArray.toStringArray(Arrays.<String>asList()).length);
    }

    /* -------------------------------------------------------------------- toList (non-array) */

    @Test
    public void toListWrapsANonArrayObjectInASingletonList() {
        List<?> l = ScilabJavaArray.toList("hello");
        assertEquals(1, l.size());
        assertSame("hello", l.get(0));
    }

    /* --------------------------------------------------------------------------- get / set */

    @Test
    public void getReadsAnElementFromANestedArray() throws ScilabJavaException {
        int[][] m = {{10, 11}, {20, 21}};
        assertEquals(21, ScilabJavaArray.get(m, new int[] {1, 1}));
    }

    @Test
    public void getFromAListHonorsZeroBasedIndex() throws ScilabJavaException {
        assertEquals("b", ScilabJavaArray.get(Arrays.asList("a", "b", "c"), new int[] {1}));
    }

    @Test
    public void getOutOfBoundsThrowsScilabJavaException() {
        int[] a = {1, 2, 3};
        assertThrows(ScilabJavaException.class, () -> ScilabJavaArray.get(a, new int[] {5}));
    }

    @Test
    public void getWithATrailingNonArrayStepThrows() {
        // Indexing one level too deep into a flat array must be reported, not NPE.
        int[] a = {1, 2, 3};
        assertThrows(ScilabJavaException.class, () -> ScilabJavaArray.get(a, new int[] {0, 0}));
    }

    @Test
    public void setWritesThroughFunctionArgumentsConversion() throws ScilabJavaException {
        // Storing a boxed Integer into an int[] exercises the convert() path in set().
        int[] a = {0, 0, 0};
        ScilabJavaArray.set(a, new int[] {1}, Integer.valueOf(99));
        assertArrayEquals(new int[] {0, 99, 0}, a);
    }

    @Test
    public void setOutOfBoundsThrowsScilabJavaException() {
        int[] a = {1, 2, 3};
        assertThrows(ScilabJavaException.class, () -> ScilabJavaArray.set(a, new int[] {9}, 1));
    }

    /* ==================================================== extended coverage: remaining overloads */

    @Test
    public void toPrimitiveUnboxesEveryWrapperArrayType() {
        assertArrayEquals(new float[] {1.5f, 2.5f}, ScilabJavaArray.toPrimitive(new Float[] {1.5f, 2.5f}), 0.0f);
        assertArrayEquals(new byte[] {1, 2}, ScilabJavaArray.toPrimitive(new Byte[] {1, 2}));
        assertArrayEquals(new short[] {3, 4}, ScilabJavaArray.toPrimitive(new Short[] {3, 4}));
        assertArrayEquals(new long[] {5L, 6L}, ScilabJavaArray.toPrimitive(new Long[] {5L, 6L}));
        assertArrayEquals(new boolean[] {true, false}, ScilabJavaArray.toPrimitive(new Boolean[] {true, false}));
    }

    @Test
    public void fromPrimitiveBoxesEveryPrimitiveArrayType() {
        assertArrayEquals(new Float[] {1.5f, 2.5f}, ScilabJavaArray.fromPrimitive(new float[] {1.5f, 2.5f}));
        assertArrayEquals(new Character[] {'a', 'b'}, ScilabJavaArray.fromPrimitive(new char[] {'a', 'b'}));
        assertArrayEquals(new Byte[] {1, 2}, ScilabJavaArray.fromPrimitive(new byte[] {1, 2}));
        assertArrayEquals(new Short[] {3, 4}, ScilabJavaArray.fromPrimitive(new short[] {3, 4}));
        assertArrayEquals(new Long[] {5L, 6L}, ScilabJavaArray.fromPrimitive(new long[] {5L, 6L}));
    }

    /* --------------------------------------------------------------- 3-D box/unbox recursion */

    @Test
    public void toPrimitiveReshapesAThreeDimensionalBoxedArray() {
        Double[][][] boxed = {{{1.0, 2.0}}, {{3.0, 4.0}}};
        Object r = ScilabJavaArray.toPrimitive(boxed);
        assertTrue(r instanceof double[][][], "Double[][][] converts to double[][][]");
        double[][][] d = (double[][][]) r;
        assertArrayEquals(new double[] {1.0, 2.0}, d[0][0], EPS);
        assertArrayEquals(new double[] {3.0, 4.0}, d[1][0], EPS);
    }

    @Test
    public void fromPrimitiveReshapesAThreeDimensionalPrimitiveArray() {
        double[][][] prim = {{{1.0, 2.0}}, {{3.0, 4.0}}};
        Object r = ScilabJavaArray.fromPrimitive(prim);
        assertTrue(r instanceof Double[][][], "double[][][] converts to Double[][][]");
        Double[][][] d = (Double[][][]) r;
        assertArrayEquals(new Double[] {1.0, 2.0}, d[0][0]);
        assertArrayEquals(new Double[] {3.0, 4.0}, d[1][0]);
    }

    /* --------------------------------------------------------- flatten every primitive matrix */

    @Test
    public void toOneDimFlattensEveryPrimitiveMatrixType() {
        assertArrayEquals(new byte[] {1, 2, 3, 4}, ScilabJavaArray.toOneDim(new byte[][] {{1, 2}, {3, 4}}));
        assertArrayEquals(new short[] {1, 2, 3, 4}, ScilabJavaArray.toOneDim(new short[][] {{1, 2}, {3, 4}}));
        assertArrayEquals(new long[] {1L, 2L, 3L, 4L}, ScilabJavaArray.toOneDim(new long[][] {{1L, 2L}, {3L, 4L}}));
        assertArrayEquals(new float[] {1, 2, 3, 4}, ScilabJavaArray.toOneDim(new float[][] {{1, 2}, {3, 4}}), 0.0f);
        assertArrayEquals(new char[] {'a', 'b', 'c', 'd'}, ScilabJavaArray.toOneDim(new char[][] {{'a', 'b'}, {'c', 'd'}}));
    }

    @Test
    public void toOneDimOfEmptyPrimitiveMatricesAreEmpty() {
        assertEquals(0, ScilabJavaArray.toOneDim(new byte[0][0]).length);
        assertEquals(0, ScilabJavaArray.toOneDim(new float[][] {{}}).length);
        assertEquals(0, ScilabJavaArray.toOneDim(new long[][] {{}}).length);
    }

    /* -------------------------------------------------------- remaining List -> primitive array */

    @Test
    public void listToArrayHelpersCoverFloatByteShort() {
        assertArrayEquals(new float[] {1.5f, 2.5f}, ScilabJavaArray.toFloatArray(Arrays.asList(1.5f, 2.5f)), 0.0f);
        assertArrayEquals(new byte[] {1, 2}, ScilabJavaArray.toByteArray(Arrays.asList((byte) 1, (byte) 2)));
        assertArrayEquals(new short[] {3, 4}, ScilabJavaArray.toShortArray(Arrays.asList((short) 3, (short) 4)));
    }

    /* ----------------------------------------------------------------------------- newInstance */

    @Test
    public void newInstanceOfAPrimitiveBaseTypeAllocatesAPrimitiveArray() throws ScilabJavaException {
        int id = ScilabJavaArray.newInstance("double", new int[] {2, 3});
        Object arr = ScilabJavaObject.arraySJO[id].object;
        assertTrue(arr instanceof double[][], "'double' base with 2 dims -> double[][]");
        assertEquals(2, ((double[][]) arr).length);
        assertEquals(3, ((double[][]) arr)[0].length);
    }

    @Test
    public void newInstanceOfAnIntBaseTypeAllocatesAnIntArray() throws ScilabJavaException {
        int id = ScilabJavaArray.newInstance("int", new int[] {5});
        Object arr = ScilabJavaObject.arraySJO[id].object;
        assertTrue(arr instanceof int[]);
        assertEquals(5, ((int[]) arr).length);
    }

    @Test
    public void newInstanceOfAReferenceClassAllocatesAReferenceArray() throws ScilabJavaException {
        int id = ScilabJavaArray.newInstance("java.lang.String", new int[] {3});
        Object arr = ScilabJavaObject.arraySJO[id].object;
        assertTrue(arr instanceof String[]);
        assertEquals(3, ((String[]) arr).length);
    }

    @Test
    public void newInstanceOfAnUnknownClassThrows() {
        assertThrows(ScilabJavaException.class,
                     () -> ScilabJavaArray.newInstance("no.such.Class12345", new int[] {2}));
    }
}
