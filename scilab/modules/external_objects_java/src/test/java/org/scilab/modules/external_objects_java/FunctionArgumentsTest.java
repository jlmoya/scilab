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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.MethodDescriptor;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link FunctionArguments}: the argument coercion and
 * overload-resolution engine that bridges Scilab values onto Java method/constructor
 * signatures. Everything under test is pure reflection over ordinary Java classes.
 *
 * <p>Three surfaces are covered:
 * <ul>
 *   <li>{@code convert} and the ten built-in {@link Converter}s registered in the static
 *       initializer (number widening/narrowing, array boxing, reshaping, enum parsing);</li>
 *   <li>{@code registerConverter}/{@code unregisterConverter} lifecycle (each test restores
 *       global state via a finally block so the shared converter list is left untouched);</li>
 *   <li>{@code findMethod}/{@code findConstructor} distance-based overload selection, the
 *       double-&gt;int coercion path (distance 2048) and varargs packing.</li>
 * </ul>
 */
public class FunctionArgumentsTest {

    /* -------------------------------------------------------- helper reflection targets */

    public static class MethodHost {
        public String pick(Object o) {
            return "object";
        }
        public String pick(String s) {
            return "string";
        }
        public String only(int x) {
            return "int:" + x;
        }
        public String sum(int a, int... rest) {
            int t = a;
            for (int r : rest) {
                t += r;
            }
            return "sum:" + t;
        }
    }

    public static class CtorHost {
        public CtorHost() { }
        public CtorHost(String s) { }
        public CtorHost(int i) { }
    }

    public static class OnlyString {
        public OnlyString(String s) { }
    }

    private static MethodDescriptor[] descriptorsOf(Class<?> c) throws IntrospectionException {
        return Introspector.getBeanInfo(c).getMethodDescriptors();
    }

    /* --------------------------------------------------------------------- convert(...) */

    @Test
    public void convertNullIsNull() {
        assertNull(FunctionArguments.convert(null, String.class));
    }

    @Test
    public void convertReturnsSameInstanceWhenAlreadyAssignable() {
        String s = "already";
        assertSame(s, FunctionArguments.convert(s, String.class));
        Integer i = 5;
        assertSame(i, FunctionArguments.convert(i, Number.class));
    }

    @Test
    public void convertNumberToIntTruncates() {
        Object r = FunctionArguments.convert(Double.valueOf(3.7), int.class);
        assertEquals(Integer.valueOf(3), r);
    }

    @Test
    public void convertDoubleToFloat() {
        Object r = FunctionArguments.convert(Double.valueOf(2.5), float.class);
        assertEquals(Float.valueOf(2.5f), r);
    }

    @Test
    public void convertDoubleArrayToIntArray() {
        Object r = FunctionArguments.convert(new double[] {1.9, 2.1, 3.0}, int[].class);
        assertTrue(r instanceof int[]);
        assertArrayEquals(new int[] {1, 2, 3}, (int[]) r);
    }

    @Test
    public void convertDoubleArrayToBoxedArray() {
        Object r = FunctionArguments.convert(new double[] {1.0, 2.0}, Double[].class);
        assertTrue(r instanceof Double[]);
        assertArrayEquals(new Double[] {1.0, 2.0}, (Double[]) r);
    }

    @Test
    public void convertStringToEnum() {
        Object r = FunctionArguments.convert("SECONDS", TimeUnit.class);
        assertSame(TimeUnit.SECONDS, r);
    }

    @Test
    public void convertMatrixToFlatArray() {
        Object r = FunctionArguments.convert(new double[][] {{1, 2}, {3, 4}}, double[].class);
        assertTrue(r instanceof double[]);
        assertArrayEquals(new double[] {1, 2, 3, 4}, (double[]) r, 0.0);
    }

    @Test
    public void convertFlatArrayToMatrix() {
        Object r = FunctionArguments.convert(new double[] {1, 2, 3}, double[][].class);
        assertTrue(r instanceof double[][]);
        double[][] m = (double[][]) r;
        assertEquals(1, m.length);
        assertArrayEquals(new double[] {1, 2, 3}, m[0], 0.0);
    }

    @Test
    public void convertBoxedScalarToArrayViaMappings() {
        Object r = FunctionArguments.convert(Double.valueOf(5.0), double[].class);
        assertTrue(r instanceof double[]);
        assertArrayEquals(new double[] {5.0}, (double[]) r, 0.0);
    }

    @Test
    public void convertWithNoApplicableConverterReturnsTheOriginal() {
        // No converter maps String -> Integer, so convert() is a documented pass-through.
        String s = "not-a-number";
        assertSame(s, FunctionArguments.convert(s, Integer.class));
    }

    /* ----------------------------------------------- register / unregister converter */

    @Test
    public void aRegisteredConverterIsConsultedByConvert() {
        Converter c = new Converter() {
            @Override
            public Object convert(Object original, Class<?> to) {
                return "CONVERTED:" + original;
            }
            @Override
            public boolean canConvert(Class<?> from, Class<?> to) {
                return from == String.class && to == StringBuilder.class;
            }
        };

        try {
            FunctionArguments.registerConverter(c);
            assertEquals("CONVERTED:x", FunctionArguments.convert("x", StringBuilder.class));
        } finally {
            FunctionArguments.unregisterConverter(c);
        }

        // Once unregistered, nothing matches String -> StringBuilder: pass-through again.
        String s = "x";
        assertSame(s, FunctionArguments.convert(s, StringBuilder.class));
    }

    @Test
    public void registeringTheSameConverterTwiceKeepsASingleEntry() {
        Converter c = new Converter() {
            @Override
            public Object convert(Object original, Class<?> to) {
                return "ONCE";
            }
            @Override
            public boolean canConvert(Class<?> from, Class<?> to) {
                return from == String.class && to == StringBuilder.class;
            }
        };

        try {
            FunctionArguments.registerConverter(c);
            FunctionArguments.registerConverter(c); // de-duplicated (moved, not appended)
            assertEquals("ONCE", FunctionArguments.convert("y", StringBuilder.class));
            // A single unregister must fully remove it, proving there was no duplicate.
            FunctionArguments.unregisterConverter(c);
            String s = "y";
            assertSame(s, FunctionArguments.convert(s, StringBuilder.class));
        } finally {
            FunctionArguments.unregisterConverter(c);
        }
    }

    /* ------------------------------------------------------------------- findMethod(...) */

    @Test
    public void findMethodPrefersTheClosestOverload() throws Exception {
        MethodDescriptor[] d = descriptorsOf(MethodHost.class);
        Object[] res = FunctionArguments.findMethod("pick", d, new Class[] {String.class}, new Object[] {"hi"});
        assertEquals(1, res.length, "no varargs repackaging expected");
        Method m = (Method) res[0];
        assertArrayEquals(new Class[] {String.class}, m.getParameterTypes(),
                          "the String overload wins over the Object overload");
    }

    @Test
    public void findMethodCoercesDoubleArgumentToIntParameter() throws Exception {
        MethodDescriptor[] d = descriptorsOf(MethodHost.class);
        Class[] argsClass = new Class[] {Double.class};
        Object[] args = new Object[] {Double.valueOf(3.0)};

        Object[] res = FunctionArguments.findMethod("only", d, argsClass, args);

        Method m = (Method) res[0];
        assertArrayEquals(new Class[] {int.class}, m.getParameterTypes());
        // findMethod rewrites the argument arrays in place with the coerced value.
        assertEquals(int.class, argsClass[0]);
        assertEquals(Integer.valueOf(3), args[0]);
    }

    @Test
    public void findMethodPacksTrailingArgsForAVarargsMethod() throws Exception {
        MethodDescriptor[] d = descriptorsOf(MethodHost.class);
        Object[] res = FunctionArguments.findMethod("sum", d,
                       new Class[] {int.class, int.class, int.class}, new Object[] {1, 2, 3});

        assertEquals(2, res.length, "varargs selection returns {method, repackaged args}");
        Method m = (Method) res[0];
        assertEquals("sum", m.getName());

        Object[] newArgs = (Object[]) res[1];
        assertEquals(2, newArgs.length, "fixed arg + one packed vararg array");
        assertEquals(Integer.valueOf(1), newArgs[0]);
        assertTrue(newArgs[1] instanceof int[]);
        assertArrayEquals(new int[] {2, 3}, (int[]) newArgs[1]);
    }

    @Test
    public void findMethodThrowsWhenNoNameMatches() throws Exception {
        MethodDescriptor[] d = descriptorsOf(MethodHost.class);
        assertThrows(NoSuchMethodException.class,
                     () -> FunctionArguments.findMethod("doesNotExist", d, new Class[0], new Object[0]));
    }

    /* -------------------------------------------------------------- findConstructor(...) */

    @Test
    public void findConstructorSelectsByArgumentType() throws Exception {
        Object[] res = FunctionArguments.findConstructor(CtorHost.class.getConstructors(),
                       new Class[] {String.class}, new Object[] {"x"});
        Constructor<?> ctor = (Constructor<?>) res[0];
        assertArrayEquals(new Class[] {String.class}, ctor.getParameterTypes());
    }

    @Test
    public void findConstructorSelectsTheNoArgConstructor() throws Exception {
        Object[] res = FunctionArguments.findConstructor(CtorHost.class.getConstructors(),
                       new Class[0], new Object[0]);
        Constructor<?> ctor = (Constructor<?>) res[0];
        assertEquals(0, ctor.getParameterTypes().length);
    }

    @Test
    public void findConstructorCoercesDoubleToIntConstructor() throws Exception {
        Class[] argsClass = new Class[] {Double.class};
        Object[] args = new Object[] {Double.valueOf(7.0)};

        Object[] res = FunctionArguments.findConstructor(CtorHost.class.getConstructors(), argsClass, args);

        Constructor<?> ctor = (Constructor<?>) res[0];
        assertArrayEquals(new Class[] {int.class}, ctor.getParameterTypes());
        assertEquals(Integer.valueOf(7), args[0]);
    }

    @Test
    public void findConstructorThrowsWhenNothingMatches() {
        assertThrows(NoSuchMethodException.class,
                     () -> FunctionArguments.findConstructor(OnlyString.class.getConstructors(),
                             new Class[] {Integer.class}, new Object[] {5}));
    }
}
