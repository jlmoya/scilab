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

package org.scilab.modules.localization;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for the SWIG-generated {@link Messages} wrapper (the Java side of
 * Scilab's gettext bridge).
 *
 * <p>{@code Messages} is a non-instantiable facade whose {@code gettext}/{@code dgettext}
 * methods delegate to the {@code native} methods on {@code MessagesJNI}. The tests cover
 * only the pure-Java surface: the throwing constructor and the public API shape. They never
 * invoke the delegating methods, so {@code MessagesJNI}'s {@code System.loadLibrary} static
 * initializer is never triggered and the tests need no native library.
 */
public class MessagesTest {

    @Test
    public void constructorThrowsUnsupportedOperation() {
        assertThrows(UnsupportedOperationException.class, () -> new Messages());
    }

    @Test
    public void soleConstructorIsProtectedNoArgAndThrowsViaReflection() throws Exception {
        Constructor<?>[] ctors = Messages.class.getDeclaredConstructors();
        assertEquals(1, ctors.length, "SWIG stub should declare exactly one constructor");

        Constructor<Messages> ctor = Messages.class.getDeclaredConstructor();
        assertTrue(Modifier.isProtected(ctor.getModifiers()), "constructor must be protected");

        ctor.setAccessible(true);
        InvocationTargetException wrapped =
                assertThrows(InvocationTargetException.class, ctor::newInstance);
        assertInstanceOf(UnsupportedOperationException.class, wrapped.getCause());
    }

    @Test
    public void classIsPublicAndConcrete() {
        int mod = Messages.class.getModifiers();
        assertTrue(Modifier.isPublic(mod));
        assertFalse(Modifier.isAbstract(mod));
        assertFalse(Modifier.isInterface(mod));
    }

    @Test
    public void gettextIsAPublicStaticNonNativeStringToStringDelegator() throws Exception {
        Method gettext = Messages.class.getDeclaredMethod("gettext", String.class);

        int mod = gettext.getModifiers();
        assertTrue(Modifier.isPublic(mod));
        assertTrue(Modifier.isStatic(mod));
        assertFalse(Modifier.isNative(mod), "the wrapper method must not itself be native");

        assertEquals(String.class, gettext.getReturnType());
        assertArrayEquals(new Class<?>[] {String.class}, gettext.getParameterTypes());
    }

    @Test
    public void dgettextTakesDomainAndKeyAndReturnsString() throws Exception {
        Method dgettext =
                Messages.class.getDeclaredMethod("dgettext", String.class, String.class);

        int mod = dgettext.getModifiers();
        assertTrue(Modifier.isPublic(mod));
        assertTrue(Modifier.isStatic(mod));
        assertFalse(Modifier.isNative(mod), "the wrapper method must not itself be native");

        assertEquals(String.class, dgettext.getReturnType());
        // (domain, key) — the two-arg, domain-qualified lookup.
        assertArrayEquals(
                new Class<?>[] {String.class, String.class}, dgettext.getParameterTypes());
    }

    @Test
    public void singleArgGettextIsNotAlsoDeclaredWithTwoArgs() {
        // gettext is the domain-less lookup; the two-arg variant is dgettext, not an
        // overload of gettext. Documents the intended API split.
        assertThrows(NoSuchMethodException.class,
                () -> Messages.class.getDeclaredMethod("gettext", String.class, String.class));
    }
}
