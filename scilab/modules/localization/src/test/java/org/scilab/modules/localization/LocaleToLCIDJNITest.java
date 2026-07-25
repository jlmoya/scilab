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
 * Hermetic unit tests for the SWIG-generated {@link LocaleToLCIDJNI} JNI stub.
 *
 * <p>{@code LocaleToLCIDJNI} is the native-binding layer under the {@link LocaleToLCID}
 * facade: it declares the {@code native String convert(String)} method and, in a static
 * initializer, calls {@code System.loadLibrary("scilocalization")}. That native library is
 * NOT present in a plain unit-test JVM, so these tests are written to exercise ONLY the
 * pure-Java, reflectively-observable surface — the class/constructor shape and the JNI
 * <em>ABI contract</em> (method name, arity, signature and {@code native}/{@code static}/
 * {@code final} modifiers that the C symbol
 * {@code Java_org_scilab_modules_localization_LocaleToLCIDJNI_convert} must match).
 *
 * <p><b>Why this is hermetic.</b> The tests use only reflective <em>lookup</em>
 * ({@link Class#getDeclaredMethod}, {@link Class#getDeclaredConstructor},
 * {@code getModifiers}, {@code getReturnType}, {@code getParameterTypes}). Per the JLS,
 * those operations require the class to be loaded and linked but do NOT trigger its static
 * initializer, and linking a class that merely <em>declares</em> {@code native} methods does
 * not need the native implementation (binding happens only on first invocation). Therefore
 * no {@code System.loadLibrary} ever runs. The tests deliberately NEVER instantiate the class
 * nor invoke {@code convert} — either of which would force initialization and try to load the
 * native library. This mirrors the technique the {@link LocaleToLCID} facade test documents.
 */
public class LocaleToLCIDJNITest {

    @Test
    public void classIsPublicConcreteAndNotFinal() {
        // Referencing the class literal loads (but does NOT initialize) the class.
        int mod = LocaleToLCIDJNI.class.getModifiers();
        assertTrue(Modifier.isPublic(mod), "the JNI stub is part of the module's public surface");
        assertFalse(Modifier.isAbstract(mod), "the JNI stub is a concrete class");
        assertFalse(Modifier.isInterface(mod));
        // SWIG emits a plain (non-final) class; documents current shape.
        assertFalse(Modifier.isFinal(mod));
    }

    @Test
    public void soleConstructorIsProtectedAndNoArg() {
        Constructor<?>[] ctors = LocaleToLCIDJNI.class.getDeclaredConstructors();
        assertEquals(1, ctors.length, "SWIG stub should declare exactly one constructor");

        Constructor<?> ctor = ctors[0];
        assertTrue(Modifier.isProtected(ctor.getModifiers()),
                "the constructor is protected (utility-class idiom)");
        assertEquals(0, ctor.getParameterCount(), "the constructor takes no arguments");
        // NOTE: we intentionally do NOT call newInstance() here. Unlike the LocaleToLCID
        // facade (which has no static initializer, so instantiating it is safe), instantiating
        // LocaleToLCIDJNI would run its <clinit> and thus System.loadLibrary("scilocalization"),
        // which is exactly the native dependency these hermetic tests must avoid.
    }

    @Test
    public void convertIsThePublicStaticFinalNativeStringToStringBinding() throws Exception {
        Method convert = LocaleToLCIDJNI.class.getDeclaredMethod("convert", String.class);

        int mod = convert.getModifiers();
        assertTrue(Modifier.isPublic(mod), "convert must be public");
        assertTrue(Modifier.isStatic(mod), "convert must be static");
        assertTrue(Modifier.isFinal(mod), "convert must be final (SWIG native binding)");
        // The native marker lives HERE, at the JNI layer — the mirror image of the facade test,
        // which pins that the wrapper's convert is NOT native. Together they lock the boundary.
        assertTrue(Modifier.isNative(mod), "convert is the JNI native binding");

        assertEquals(String.class, convert.getReturnType(), "the LCID string is returned");
        assertArrayEquals(new Class<?>[] {String.class}, convert.getParameterTypes(),
                "convert maps a single locale String to its LCID String");
    }

    @Test
    public void convertIsTheOnlyRealDeclaredMethodAndItIsNative() {
        // The stub's entire non-constructor surface is the single native binding: no
        // pure-Java helper methods, no overloads. Pins the JNI surface as exactly one symbol.
        // Synthetic methods are filtered out so the assertion targets the SWIG-authored
        // surface, not build-time instrumentation artifacts (e.g. JaCoCo's on-the-fly agent
        // injects a synthetic $jacocoInit() when coverage is enabled).
        Set<String> realNames = new HashSet<>();
        for (Method m : LocaleToLCIDJNI.class.getDeclaredMethods()) {
            if (m.isSynthetic()) {
                continue;
            }
            assertTrue(Modifier.isNative(m.getModifiers()),
                    m.getName() + " must be a native binding");
            realNames.add(m.getName());
        }
        assertEquals(Set.of("convert"), realNames,
                "convert is the sole real (non-synthetic) declared method");
    }

    @Test
    public void noSingleArgOrThreeArgConvertOverloadExists() {
        // Documents the exact arity of the JNI contract: convert(String) only.
        assertThrows(NoSuchMethodException.class,
                () -> LocaleToLCIDJNI.class.getDeclaredMethod("convert"));
        assertThrows(NoSuchMethodException.class,
                () -> LocaleToLCIDJNI.class.getDeclaredMethod("convert", String.class, String.class));
    }
}
