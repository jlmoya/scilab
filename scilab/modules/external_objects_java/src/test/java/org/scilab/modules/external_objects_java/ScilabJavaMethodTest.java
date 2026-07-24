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
}
