/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Scilab Enterprises
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 *
 */

package org.scilab.modules.completion;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Hermetic tests for the SWIG-generated {@link Completion} binding facade.
 *
 * <p>Every {@code searchXxxDictionary}/{@code getXxx}/{@code completelineforjava}
 * method delegates straight into {@link CompletionJNI} and therefore needs the
 * native {@code scicompletion} library — those bodies are NOT exercised here.
 * What <em>is</em> pure Java and worth pinning down is the shape SWIG emits:
 * the class is non-instantiable (protected constructor that throws), and it
 * exposes a fixed set of {@code public static} methods with exact signatures
 * that {@link AbstractSciCompletionWindow} calls into. Reflection reads that
 * surface without ever invoking (and thus without loading the native lib).</p>
 */
public class CompletionTest {

    /** The 13 delegating entry points SWIG generates, by name. */
    private static final String[] PUBLIC_API = {
        "searchAllDictionaries",
        "searchFunctionsDictionary",
        "searchCommandsDictionary",
        "searchMacrosDictionary",
        "searchVariablesDictionary",
        "searchFilesDictionary",
        "searchFieldsDictionary",
        "searchHandleGraphicsPropertiesDictionary",
        "searchMustBeDictionary",
        "getPartLevel",
        "getFilePartLevel",
        "getCommonPart",
        "completelineforjava",
    };

    @Test
    void constructorThrowsUnsupportedOperationException() {
        // The protected no-arg constructor is reachable from a same-package
        // test; SWIG's body is a bare `throw new UnsupportedOperationException()`.
        // This does not touch CompletionJNI, so no native library is loaded.
        assertThrows(UnsupportedOperationException.class, () -> new Completion());
    }

    @Test
    void constructorIsProtectedAndSoleConstructor() throws Exception {
        Constructor<?>[] ctors = Completion.class.getDeclaredConstructors();
        assertEquals(1, ctors.length, "SWIG emits exactly one constructor");
        Constructor<Completion> c = Completion.class.getDeclaredConstructor();
        assertTrue(Modifier.isProtected(c.getModifiers()),
                   "constructor must be protected (non-instantiable facade)");
    }

    @Test
    void classIsPublicAndConcrete() {
        int m = Completion.class.getModifiers();
        assertTrue(Modifier.isPublic(m));
        assertFalse(Modifier.isAbstract(m));
        assertFalse(Modifier.isInterface(m));
    }

    @Test
    void everyDeclaredPublicMethodIsStatic() {
        for (Method method : Completion.class.getDeclaredMethods()) {
            if (method.isSynthetic()) {
                continue;
            }
            if (Modifier.isPublic(method.getModifiers())) {
                assertTrue(Modifier.isStatic(method.getModifiers()),
                           "public method must be static: " + method.getName());
            }
        }
    }

    @Test
    void exposesExactlyTheThirteenPublicStaticEntryPoints() {
        int count = 0;
        for (Method method : Completion.class.getDeclaredMethods()) {
            if (method.isSynthetic()) {
                continue;
            }
            int mod = method.getModifiers();
            if (Modifier.isPublic(mod) && Modifier.isStatic(mod)) {
                count++;
            }
        }
        assertEquals(PUBLIC_API.length, count);

        // ... and each expected name is actually present.
        for (String name : PUBLIC_API) {
            boolean found = false;
            for (Method method : Completion.class.getDeclaredMethods()) {
                if (method.getName().equals(name)) {
                    found = true;
                    break;
                }
            }
            assertTrue(found, "missing public API method: " + name);
        }
    }

    @Test
    void singleStringSearchesReturnStringArrays() throws Exception {
        for (String name : new String[] {
                 "searchAllDictionaries", "searchFunctionsDictionary",
                 "searchCommandsDictionary", "searchMacrosDictionary",
                 "searchVariablesDictionary", "searchFilesDictionary",
                 "searchHandleGraphicsPropertiesDictionary", "searchMustBeDictionary"
             }) {
            Method m = Completion.class.getDeclaredMethod(name, String.class);
            assertEquals(String[].class, m.getReturnType(), name + " should return String[]");
            assertTrue(Modifier.isStatic(m.getModifiers()));
            assertTrue(Modifier.isPublic(m.getModifiers()));
        }
    }

    @Test
    void searchFieldsDictionaryTakesTwoStringsAndReturnsStringArray() throws Exception {
        Method m = Completion.class.getDeclaredMethod("searchFieldsDictionary",
                                                      String.class, String.class);
        assertEquals(String[].class, m.getReturnType());
        assertEquals(2, m.getParameterCount());
    }

    @Test
    void partLevelHelpersReturnAsingleString() throws Exception {
        for (String name : new String[] {"getPartLevel", "getFilePartLevel"}) {
            Method m = Completion.class.getDeclaredMethod(name, String.class);
            assertEquals(String.class, m.getReturnType(), name + " should return String");
        }
    }

    @Test
    void getCommonPartSignatureIsStringArrayAndInt() throws Exception {
        Method m = Completion.class.getDeclaredMethod("getCommonPart",
                                                      String[].class, int.class);
        assertEquals(String.class, m.getReturnType());
        Class<?>[] p = m.getParameterTypes();
        assertArrayEquals(new Class<?>[] {String[].class, int.class}, p);
    }

    @Test
    void completelineforjavaSignatureMatchesTheWindowCaller() throws Exception {
        // AbstractSciCompletionWindow.addCompletedWord() calls this with
        // (lineBeforeCaret, stringToAdd, isFile, lineAfterCaret).
        Method m = Completion.class.getDeclaredMethod("completelineforjava",
                     String.class, String.class, boolean.class, String.class);
        assertEquals(String.class, m.getReturnType());
        assertArrayEquals(new Class<?>[] {String.class, String.class, boolean.class, String.class},
                          m.getParameterTypes());
    }
}
