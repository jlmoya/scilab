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

import java.util.Arrays;

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
}
