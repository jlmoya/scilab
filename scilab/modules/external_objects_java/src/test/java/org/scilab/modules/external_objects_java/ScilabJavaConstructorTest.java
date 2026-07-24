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
}
