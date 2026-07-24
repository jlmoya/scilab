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

package org.scilab.modules.history_manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.junit.jupiter.api.Test;

/**
 * Hermetic tests for {@link HistoryManagement}, the SWIG-generated facade over the native
 * history manager.
 *
 * <p>Every <em>functional</em> method on this class delegates to a {@code native} method in
 * {@code HistoryManagementJNI} and so cannot run without the {@code scihistory_manager} shared
 * library — those calls are out of scope for a hermetic unit test. What <em>is</em> pure Java and
 * verifiable here: the non-instantiability guard baked into the generated constructor, and the
 * shape of the public API surface (a utility facade of only static entry points). Instantiating
 * the class runs only the guard (it throws before touching any native method), and the reflective
 * checks read class metadata without ever invoking a delegating method, so no native library load
 * is attempted anywhere in this suite.
 */
public class HistoryManagementTest {

    @Test
    public void constructorThrowsToForbidInstantiation() {
        // The generated protected constructor is a hard "do not instantiate" guard, and it is
        // pure Java: it throws before any native method could be reached.
        assertThrows(UnsupportedOperationException.class,
                     () -> new HistoryManagement());
    }

    @Test
    public void theOnlyDeclaredConstructorIsProtectedAndZeroArg() {
        Constructor<?>[] ctors = HistoryManagement.class.getDeclaredConstructors();
        assertEquals(1, ctors.length, "the facade should expose exactly one constructor");
        Constructor<?> ctor = ctors[0];
        assertEquals(0, ctor.getParameterCount());
        assertTrue(Modifier.isProtected(ctor.getModifiers()),
                   "SWIG emits a protected guard constructor");
    }

    @Test
    public void theClassIsPublic() {
        assertTrue(Modifier.isPublic(HistoryManagement.class.getModifiers()));
    }

    @Test
    public void everyPublicMethodIsStatic() {
        // Utility-facade invariant: the wrapper exposes only static entry points, never
        // instance methods. Reading modifiers does not initialize the class or load any native lib.
        for (Method m : HistoryManagement.class.getDeclaredMethods()) {
            if (Modifier.isPublic(m.getModifiers())) {
                assertTrue(Modifier.isStatic(m.getModifiers()),
                           "expected a static method but found an instance one: " + m.getName());
            }
        }
    }

    @Test
    public void publicApiSurfaceMatchesTheDocumentedContract() throws Exception {
        // Pin a representative slice of the generated facade so accidental drift (a rename, a
        // changed return type, a changed arity) is caught without invoking any native code.
        assertEquals(boolean.class,
                     HistoryManagement.class.getMethod("appendLineToScilabHistory", String.class)
                     .getReturnType());
        assertEquals(boolean.class,
                     HistoryManagement.class.getMethod("appendLinesToScilabHistory",
                             String[].class, int.class).getReturnType());
        assertEquals(String[].class,
                     HistoryManagement.class.getMethod("getAllLinesOfScilabHistory").getReturnType());
        assertEquals(String.class,
                     HistoryManagement.class.getMethod("getNthLineInScilabHistory", int.class)
                     .getReturnType());
        assertEquals(boolean.class,
                     HistoryManagement.class.getMethod("deleteNthLineScilabHistory", int.class)
                     .getReturnType());
        assertEquals(String.class,
                     HistoryManagement.class.getMethod("getFilenameScilabHistory").getReturnType());
        assertEquals(int.class,
                     HistoryManagement.class.getMethod("getSizeScilabHistory").getReturnType());
        assertEquals(boolean.class,
                     HistoryManagement.class.getMethod("historyIsEnabled").getReturnType());
        assertEquals(void.class,
                     HistoryManagement.class.getMethod("displayScilabHistory").getReturnType());
    }
}
