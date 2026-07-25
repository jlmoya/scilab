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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link ScilabJavaConstructor}, the wrapper that resolves and
 * invokes a Java constructor from Scilab-side argument ids. Arguments are supplied the
 * real way — pre-wrapped in the {@link ScilabJavaObject} reference table — and the target
 * types are plain JDK classes that are not {@code java.awt.Component}, so the Swing/EDT
 * branch is never taken and the test stays hermetic and headless.
 */
public class ScilabJavaConstructorTest {

    public static class NoDefault {
        public NoDefault(String required) { }
    }

    @Test
    public void storesTheTargetClass() {
        ScilabJavaConstructor sjc = new ScilabJavaConstructor(String.class);
        assertSame(String.class, sjc.clazz);
    }

    @Test
    public void invokesTheNoArgConstructor() throws ScilabJavaException {
        Object built = new ScilabJavaConstructor(ArrayList.class).invoke(new int[0]);
        assertTrue(built instanceof ArrayList);
        assertEquals(0, ((ArrayList) built).size());
    }

    @Test
    public void invokesAConstructorWithAWrappedArgument() throws ScilabJavaException {
        int argId = ScilabJavaObject.wrap("abc");
        Object built = new ScilabJavaConstructor(StringBuilder.class).invoke(new int[] {argId});
        assertTrue(built instanceof StringBuilder);
        assertEquals("abc", built.toString());
    }

    @Test
    public void throwsWhenNoConstructorMatchesTheArgumentCount() {
        // NoDefault only exposes NoDefault(String); a zero-arg call matches nothing.
        assertThrows(ScilabJavaException.class,
                     () -> new ScilabJavaConstructor(NoDefault.class).invoke(new int[0]));
    }

    /* ============================================================ extended coverage */

    public static class VarargsCtor {
        final int total;
        public VarargsCtor(String label, int... nums) {
            int t = 0;
            for (int n : nums) {
                t += n;
            }
            this.total = t;
        }
    }

    public static class IntCtor {
        final int value;
        public IntCtor(int value) {
            this.value = value;
        }
    }

    public abstract static class AbstractCtor {
        public AbstractCtor() { }
        public abstract void go();
    }

    public static class BoomCtor {
        public BoomCtor() {
            throw new IllegalStateException("boom");
        }
    }

    @Test
    public void invokesAVarargsConstructorPackingTrailingArgs() throws ScilabJavaException {
        int label = ScilabJavaObject.wrap("x");
        int n1 = ScilabJavaObject.wrap(2);
        int n2 = ScilabJavaObject.wrap(3);
        Object built = new ScilabJavaConstructor(VarargsCtor.class).invoke(new int[] {label, n1, n2});
        assertTrue(built instanceof VarargsCtor);
        assertEquals(5, ((VarargsCtor) built).total, "the two trailing ints are packed into nums[]");
    }

    @Test
    public void coercesAnIntegralDoubleToAnIntConstructor() throws ScilabJavaException {
        int d = ScilabJavaObject.wrap(4.0);
        Object built = new ScilabJavaConstructor(IntCtor.class).invoke(new int[] {d});
        assertTrue(built instanceof IntCtor);
        assertEquals(4, ((IntCtor) built).value, "an integral double is coerced to the int parameter");
    }

    @Test
    public void anAbstractClassIsReportedAsNonInstantiable() {
        // getConstructors() exposes the public no-arg constructor, but newInstance() on an
        // abstract class raises InstantiationException, wrapped as ScilabJavaException.
        assertThrows(ScilabJavaException.class,
                     () -> new ScilabJavaConstructor(AbstractCtor.class).invoke(new int[0]));
    }

    @Test
    public void aConstructorThatThrowsIsWrappedInScilabJavaException() {
        assertThrows(ScilabJavaException.class,
                     () -> new ScilabJavaConstructor(BoomCtor.class).invoke(new int[0]));
    }
}
