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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link ScilabJavaMethod}, which resolves and invokes an
 * instance/static method by name and reports the effective return type back through the
 * caller-supplied {@code returnType[0]} slot. All targets are non-{@code Component} JDK
 * classes, so the Swing/EDT dispatch branch is never entered.
 *
 * <p>{@code call} is exercised directly (it takes ready Object/Class arrays); {@code invoke}
 * is exercised through the {@link ScilabJavaObject} reference table the way the JNI layer
 * calls it (arguments passed as ids).
 */
public class ScilabJavaMethodTest {

    @Test
    public void callInvokesAStaticMethodAndReportsAPrimitiveReturnType() throws ScilabJavaException {
        Class[] returnType = new Class[1];
        Object ret = ScilabJavaMethod.call("abs", Math.class, null, returnType,
                                           new Object[] {Integer.valueOf(-5)}, new Class[] {int.class});
        assertEquals(Integer.valueOf(5), ret);
        assertSame(int.class, returnType[0], "a primitive-returning method reports the primitive type");
    }

    @Test
    public void callInvokesAnInstanceMethodWithNoArguments() throws ScilabJavaException {
        Class[] returnType = new Class[1];
        Object ret = ScilabJavaMethod.call("length", String.class, "hello", returnType,
                                           new Object[0], new Class[0]);
        assertEquals(Integer.valueOf(5), ret);
        assertSame(int.class, returnType[0]);
    }

    @Test
    public void callInvokesAnInstanceMethodReturningAnObject() throws ScilabJavaException {
        Class[] returnType = new Class[1];
        Object ret = ScilabJavaMethod.call("toUpperCase", String.class, "abc", returnType,
                                           new Object[0], new Class[0]);
        assertEquals("ABC", ret);
        assertSame(String.class, returnType[0]);
    }

    @Test
    public void callThrowsForAnUnknownMethod() {
        assertThrows(ScilabJavaException.class,
                     () -> ScilabJavaMethod.call("noSuchMethod", String.class, "x", new Class[1],
                             new Object[0], new Class[0]));
    }

    @Test
    public void invokeResolvesArgumentsThroughTheReferenceTable() throws ScilabJavaException {
        int argId = ScilabJavaObject.wrap("bar");
        Class[] returnType = new Class[1];
        Object ret = ScilabJavaMethod.invoke("concat", String.class, "foo", returnType, new int[] {argId});
        assertEquals("foobar", ret);
        assertSame(String.class, returnType[0]);
    }

    /* ============================================================ extended coverage */

    public static class VarargsHost {
        public static String join(String sep, Object... parts) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) {
                    sb.append(sep);
                }
                sb.append(parts[i]);
            }
            return sb.toString();
        }
    }

    public static class ThrowingHost {
        public void boom() {
            throw new IllegalStateException("kaboom");
        }
    }

    @Test
    public void callReportsEachPrimitiveReturnType() throws ScilabJavaException {
        Class[] rt = new Class[1];
        ScilabJavaMethod.call("longValue", Long.class, 7L, rt, new Object[0], new Class[0]);
        assertSame(long.class, rt[0]);
        ScilabJavaMethod.call("floatValue", Float.class, 1.5f, rt, new Object[0], new Class[0]);
        assertSame(float.class, rt[0]);
        ScilabJavaMethod.call("doubleValue", Double.class, 2.5, rt, new Object[0], new Class[0]);
        assertSame(double.class, rt[0]);
        ScilabJavaMethod.call("shortValue", Short.class, (short) 3, rt, new Object[0], new Class[0]);
        assertSame(short.class, rt[0]);
        ScilabJavaMethod.call("byteValue", Byte.class, (byte) 4, rt, new Object[0], new Class[0]);
        assertSame(byte.class, rt[0]);
        ScilabJavaMethod.call("booleanValue", Boolean.class, Boolean.TRUE, rt, new Object[0], new Class[0]);
        assertSame(boolean.class, rt[0]);
        ScilabJavaMethod.call("charValue", Character.class, 'z', rt, new Object[0], new Class[0]);
        assertSame(char.class, rt[0]);
    }

    @Test
    public void callPacksAndInvokesAVarargsMethod() throws ScilabJavaException {
        Class[] rt = new Class[1];
        Object ret = ScilabJavaMethod.call("join", VarargsHost.class, null, rt,
                     new Object[] {"-", "a", "b"}, new Class[] {String.class, String.class, String.class});
        assertEquals("a-b", ret, "trailing args are packed into the varargs array and joined");
        assertSame(String.class, rt[0]);
    }

    @Test
    public void callWrapsAThrownExceptionInScilabJavaException() {
        assertThrows(ScilabJavaException.class,
                     () -> ScilabJavaMethod.call("boom", ThrowingHost.class, new ThrowingHost(), new Class[1],
                             new Object[0], new Class[0]));
    }
}
