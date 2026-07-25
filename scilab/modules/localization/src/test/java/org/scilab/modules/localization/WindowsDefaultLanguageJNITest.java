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
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for the SWIG-generated {@link WindowsDefaultLanguageJNI} JNI stub — the
 * native binding layer under the {@link WindowsDefaultLanguage} facade that reads/writes the
 * default UI language in the Windows registry.
 *
 * <p>{@code WindowsDefaultLanguageJNI} declares {@code native void setdefaultlanguage(String)}
 * and {@code native String getdefaultlanguage()} and, in a static initializer, calls
 * {@code System.loadLibrary("scilocalization")}. The tests exercise ONLY the pure-Java,
 * reflectively-observable surface and are therefore platform-independent: they pass on
 * macOS/Linux even though the underlying feature is Windows-only, precisely because they never
 * invoke the native methods.
 *
 * <p><b>Why this is hermetic.</b> Reflective lookup ({@code getDeclaredMethod} /
 * {@code getDeclaredConstructor} / {@code getModifiers} / {@code getReturnType} /
 * {@code getParameterTypes}) loads and links the class but does NOT run its static
 * initializer, so {@code System.loadLibrary} never fires. The tests NEVER instantiate the
 * class nor invoke either native method (either would force initialization). They pin the JNI
 * <em>ABI contract</em> the C symbols
 * {@code Java_org_scilab_modules_localization_WindowsDefaultLanguageJNI_setdefaultlanguage} /
 * {@code ..._getdefaultlanguage} must match.
 */
public class WindowsDefaultLanguageJNITest {

    @Test
    public void classIsPublicConcreteAndNotFinal() {
        int mod = WindowsDefaultLanguageJNI.class.getModifiers();
        assertTrue(Modifier.isPublic(mod));
        assertFalse(Modifier.isAbstract(mod), "the JNI stub is a concrete class");
        assertFalse(Modifier.isInterface(mod));
        assertFalse(Modifier.isFinal(mod), "SWIG emits a plain, non-final class");
    }

    @Test
    public void soleConstructorIsProtectedAndNoArg() {
        Constructor<?>[] ctors = WindowsDefaultLanguageJNI.class.getDeclaredConstructors();
        assertEquals(1, ctors.length, "SWIG stub should declare exactly one constructor");

        Constructor<?> ctor = ctors[0];
        assertTrue(Modifier.isProtected(ctor.getModifiers()), "constructor must be protected");
        assertEquals(0, ctor.getParameterCount(), "constructor takes no arguments");
        // Intentionally NOT invoked: <clinit> would run System.loadLibrary("scilocalization").
    }

    @Test
    public void setterIsAPublicStaticFinalNativeVoidTakingAString() throws Exception {
        Method setter =
                WindowsDefaultLanguageJNI.class.getDeclaredMethod("setdefaultlanguage", String.class);

        int mod = setter.getModifiers();
        assertTrue(Modifier.isPublic(mod));
        assertTrue(Modifier.isStatic(mod));
        assertTrue(Modifier.isFinal(mod), "SWIG native binding is final");
        assertTrue(Modifier.isNative(mod), "setdefaultlanguage is the JNI native binding");

        assertEquals(void.class, setter.getReturnType(), "the setter returns void");
        assertArrayEquals(new Class<?>[] {String.class}, setter.getParameterTypes(),
                "the setter takes the language to persist");
    }

    @Test
    public void getterIsAPublicStaticFinalNativeNoArgStringGetter() throws Exception {
        Method getter =
                WindowsDefaultLanguageJNI.class.getDeclaredMethod("getdefaultlanguage");

        int mod = getter.getModifiers();
        assertTrue(Modifier.isPublic(mod));
        assertTrue(Modifier.isStatic(mod));
        assertTrue(Modifier.isFinal(mod), "SWIG native binding is final");
        assertTrue(Modifier.isNative(mod), "getdefaultlanguage is the JNI native binding");

        assertEquals(String.class, getter.getReturnType(), "the getter returns the language");
        assertEquals(0, getter.getParameterCount(), "the getter takes no arguments");
    }

    @Test
    public void exactlyTwoRealMethodsGetterAndSetterBothNative() {
        // The whole non-constructor surface is native: the getter/setter pair, nothing else.
        // Synthetic methods are skipped so a coverage agent's injected $jacocoInit() (or any
        // other compiler/tool synthetic) does not perturb this ABI-surface assertion.
        Set<String> realNames = new HashSet<>();
        for (Method m : WindowsDefaultLanguageJNI.class.getDeclaredMethods()) {
            if (m.isSynthetic()) {
                continue;
            }
            assertTrue(Modifier.isNative(m.getModifiers()),
                    m.getName() + " must be a native binding");
            realNames.add(m.getName());
        }
        assertEquals(Set.of("getdefaultlanguage", "setdefaultlanguage"), realNames,
                "the getter and setter are the only real declared methods");
    }

    @Test
    public void getterAndSetterArityContractIsExact() {
        // The getter is strictly no-arg and the setter strictly one-arg: no overloads exist.
        assertThrows(NoSuchMethodException.class,
                () -> WindowsDefaultLanguageJNI.class.getDeclaredMethod("getdefaultlanguage", String.class));
        assertThrows(NoSuchMethodException.class,
                () -> WindowsDefaultLanguageJNI.class.getDeclaredMethod("setdefaultlanguage"));
    }
}
