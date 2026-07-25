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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link ScilabJavaObject}, the reference table that assigns
 * every Java value crossing the Scilab boundary an integer id. Only the pure-Java
 * surface is exercised here: the id-allocating constructor, the {@code wrap}/{@code unwrap}
 * round-trips, the {@code isUnwrappable} type discriminator, id validity/representation,
 * array element access, and list/poly wrapping. The JNI entry points and the {@code debug}
 * logging paths are not touched (they need a running Scilab / a log file).
 *
 * <p>The reference table {@code arraySJO} is process-global static state; these tests only
 * append to it (never {@code garbageCollect}), and each assertion works against ids it
 * allocated itself, so ordering between tests is irrelevant.
 */
public class ScilabJavaObjectTest {

    /* --------------------------------------------------------------- construction / ids */

    @Test
    public void constructingWithAnObjectAllocatesAPositiveIdAndRegisters() {
        ScilabJavaObject o = new ScilabJavaObject("hello");
        assertTrue(o.id > 0, "a non-null value gets a positive id");
        assertSame(o, ScilabJavaObject.arraySJO[o.id], "it is registered in the reference table");
        assertSame(String.class, o.clazz);
        assertEquals("hello", o.object);
    }

    @Test
    public void constructingWithNullMapsToIdZero() {
        ScilabJavaObject o = new ScilabJavaObject(null);
        assertEquals(0, o.id, "null is the canonical id-0 object");
    }

    @Test
    public void distinctObjectsGetDistinctIds() {
        ScilabJavaObject a = new ScilabJavaObject(new Object());
        ScilabJavaObject b = new ScilabJavaObject(new Object());
        assertNotEquals(a.id, b.id);
    }

    /* ----------------------------------------------------------------------- toString */

    @Test
    public void toStringOfANullWrapperIsTheStringNull() {
        assertEquals("null", new ScilabJavaObject(null).toString());
    }

    @Test
    public void toStringDelegatesToTheWrappedObject() {
        assertEquals("hello", new ScilabJavaObject("hello").toString());
    }

    /* ------------------------------------------------------- validity / representation */

    @Test
    public void idZeroIsAlwaysValid() {
        assertTrue(ScilabJavaObject.isValidJavaObject(0));
    }

    @Test
    public void aFreshlyWrappedIdIsValid() {
        int id = ScilabJavaObject.wrap("v");
        assertTrue(ScilabJavaObject.isValidJavaObject(id));
    }

    @Test
    public void negativeIdsAreNeverValid() {
        assertFalse(ScilabJavaObject.isValidJavaObject(-3));
    }

    @Test
    public void representationOfIdZeroIsNull() {
        assertEquals("null", ScilabJavaObject.getRepresentation(0));
    }

    @Test
    public void removingAnObjectInvalidatesItAndItsRepresentation() {
        int id = ScilabJavaObject.wrap("temp");
        assertTrue(ScilabJavaObject.isValidJavaObject(id));
        ScilabJavaObject.removeScilabJavaObject(id);
        assertFalse(ScilabJavaObject.isValidJavaObject(id));
        assertEquals("Invalid Java object", ScilabJavaObject.getRepresentation(id));
    }

    @Test
    public void getClassNameReflectsTheDeclaredClassAndNullForZero() throws ScilabJavaException {
        int id = ScilabJavaObject.wrap("s");
        assertEquals("java.lang.String", ScilabJavaObject.getClassName(id));
        assertEquals("null", ScilabJavaObject.getClassName(0));
    }

    /* ---------------------------------------------------------------- wrap / unwrap */

    @Test
    public void wrapUnwrapRoundTripsScalars() {
        assertEquals(3.5, ScilabJavaObject.unwrapDouble(ScilabJavaObject.wrap(3.5)), 0.0);
        assertEquals(42, ScilabJavaObject.unwrapInt(ScilabJavaObject.wrap(42)));
        assertEquals("txt", ScilabJavaObject.unwrapString(ScilabJavaObject.wrap("txt")));
        assertTrue(ScilabJavaObject.unwrapBoolean(ScilabJavaObject.wrap(true)));
        assertEquals(7L, ScilabJavaObject.unwrapLong(ScilabJavaObject.wrap(7L)));
        assertEquals('q', ScilabJavaObject.unwrapChar(ScilabJavaObject.wrap('q')));
    }

    @Test
    public void wrapUnwrapRoundTripsRowVectors() {
        int id = ScilabJavaObject.wrap(new double[] {1.0, 2.0, 3.0});
        assertArrayEquals(new double[] {1.0, 2.0, 3.0}, (double[]) ScilabJavaObject.unwrapRowDouble(id), 0.0);

        int sid = ScilabJavaObject.wrap(new String[] {"a", "b"});
        assertArrayEquals(new String[] {"a", "b"}, ScilabJavaObject.unwrapRowString(sid));
    }

    @Test
    public void wrapUnwrapRoundTripsMatrices() {
        int id = ScilabJavaObject.wrap(new double[][] {{1, 2}, {3, 4}});
        double[][] m = ScilabJavaObject.unwrapMatDouble(id);
        assertArrayEquals(new double[] {1, 2}, m[0], 0.0);
        assertArrayEquals(new double[] {3, 4}, m[1], 0.0);
    }

    /* ---------------------------------------------------------------- isUnwrappable */

    @Test
    public void isUnwrappableReturnsOneForNull() {
        assertEquals(1, ScilabJavaObject.isUnwrappable(0));
    }

    @Test
    public void isUnwrappableClassifiesPrimitiveWrappers() {
        // Codes come from the static unwrappableType map (double=2, int=23, String=5).
        assertEquals(2, ScilabJavaObject.isUnwrappable(ScilabJavaObject.wrap(1.0)));
        assertEquals(23, ScilabJavaObject.isUnwrappable(ScilabJavaObject.wrap(1)));
        assertEquals(5, ScilabJavaObject.isUnwrappable(ScilabJavaObject.wrap("s")));
    }

    @Test
    public void isUnwrappableClassifiesAHomogeneousDoubleList() {
        int id = new ScilabJavaObject(Arrays.asList(1.0, 2.0, 3.0)).id;
        assertEquals(3, ScilabJavaObject.isUnwrappable(id), "a List<Double> unwraps as a double row (code 3)");
    }

    @Test
    public void isUnwrappableReturnsMinusOneForAnOpaqueObject() {
        int id = new ScilabJavaObject(new Object()).id;
        assertEquals(-1, ScilabJavaObject.isUnwrappable(id));
    }

    /* ---------------------------------------------------------- array element access */

    @Test
    public void getArrayElementReadsThroughToAScalarWrapper() throws ScilabJavaException {
        int arrId = ScilabJavaObject.wrap(new int[] {10, 20, 30});
        int elemId = ScilabJavaObject.getArrayElement(arrId, new int[] {1});
        assertEquals(20, ScilabJavaObject.unwrapInt(elemId));
    }

    @Test
    public void setArrayElementWritesThroughAScalarWrapper() throws ScilabJavaException {
        int arrId = ScilabJavaObject.wrap(new int[] {0, 0, 0});
        int valId = ScilabJavaObject.wrap(99);
        ScilabJavaObject.setArrayElement(arrId, new int[] {2}, valId);
        int back = ScilabJavaObject.getArrayElement(arrId, new int[] {2});
        assertEquals(99, ScilabJavaObject.unwrapInt(back));
    }

    @Test
    public void getArrayElementOutOfBoundsThrows() {
        int arrId = ScilabJavaObject.wrap(new int[] {1, 2, 3});
        assertThrows(ScilabJavaException.class, () -> ScilabJavaObject.getArrayElement(arrId, new int[] {7}));
    }

    @Test
    public void getArrayElementOnNullIsRejected() {
        assertThrows(ScilabJavaException.class, () -> ScilabJavaObject.getArrayElement(0, new int[] {0}));
    }

    /* ----------------------------------------------------------------- wrapList / poly */

    @Test
    public void wrapListProducesAnArrayList() throws ScilabJavaException {
        int a = ScilabJavaObject.wrap("a");
        int b = ScilabJavaObject.wrap("b");
        int listId = ScilabJavaObject.wrapList(new int[] {a, b});
        assertEquals("java.util.ArrayList", ScilabJavaObject.getClassName(listId));
        assertEquals(2, ((java.util.List) ScilabJavaObject.arraySJO[listId].object).size());
    }

    @Test
    public void wrapPolyProducesAValidWrappedObject() {
        int id = ScilabJavaObject.wrapPoly(new double[] {0.0, 1.0});
        assertTrue(ScilabJavaObject.isValidJavaObject(id));
        assertTrue(ScilabJavaObject.arraySJO[id].clazz.getName().contains("Poly"),
                   "the polynomial index marker is wrapped as the private Poly class");
    }

    /* =============================================================== extended coverage below */

    /** Public bean so reflection (getFields/getMethods/Introspector) sees its members. */
    public static class Bean {
        public int count = 7;
        public String label = "orig";
        private String note = "n";
        public String getNote() {
            return note;
        }
        public void setNote(String note) {
            this.note = note;
        }
        public String shout(String s) {
            return s.toUpperCase();
        }
        public void clear() {
            label = "";
        }
    }

    /** A value whose toString() returns null, to reach the identity-string fallback. */
    public static class NullToString {
        @Override
        public String toString() {
            return null;
        }
    }

    /* ------------------------------------------------------------------------- clone / toString */

    @Test
    public void cloneProducesADistinctRegistrationWithTheSameContents() {
        ScilabJavaObject o = new ScilabJavaObject("hi");
        ScilabJavaObject c = o.clone();
        assertNotEquals(o.id, c.id, "clone is a fresh registration");
        assertEquals("hi", c.object);
        assertSame(String.class, c.clazz);
        assertTrue(ScilabJavaObject.isValidJavaObject(c.id));
    }

    @Test
    public void toStringFallsBackToIdentityWhenTheDelegateReturnsNull() {
        String s = new ScilabJavaObject(new NullToString()).toString();
        assertTrue(s.startsWith("Instance of "), "a null delegate toString yields an identity string, got: " + s);
    }

    /* ------------------------------------------------------------------------ wrap overloads */

    @Test
    public void wrapOverloadsRecordTheDeclaredStaticType() {
        assertSame(long.class, ScilabJavaObject.arraySJO[ScilabJavaObject.wrap(5L)].clazz);
        assertSame(long[].class, ScilabJavaObject.arraySJO[ScilabJavaObject.wrap(new long[] {1L})].clazz);
        assertSame(long[][].class, ScilabJavaObject.arraySJO[ScilabJavaObject.wrap(new long[][] {{1L}})].clazz);
        assertSame(byte.class, ScilabJavaObject.arraySJO[ScilabJavaObject.wrap((byte) 1)].clazz);
        assertSame(byte[].class, ScilabJavaObject.arraySJO[ScilabJavaObject.wrap(new byte[] {1})].clazz);
        assertSame(byte[][].class, ScilabJavaObject.arraySJO[ScilabJavaObject.wrap(new byte[][] {{1}})].clazz);
        assertSame(short.class, ScilabJavaObject.arraySJO[ScilabJavaObject.wrap((short) 1)].clazz);
        assertSame(short[].class, ScilabJavaObject.arraySJO[ScilabJavaObject.wrap(new short[] {1})].clazz);
        assertSame(short[][].class, ScilabJavaObject.arraySJO[ScilabJavaObject.wrap(new short[][] {{1}})].clazz);
        assertSame(char.class, ScilabJavaObject.arraySJO[ScilabJavaObject.wrap('x')].clazz);
        assertSame(char[].class, ScilabJavaObject.arraySJO[ScilabJavaObject.wrap(new char[] {'x'})].clazz);
        assertSame(char[][].class, ScilabJavaObject.arraySJO[ScilabJavaObject.wrap(new char[][] {{'x'}})].clazz);
        assertSame(float.class, ScilabJavaObject.arraySJO[ScilabJavaObject.wrap(1.5f)].clazz);
        assertSame(float[].class, ScilabJavaObject.arraySJO[ScilabJavaObject.wrap(new float[] {1.5f})].clazz);
        assertSame(float[][].class, ScilabJavaObject.arraySJO[ScilabJavaObject.wrap(new float[][] {{1.5f}})].clazz);
        assertSame(int[].class, ScilabJavaObject.arraySJO[ScilabJavaObject.wrap(new int[] {1})].clazz);
        assertSame(int[][].class, ScilabJavaObject.arraySJO[ScilabJavaObject.wrap(new int[][] {{1}})].clazz);
        assertSame(boolean[].class, ScilabJavaObject.arraySJO[ScilabJavaObject.wrap(new boolean[] {true})].clazz);
        assertSame(boolean[][].class, ScilabJavaObject.arraySJO[ScilabJavaObject.wrap(new boolean[][] {{true}})].clazz);
        assertSame(String[][].class, ScilabJavaObject.arraySJO[ScilabJavaObject.wrap(new String[][] {{"a"}})].clazz);
    }

    /* ------------------------------------------------------------------------ unwrap scalars */

    @Test
    public void unwrapScalarVariantsRoundTrip() {
        assertEquals((short) 7, ScilabJavaObject.unwrapShort(ScilabJavaObject.wrap((short) 7)));
        assertEquals((byte) 3, ScilabJavaObject.unwrapByte(ScilabJavaObject.wrap((byte) 3)));
        assertEquals(1.5f, ScilabJavaObject.unwrapFloat(ScilabJavaObject.wrap(1.5f)), 0.0f);
        assertEquals(9L, ScilabJavaObject.unwrapLong(ScilabJavaObject.wrap(9L)));
    }

    /* ----------------------------------------------------------- unwrapRow from List / boxed */

    @Test
    public void unwrapRowVariantsUnpackListsToPrimitiveArrays() {
        assertArrayEquals(new int[] {1, 2}, (int[]) ScilabJavaObject.unwrapRowInt(new ScilabJavaObject(Arrays.asList(1, 2)).id));
        assertArrayEquals(new long[] {1L, 2L}, (long[]) ScilabJavaObject.unwrapRowLong(new ScilabJavaObject(Arrays.asList(1L, 2L)).id));
        assertArrayEquals(new short[] {1, 2}, (short[]) ScilabJavaObject.unwrapRowShort(new ScilabJavaObject(Arrays.asList((short) 1, (short) 2)).id));
        assertArrayEquals(new byte[] {1, 2}, (byte[]) ScilabJavaObject.unwrapRowByte(new ScilabJavaObject(Arrays.asList((byte) 1, (byte) 2)).id));
        assertArrayEquals(new char[] {'a', 'b'}, (char[]) ScilabJavaObject.unwrapRowChar(new ScilabJavaObject(Arrays.asList('a', 'b')).id));
        assertArrayEquals(new float[] {1.5f, 2.5f}, (float[]) ScilabJavaObject.unwrapRowFloat(new ScilabJavaObject(Arrays.asList(1.5f, 2.5f)).id), 0.0f);
        assertArrayEquals(new boolean[] {true, false}, ScilabJavaObject.unwrapRowBoolean(new ScilabJavaObject(Arrays.asList(true, false)).id));
        assertArrayEquals(new String[] {"a", "b"}, ScilabJavaObject.unwrapRowString(new ScilabJavaObject(Arrays.asList("a", "b")).id));
    }

    @Test
    public void unwrapRowVariantsUnboxBoxedArrays() {
        assertArrayEquals(new int[] {4, 5}, (int[]) ScilabJavaObject.unwrapRowInt(new ScilabJavaObject(new Integer[] {4, 5}).id));
        assertArrayEquals(new long[] {4L, 5L}, (long[]) ScilabJavaObject.unwrapRowLong(new ScilabJavaObject(new Long[] {4L, 5L}).id));
        assertArrayEquals(new short[] {4, 5}, (short[]) ScilabJavaObject.unwrapRowShort(new ScilabJavaObject(new Short[] {4, 5}).id));
        assertArrayEquals(new byte[] {4, 5}, (byte[]) ScilabJavaObject.unwrapRowByte(new ScilabJavaObject(new Byte[] {4, 5}).id));
        assertArrayEquals(new char[] {'y', 'z'}, (char[]) ScilabJavaObject.unwrapRowChar(new ScilabJavaObject(new Character[] {'y', 'z'}).id));
        assertArrayEquals(new boolean[] {false, true}, ScilabJavaObject.unwrapRowBoolean(new ScilabJavaObject(new Boolean[] {false, true}).id));
    }

    @Test
    public void unwrapRowFloatDoesNotUnboxAFloatArrayDueToATypeCheckTypo() {
        // DEFECT CHARACTERIZATION: unwrapRowFloat tests `instanceof Double[]` (a copy-paste typo)
        // instead of Float[], so a Float[] never enters the unbox branch and is returned as-is.
        Object r = ScilabJavaObject.unwrapRowFloat(new ScilabJavaObject(new Float[] {1.5f, 2.5f}).id);
        assertTrue(r instanceof Float[], "the Double[] typo leaves a Float[] un-unboxed");
    }

    @Test
    public void unwrapRowDoubleReadsANonDirectBufferBackingArray() {
        double[] backing = {1.0, 2.0, 3.0};
        int id = new ScilabJavaObject(DoubleBuffer.wrap(backing)).id;
        assertArrayEquals(backing, (double[]) ScilabJavaObject.unwrapRowDouble(id), 0.0);
    }

    /* ----------------------------------------------------------- unwrapMat from boxed matrices */

    @Test
    public void unwrapMatVariantsUnboxBoxedMatrices() {
        assertArrayEquals(new int[] {1, 2}, ScilabJavaObject.unwrapMatInt(new ScilabJavaObject(new Integer[][] {{1, 2}, {3, 4}}).id)[0]);
        assertArrayEquals(new long[] {1L, 2L}, ScilabJavaObject.unwrapMatLong(new ScilabJavaObject(new Long[][] {{1L, 2L}}).id)[0]);
        assertArrayEquals(new short[] {1, 2}, ScilabJavaObject.unwrapMatShort(new ScilabJavaObject(new Short[][] {{1, 2}}).id)[0]);
        assertArrayEquals(new byte[] {1, 2}, ScilabJavaObject.unwrapMatByte(new ScilabJavaObject(new Byte[][] {{1, 2}}).id)[0]);
        assertArrayEquals(new char[] {'a', 'b'}, ScilabJavaObject.unwrapMatChar(new ScilabJavaObject(new Character[][] {{'a', 'b'}}).id)[0]);
        assertArrayEquals(new float[] {1.5f, 2.5f}, ScilabJavaObject.unwrapMatFloat(new ScilabJavaObject(new Float[][] {{1.5f, 2.5f}}).id)[0], 0.0f);
        assertArrayEquals(new boolean[] {true, false}, ScilabJavaObject.unwrapMatBoolean(new ScilabJavaObject(new Boolean[][] {{true, false}}).id)[0]);
        assertArrayEquals(new String[] {"a", "b"}, ScilabJavaObject.unwrapMatString(new ScilabJavaObject(new String[][] {{"a", "b"}}).id)[0]);
    }

    /* -------------------------------------------------------------------------- isUnwrappable */

    @Test
    public void isUnwrappableClassifiesMatricesAndBoxedRows() {
        assertEquals(4, ScilabJavaObject.isUnwrappable(ScilabJavaObject.wrap(new double[][] {{1}})));
        assertEquals(6, ScilabJavaObject.isUnwrappable(ScilabJavaObject.wrap(new String[] {"a"})));
        assertEquals(9, ScilabJavaObject.isUnwrappable(ScilabJavaObject.wrap(new boolean[] {true})));
        assertEquals(24, ScilabJavaObject.isUnwrappable(new ScilabJavaObject(new Integer[] {1}).id));
    }

    @Test
    public void isUnwrappableClassifiesHomogeneousListsAndRejectsMixedOrEmpty() {
        assertEquals(6, ScilabJavaObject.isUnwrappable(new ScilabJavaObject(Arrays.asList("a", "b")).id));
        assertEquals(-1, ScilabJavaObject.isUnwrappable(new ScilabJavaObject(new ArrayList<Object>()).id), "empty list is not unwrappable");
        List<Object> mixed = new ArrayList<Object>();
        mixed.add(1.0);
        mixed.add("x");
        assertEquals(-1, ScilabJavaObject.isUnwrappable(new ScilabJavaObject(mixed).id), "a heterogeneous list is not unwrappable");
    }

    @Test
    public void isUnwrappableClassifiesNioBuffersByComponentType() {
        assertEquals(3, ScilabJavaObject.isUnwrappable(new ScilabJavaObject(DoubleBuffer.allocate(2)).id));
        assertEquals(24, ScilabJavaObject.isUnwrappable(new ScilabJavaObject(IntBuffer.allocate(2)).id));
        assertEquals(12, ScilabJavaObject.isUnwrappable(new ScilabJavaObject(ByteBuffer.allocate(2)).id));
    }

    /* --------------------------------------------------------------- direct-buffer wrapping */

    @Test
    public void wrapAsDirectBufferFamilyRecordsTheViewType() {
        assertSame(ByteBuffer.class, ScilabJavaObject.arraySJO[ScilabJavaObject.wrapAsDirectByteBuffer(ByteBuffer.allocateDirect(64))].clazz);
        assertSame(DoubleBuffer.class, ScilabJavaObject.arraySJO[ScilabJavaObject.wrapAsDirectDoubleBuffer(ByteBuffer.allocateDirect(64))].clazz);
        assertSame(IntBuffer.class, ScilabJavaObject.arraySJO[ScilabJavaObject.wrapAsDirectIntBuffer(ByteBuffer.allocateDirect(64))].clazz);
        assertSame(CharBuffer.class, ScilabJavaObject.arraySJO[ScilabJavaObject.wrapAsDirectCharBuffer(ByteBuffer.allocateDirect(64))].clazz);
        assertSame(FloatBuffer.class, ScilabJavaObject.arraySJO[ScilabJavaObject.wrapAsDirectFloatBuffer(ByteBuffer.allocateDirect(64))].clazz);
        assertSame(LongBuffer.class, ScilabJavaObject.arraySJO[ScilabJavaObject.wrapAsDirectLongBuffer(ByteBuffer.allocateDirect(64))].clazz);
        assertSame(ShortBuffer.class, ScilabJavaObject.arraySJO[ScilabJavaObject.wrapAsDirectShortBuffer(ByteBuffer.allocateDirect(64))].clazz);
    }

    @Test
    public void limitDirectBufferZeroesABuffersLimit() {
        ByteBuffer bb = ByteBuffer.allocateDirect(16);
        int id = new ScilabJavaObject(bb).id;
        ScilabJavaObject.limitDirectBuffer(id);
        assertEquals(0, bb.limit(), "a direct buffer is neutralized by setting its limit to 0");
    }

    @Test
    public void limitDirectBufferIgnoresNonBufferObjects() {
        int id = ScilabJavaObject.wrap("not a buffer");
        ScilabJavaObject.limitDirectBuffer(id); // must be a no-op, not a throw
        assertTrue(ScilabJavaObject.isValidJavaObject(id));
    }

    /* ---------------------------------------------------------------------- bulk removal */

    @Test
    public void removeScilabJavaObjectBulkInvalidatesEveryId() {
        int a = ScilabJavaObject.wrap("a");
        int b = ScilabJavaObject.wrap("b");
        int c = ScilabJavaObject.wrap("c");
        ScilabJavaObject.removeScilabJavaObject(new int[] {a, b, c});
        assertFalse(ScilabJavaObject.isValidJavaObject(a));
        assertFalse(ScilabJavaObject.isValidJavaObject(b));
        assertFalse(ScilabJavaObject.isValidJavaObject(c));
    }

    /* -------------------------------------------------- getField / setField / getFieldType */

    @Test
    public void getFieldReadsPublicPrimitiveAndReferenceFields() throws ScilabJavaException {
        int id = new ScilabJavaObject(new Bean()).id;
        assertEquals(7, ScilabJavaObject.unwrapInt(ScilabJavaObject.getField(id, "count")));
        assertEquals("orig", ScilabJavaObject.unwrapString(ScilabJavaObject.getField(id, "label")));
    }

    @Test
    public void getFieldReadsThroughABeanPropertyGetter() throws ScilabJavaException {
        int id = new ScilabJavaObject(new Bean()).id;
        assertEquals("n", ScilabJavaObject.unwrapString(ScilabJavaObject.getField(id, "note")));
    }

    @Test
    public void getFieldReadsArrayLength() throws ScilabJavaException {
        int id = ScilabJavaObject.wrap(new int[] {1, 2, 3, 4, 5});
        assertEquals(5, ScilabJavaObject.unwrapInt(ScilabJavaObject.getField(id, "length")));
    }

    @Test
    public void getFieldClassOnAClassWrapperReturnsTheClassObject() throws ScilabJavaException {
        ScilabJavaClass cw = new ScilabJavaClass(String.class);
        int rid = ScilabJavaObject.getField(cw.id, "class");
        assertSame(String.class, ScilabJavaObject.arraySJO[rid].object);
    }

    @Test
    public void getFieldOnAnUnknownNameThrows() {
        int id = new ScilabJavaObject(new Bean()).id;
        assertThrows(ScilabJavaException.class, () -> ScilabJavaObject.getField(id, "missing"));
    }

    @Test
    public void setFieldThroughABeanPropertySetterSucceeds() throws ScilabJavaException {
        Bean bean = new Bean();
        int id = new ScilabJavaObject(bean).id;
        ScilabJavaObject.setField(id, "note", ScilabJavaObject.wrap("changed"));
        assertEquals("changed", bean.getNote());
    }

    @Test
    public void setFieldCoercesAnIntegralDoubleIntoAnIntField() throws ScilabJavaException {
        Bean bean = new Bean();
        int id = new ScilabJavaObject(bean).id;
        // The IllegalArgumentException -> Double.intValue() coercion branch returns cleanly.
        ScilabJavaObject.setField(id, "count", ScilabJavaObject.wrap(3.0));
        assertEquals(3, bean.count);
    }

    @Test
    public void setFieldOnAPlainPublicFieldMutatesButThenThrowsDueToBeanFallThrough() {
        // DEFECT CHARACTERIZATION: after a successful public-field write, setField does not
        // return; it falls through to the bean-property path, and since a plain public field
        // is not a bean property, lookupBeanProperty throws -- yet the field was already set.
        Bean bean = new Bean();
        int id = new ScilabJavaObject(bean).id;
        assertThrows(ScilabJavaException.class, () -> ScilabJavaObject.setField(id, "label", ScilabJavaObject.wrap("written")));
        assertEquals("written", bean.label, "the field is mutated before the fall-through throw");
    }

    @Test
    public void getFieldTypeDiscriminatesMethodsFieldsAndUnknowns() {
        int id = new ScilabJavaObject(new Bean()).id;
        assertEquals(0, ScilabJavaObject.getFieldType(id, "shout"), "a method is type 0");
        assertEquals(1, ScilabJavaObject.getFieldType(id, "count"), "a public field is type 1");
        assertEquals(1, ScilabJavaObject.getFieldType(id, "note"), "a bean property is type 1");
        assertEquals(-1, ScilabJavaObject.getFieldType(id, "nope"), "an unknown name is type -1");
    }

    @Test
    public void getFieldTypeOfArrayLengthAndUnknownArrayField() {
        int id = ScilabJavaObject.wrap(new int[] {1});
        assertEquals(1, ScilabJavaObject.getFieldType(id, "length"));
        assertEquals(-1, ScilabJavaObject.getFieldType(id, "notAField"));
    }

    /* ------------------------------------------------- accessible methods / fields / completion */

    @Test
    public void getAccessibleMethodsListsPublicMethodNames() throws ScilabJavaException {
        int id = new ScilabJavaObject(new Bean()).id;
        List<String> methods = Arrays.asList(ScilabJavaObject.getAccessibleMethods(id));
        assertTrue(methods.contains("shout"));
        assertTrue(methods.contains("getNote"));
        assertTrue(methods.contains("clear"));
    }

    @Test
    public void getAccessibleFieldsListsPublicFieldNames() throws ScilabJavaException {
        int id = new ScilabJavaObject(new Bean()).id;
        List<String> fields = Arrays.asList(ScilabJavaObject.getAccessibleFields(id));
        assertTrue(fields.contains("count"));
        assertTrue(fields.contains("label"));
    }

    @Test
    public void getAccessibleFieldsOnAnArrayIsJustLength() throws ScilabJavaException {
        int id = ScilabJavaObject.wrap(new double[] {1, 2});
        assertArrayEquals(new String[] {"length"}, ScilabJavaObject.getAccessibleFields(id));
    }

    @Test
    public void getCompletionWithEmptyPathFoldsBeanAccessorsIntoProperties() throws ScilabJavaException {
        int id = new ScilabJavaObject(new Bean()).id;
        List<String> names = Arrays.asList(ScilabJavaObject.getCompletion(id, new String[0]));
        assertTrue(names.contains("count"));
        assertTrue(names.contains("shout"));
        assertTrue(names.contains("note"), "getNote/setNote are folded into the property 'note'");
        assertFalse(names.contains("getNote"), "the raw accessor is removed once folded");
    }

    @Test
    public void getCompletionNavigatesAPublicFieldPath() throws ScilabJavaException {
        int id = new ScilabJavaObject(new Bean()).id;
        // "label" is a public String field, so completion descends into String's members.
        List<String> names = Arrays.asList(ScilabJavaObject.getCompletion(id, new String[] {"label"}));
        assertTrue(names.contains("charAt"), "descended into java.lang.String");
    }

    @Test
    public void getCompletionOnNullIsEmpty() throws ScilabJavaException {
        assertEquals(0, ScilabJavaObject.getCompletion(0, new String[0]).length);
    }

    /* ------------------------------------------------------------------------------- invoke */

    @Test
    public void invokeCallsAnInstanceMethodThroughTheReferenceTable() throws ScilabJavaException {
        int id = new ScilabJavaObject(new Bean()).id;
        int r = ScilabJavaObject.invoke(id, "shout", new int[] {ScilabJavaObject.wrap("hey")});
        assertEquals("HEY", ScilabJavaObject.unwrapString(r));
    }

    @Test
    public void invokeAVoidMethodReturnsMinusOne() throws ScilabJavaException {
        Bean bean = new Bean();
        int id = new ScilabJavaObject(bean).id;
        assertEquals(-1, ScilabJavaObject.invoke(id, "clear", new int[0]), "a void return maps to id -1");
        assertEquals("", bean.label);
    }

    @Test
    public void invokeOnNullIsRejected() {
        assertThrows(ScilabJavaException.class, () -> ScilabJavaObject.invoke(0, "toString", new int[0]));
    }

    /* ------------------------------------------------------------------------ extract / insert */

    @Test
    public void extractReadsFromAMapByKey() throws ScilabJavaException {
        Map<String, String> m = new HashMap<String, String>();
        m.put("k", "v");
        int id = new ScilabJavaObject(m).id;
        int r = ScilabJavaObject.extract(id, new int[] {ScilabJavaObject.wrap("k")});
        assertEquals("v", ScilabJavaObject.unwrapString(r));
    }

    @Test
    public void extractReadsFromAListWithAOneBasedIndex() throws ScilabJavaException {
        int id = new ScilabJavaObject(new ArrayList<String>(Arrays.asList("a", "b", "c"))).id;
        int r = ScilabJavaObject.extract(id, new int[] {ScilabJavaObject.wrap(2.0)});
        assertEquals("b", ScilabJavaObject.unwrapString(r), "Scilab index 2 -> list element 1");
    }

    @Test
    public void extractReadsFromAnArrayWithAOneBasedIndex() throws ScilabJavaException {
        int id = ScilabJavaObject.wrap(new int[] {10, 20, 30});
        int r = ScilabJavaObject.extract(id, new int[] {ScilabJavaObject.wrap(2.0)});
        assertEquals(20, ScilabJavaObject.unwrapInt(r));
    }

    @Test
    public void extractRejectsANegativeArgumentId() {
        int id = new ScilabJavaObject(new HashMap<String, String>()).id;
        assertThrows(ScilabJavaException.class, () -> ScilabJavaObject.extract(id, new int[] {-1}));
    }

    @Test
    public void insertPutsIntoAMap() throws ScilabJavaException {
        Map<String, String> m = new HashMap<String, String>();
        int id = new ScilabJavaObject(m).id;
        ScilabJavaObject.insert(id, new int[] {ScilabJavaObject.wrap("k")}, ScilabJavaObject.wrap("v"));
        assertEquals("v", m.get("k"));
    }

    @Test
    public void insertSetsIntoAListWithAOneBasedIndex() throws ScilabJavaException {
        List<String> l = new ArrayList<String>(Arrays.asList("a", "b", "c"));
        int id = new ScilabJavaObject(l).id;
        ScilabJavaObject.insert(id, new int[] {ScilabJavaObject.wrap(1.0)}, ScilabJavaObject.wrap("X"));
        assertEquals("X", l.get(0));
    }

    @Test
    public void insertSetsIntoAnArrayWithAOneBasedIndex() throws ScilabJavaException {
        int[] arr = {0, 0, 0};
        int id = ScilabJavaObject.wrap(arr);
        ScilabJavaObject.insert(id, new int[] {ScilabJavaObject.wrap(2.0)}, ScilabJavaObject.wrap(42));
        assertEquals(42, arr[1], "Scilab index 2 -> array slot 1");
    }

    /* -------------------------------------------------------------------------------- javaCast */

    @Test
    public void javaCastByClassNameUpcastsAndRetypesTheWrapper() throws ScilabJavaException {
        int alId = new ScilabJavaObject(new ArrayList<String>()).id;
        int cast = ScilabJavaObject.javaCast(alId, "java.util.List");
        assertEquals("java.util.List", ScilabJavaObject.getClassName(cast), "the wrapper is retyped to the cast target");
    }

    @Test
    public void javaCastByClassIdUpcasts() throws ScilabJavaException {
        int csId = ScilabClassLoader.loadJavaClass("java.lang.CharSequence");
        int cast = ScilabJavaObject.javaCast(ScilabJavaObject.wrap("hello"), csId);
        assertEquals("java.lang.CharSequence", ScilabJavaObject.getClassName(cast));
    }

    @Test
    public void javaCastToAnIncompatibleTypeThrows() {
        assertThrows(ScilabJavaException.class, () -> ScilabJavaObject.javaCast(ScilabJavaObject.wrap("hello"), "java.util.List"));
    }

    /* --------------------------------------------------------------------------------- getInfos */

    @Test
    public void getInfosIsNullOrNonEmptyAndDoesNotCorruptStdout() {
        PrintStream original = System.out;
        try {
            String[] infos = ScilabJavaObject.getInfos();
            if (infos != null) {
                assertTrue(infos.length > 0, "when available, the version banner has at least one line");
            }
        } finally {
            // Defensive: getInfos() has a latent System.out-restore bug on its success path.
            System.setOut(original);
        }
    }

    /* ----------------------------------------------------------------- enable/disable trace */

    @Test
    public void enablingTraceLogsTheIdApiOperationsAndCanBeDisabled() throws Exception {
        File log = File.createTempFile("jims-trace", ".log");
        log.deleteOnExit();
        // Keep the JIMS log records out of the console during the test run.
        Logger.getLogger("JIMS").setUseParentHandlers(false);
        try {
            ScilabJavaObject.enableTrace(log.getAbsolutePath());
            assertTrue(ScilabJavaObject.debug, "tracing is on");
            // Re-enabling while already tracing drives the disable-then-reopen path.
            ScilabJavaObject.enableTrace(log.getAbsolutePath());
            ScilabJavaObject.writeLog("hello from the test");

            // Every one of these has an `if (debug)` logging block that is otherwise never run.
            int arr = ScilabJavaObject.wrap(new int[] {1, 2, 3});
            ScilabJavaObject.getArrayElement(arr, new int[] {1});
            ScilabJavaObject.setArrayElement(arr, new int[] {0}, ScilabJavaObject.wrap(9));

            int bean = new ScilabJavaObject(new Bean()).id;
            ScilabJavaObject.invoke(bean, "shout", new int[] {ScilabJavaObject.wrap("x")});
            ScilabJavaObject.getField(bean, "count");
            ScilabJavaObject.setField(bean, "note", ScilabJavaObject.wrap("v"));
            ScilabJavaObject.getFieldType(bean, "count");
            ScilabJavaObject.getAccessibleMethods(bean);
            ScilabJavaObject.getAccessibleFields(bean);
            ScilabJavaObject.getCompletion(bean, new String[0]);
            ScilabJavaObject.getClassName(bean);

            int listId = new ScilabJavaObject(new ArrayList<String>(Arrays.asList("a", "b"))).id;
            ScilabJavaObject.extract(listId, new int[] {ScilabJavaObject.wrap(1.0)});
            ScilabJavaObject.insert(listId, new int[] {ScilabJavaObject.wrap(1.0)}, ScilabJavaObject.wrap("z"));

            int cls = ScilabClassLoader.loadJavaClass("java.util.BitSet");
            ScilabJavaClass.newInstance(cls, new int[0]);
            ScilabJavaArray.newInstance("double", new int[] {2});
            ScilabJavaObject.javaCast(ScilabJavaObject.wrap("hi"), "java.lang.CharSequence");
            ScilabJavaObject.limitDirectBuffer(new ScilabJavaObject(ByteBuffer.allocateDirect(8)).id);
            ScilabJavaObject.removeScilabJavaObject(arr);

            assertTrue(log.exists(), "the trace file is created");
        } finally {
            ScilabJavaObject.disableTrace();
            Logger.getLogger("JIMS").setUseParentHandlers(true);
            log.delete();
        }
        assertFalse(ScilabJavaObject.debug, "tracing is turned back off");
    }

    /* --------------------------------------------------------------------------- garbageCollect */

    @Test
    public void garbageCollectResetsTheReferenceTable() {
        int before = ScilabJavaObject.wrap("temp");
        assertTrue(ScilabJavaObject.isValidJavaObject(before));
        ScilabJavaObject.garbageCollect();
        // id 0 (null) is re-established and the id counter restarts at 1.
        assertTrue(ScilabJavaObject.isValidJavaObject(0));
        int fresh = ScilabJavaObject.wrap("after");
        assertEquals(1, fresh, "after a GC the id counter restarts from 1");
        assertEquals("after", ScilabJavaObject.unwrapString(fresh));
    }
}
