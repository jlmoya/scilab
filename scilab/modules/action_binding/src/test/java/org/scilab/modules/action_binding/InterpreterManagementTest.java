/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Jose Moya
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 *
 */

package org.scilab.modules.action_binding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link InterpreterManagement}.
 *
 * This SWIG-generated class is the thin Java facade over the interpreter: its
 * three command methods delegate to the native {@code InterpreterManagementJNI}
 * and so cannot run without a live engine. What CAN be exercised without any
 * native code is its pure-Java contract:
 * <ul>
 * <li>the "static singleton" guard — the protected no-arg constructor always
 * throws {@link UnsupportedOperationException} (directly and via reflection);</li>
 * <li>the class shape — public, concrete, and non-final so
 * {@code ScilabInterpreterManagement} can extend it;</li>
 * <li>the public API surface — the three command entry points are
 * {@code public static} and return an {@code int} status, which is the only
 * usable way to reach the class given the unusable constructor.</li>
 * </ul>
 *
 * None of these tests INVOKE the command methods, so the native library is
 * never touched. (Even if it were loaded, {@code InterpreterManagementJNI}'s
 * static block swallows {@code UnsatisfiedLinkError}.)
 */
class InterpreterManagementTest {

    // ------------------------------------------------------------------
    // "static singleton, must not be instantiated" guard
    // ------------------------------------------------------------------

    /**
     * The protected constructor is reachable from this same-package test, and
     * it must reject every instantiation with UnsupportedOperationException.
     */
    @Test
    void directInstantiationThrowsUnsupportedOperation() {
        assertThrows(UnsupportedOperationException.class,
            () -> new InterpreterManagement());
    }

    /** The no-arg constructor exists and is protected (not public): the class
     * is designed to be subclassed, never freely instantiated. */
    @Test
    void noArgConstructorIsProtected() throws NoSuchMethodException {
        Constructor<InterpreterManagement> ctor =
            InterpreterManagement.class.getDeclaredConstructor();
        int mods = ctor.getModifiers();
        assertTrue(Modifier.isProtected(mods), "constructor must be protected");
        assertFalse(Modifier.isPublic(mods), "constructor must not be public");
    }

    /** Reflection must not be able to bypass the guard either: newInstance
     * wraps the same UnsupportedOperationException in an InvocationTargetException. */
    @Test
    void reflectiveConstructionAlsoThrows() throws NoSuchMethodException {
        Constructor<InterpreterManagement> ctor =
            InterpreterManagement.class.getDeclaredConstructor();
        ctor.setAccessible(true);

        InvocationTargetException wrapper =
            assertThrows(InvocationTargetException.class, ctor::newInstance);
        Throwable cause = wrapper.getCause();
        assertNotNull(cause, "the reflective failure must carry a cause");
        assertTrue(cause instanceof UnsupportedOperationException,
            "the wrapped cause must be UnsupportedOperationException, was " + cause);
    }

    // ------------------------------------------------------------------
    // class shape
    // ------------------------------------------------------------------

    /** Public, concrete and non-final so ScilabInterpreterManagement extends it. */
    @Test
    void classIsPublicConcreteAndSubclassable() {
        int mods = InterpreterManagement.class.getModifiers();
        assertTrue(Modifier.isPublic(mods), "must be public");
        assertFalse(Modifier.isInterface(mods), "must be a class, not an interface");
        assertFalse(Modifier.isAbstract(mods), "must be concrete");
        assertFalse(Modifier.isFinal(mods), "must be non-final so it can be subclassed");
    }

    // ------------------------------------------------------------------
    // public command API — signature contract only, never invoked
    // ------------------------------------------------------------------

    @Test
    void putCommandInScilabQueueIsPublicStaticStringToInt() throws NoSuchMethodException {
        assertStringToIntCommand("putCommandInScilabQueue");
    }

    @Test
    void requestScilabExecIsPublicStaticStringToInt() throws NoSuchMethodException {
        assertStringToIntCommand("requestScilabExec");
    }

    @Test
    void interruptScilabIsPublicStaticNoArgReturningInt() throws NoSuchMethodException {
        Method m = InterpreterManagement.class.getDeclaredMethod("interruptScilab");
        int mods = m.getModifiers();
        assertTrue(Modifier.isPublic(mods), "interruptScilab must be public");
        assertTrue(Modifier.isStatic(mods), "interruptScilab must be static");
        assertEquals(int.class, m.getReturnType(), "interruptScilab must return an int status");
        assertEquals(0, m.getParameterCount(), "interruptScilab takes no arguments");
    }

    /**
     * The two command-submission methods share a shape: {@code public static
     * int name(String)}. Pinning it documents that a caller reaches the
     * interpreter through static, status-returning entry points (the class
     * itself being non-instantiable).
     */
    private static void assertStringToIntCommand(String name) throws NoSuchMethodException {
        Method m = InterpreterManagement.class.getDeclaredMethod(name, String.class);
        int mods = m.getModifiers();
        assertTrue(Modifier.isPublic(mods), name + " must be public");
        assertTrue(Modifier.isStatic(mods), name + " must be static");
        assertEquals(int.class, m.getReturnType(), name + " must return an int status");
        assertEquals(1, m.getParameterCount(), name + " takes a single argument");
        assertEquals(String.class, m.getParameterTypes()[0],
            name + " takes the command as a String");
    }
}
