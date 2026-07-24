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
 * Hermetic unit tests for the SWIG-generated {@link WindowsDefaultLanguage} wrapper.
 *
 * <p>The facade exposes a Windows-registry getter/setter pair that delegates to the
 * {@code native} methods on {@code WindowsDefaultLanguageJNI}. These tests validate the
 * pure-Java surface — the non-instantiability contract and the getter/setter signatures —
 * without invoking the delegating methods, so no {@code System.loadLibrary} runs and the
 * tests are platform-independent (they pass on macOS/Linux even though the underlying
 * feature is Windows-only).
 */
public class WindowsDefaultLanguageTest {

    @Test
    public void constructorThrowsUnsupportedOperation() {
        assertThrows(UnsupportedOperationException.class, () -> new WindowsDefaultLanguage());
    }

    @Test
    public void soleConstructorIsProtectedNoArgAndThrowsViaReflection() throws Exception {
        Constructor<?>[] ctors = WindowsDefaultLanguage.class.getDeclaredConstructors();
        assertEquals(1, ctors.length, "SWIG stub should declare exactly one constructor");

        Constructor<WindowsDefaultLanguage> ctor =
                WindowsDefaultLanguage.class.getDeclaredConstructor();
        assertTrue(Modifier.isProtected(ctor.getModifiers()), "constructor must be protected");

        ctor.setAccessible(true);
        InvocationTargetException wrapped =
                assertThrows(InvocationTargetException.class, ctor::newInstance);
        assertInstanceOf(UnsupportedOperationException.class, wrapped.getCause());
    }

    @Test
    public void classIsPublicAndConcrete() {
        int mod = WindowsDefaultLanguage.class.getModifiers();
        assertTrue(Modifier.isPublic(mod));
        assertFalse(Modifier.isAbstract(mod));
    }

    @Test
    public void setDefaultLanguageIsAPublicStaticNonNativeVoidTakingAString() throws Exception {
        Method setter =
                WindowsDefaultLanguage.class.getDeclaredMethod("setdefaultlanguage", String.class);

        int mod = setter.getModifiers();
        assertTrue(Modifier.isPublic(mod));
        assertTrue(Modifier.isStatic(mod));
        assertFalse(Modifier.isNative(mod), "the wrapper method must not itself be native");

        assertEquals(void.class, setter.getReturnType(), "the setter returns void");
        assertArrayEquals(new Class<?>[] {String.class}, setter.getParameterTypes());
    }

    @Test
    public void getDefaultLanguageIsAPublicStaticNonNativeNoArgStringGetter() throws Exception {
        Method getter =
                WindowsDefaultLanguage.class.getDeclaredMethod("getdefaultlanguage");

        int mod = getter.getModifiers();
        assertTrue(Modifier.isPublic(mod));
        assertTrue(Modifier.isStatic(mod));
        assertFalse(Modifier.isNative(mod), "the wrapper method must not itself be native");

        assertEquals(String.class, getter.getReturnType(), "the getter returns the language");
        assertEquals(0, getter.getParameterCount(), "the getter takes no arguments");
    }
}
