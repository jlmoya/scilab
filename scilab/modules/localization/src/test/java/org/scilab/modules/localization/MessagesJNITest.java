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
 * Hermetic unit tests for the SWIG-generated {@link MessagesJNI} JNI stub — the native
 * binding layer under the {@link Messages} gettext facade.
 *
 * <p>{@code MessagesJNI} declares the {@code native} methods {@code gettext(String)} and
 * {@code dgettext(String, String)} and, in a static initializer, calls
 * {@code System.loadLibrary("scilocalization")} (and, under a {@code testngTests} system
 * property on non-Windows, {@code System.loadLibrary("scilab")} — the whole engine). None of
 * those libraries are present in a plain unit-test JVM.
 *
 * <p><b>Why this is hermetic.</b> The tests use only reflective <em>lookup</em>
 * ({@code getDeclaredMethod}/{@code getDeclaredConstructor}/{@code getModifiers}/
 * {@code getReturnType}/{@code getParameterTypes}), which loads and links the class but does
 * NOT run its static initializer — so neither {@code loadLibrary} call ever fires. The tests
 * NEVER instantiate {@code MessagesJNI} nor invoke {@code gettext}/{@code dgettext} (either
 * would force initialization). What they pin is the JNI <em>ABI contract</em> the C symbols
 * {@code Java_org_scilab_modules_localization_MessagesJNI_gettext} /
 * {@code ..._dgettext} must match: name, arity, signature and
 * {@code native}/{@code static}/{@code final} modifiers.
 */
public class MessagesJNITest {

    @Test
    public void classIsPublicConcreteAndNotFinal() {
        int mod = MessagesJNI.class.getModifiers();
        assertTrue(Modifier.isPublic(mod));
        assertFalse(Modifier.isAbstract(mod), "the JNI stub is a concrete class");
        assertFalse(Modifier.isInterface(mod));
        assertFalse(Modifier.isFinal(mod), "SWIG emits a plain, non-final class");
    }

    @Test
    public void soleConstructorIsProtectedAndNoArg() {
        Constructor<?>[] ctors = MessagesJNI.class.getDeclaredConstructors();
        assertEquals(1, ctors.length, "SWIG stub should declare exactly one constructor");

        Constructor<?> ctor = ctors[0];
        assertTrue(Modifier.isProtected(ctor.getModifiers()), "constructor must be protected");
        assertEquals(0, ctor.getParameterCount(), "constructor takes no arguments");
        // Intentionally NOT invoked: MessagesJNI's <clinit> would run System.loadLibrary,
        // the native dependency these hermetic tests are designed to avoid.
    }

    @Test
    public void gettextIsThePublicStaticFinalNativeDomainlessLookup() throws Exception {
        Method gettext = MessagesJNI.class.getDeclaredMethod("gettext", String.class);

        int mod = gettext.getModifiers();
        assertTrue(Modifier.isPublic(mod));
        assertTrue(Modifier.isStatic(mod));
        assertTrue(Modifier.isFinal(mod), "SWIG native binding is final");
        assertTrue(Modifier.isNative(mod), "gettext is the JNI native binding");

        assertEquals(String.class, gettext.getReturnType());
        assertArrayEquals(new Class<?>[] {String.class}, gettext.getParameterTypes(),
                "gettext is the single-key, domain-less lookup");
    }

    @Test
    public void dgettextIsThePublicStaticFinalNativeDomainQualifiedLookup() throws Exception {
        Method dgettext =
                MessagesJNI.class.getDeclaredMethod("dgettext", String.class, String.class);

        int mod = dgettext.getModifiers();
        assertTrue(Modifier.isPublic(mod));
        assertTrue(Modifier.isStatic(mod));
        assertTrue(Modifier.isFinal(mod), "SWIG native binding is final");
        assertTrue(Modifier.isNative(mod), "dgettext is the JNI native binding");

        assertEquals(String.class, dgettext.getReturnType());
        // (domain, key) — the two-arg, domain-qualified lookup.
        assertArrayEquals(new Class<?>[] {String.class, String.class}, dgettext.getParameterTypes(),
                "dgettext takes a translation domain and a key");
    }

    @Test
    public void exactlyTwoRealMethodsGettextAndDgettextBothNative() {
        // The whole non-constructor surface is native: gettext + dgettext, nothing else.
        // Synthetic methods are skipped so a coverage agent's injected $jacocoInit() (or any
        // other compiler/tool synthetic) does not perturb this ABI-surface assertion.
        Set<String> realNames = new HashSet<>();
        for (Method m : MessagesJNI.class.getDeclaredMethods()) {
            if (m.isSynthetic()) {
                continue;
            }
            assertTrue(Modifier.isNative(m.getModifiers()),
                    m.getName() + " must be a native binding");
            realNames.add(m.getName());
        }
        assertEquals(Set.of("gettext", "dgettext"), realNames,
                "gettext and dgettext are the only real declared methods");
    }

    @Test
    public void theArityContractIsExact() {
        // gettext is domain-less; the two-arg variant is dgettext, not a gettext overload,
        // and dgettext has no single-arg form. Pins the API split at the JNI layer.
        assertThrows(NoSuchMethodException.class,
                () -> MessagesJNI.class.getDeclaredMethod("gettext", String.class, String.class));
        assertThrows(NoSuchMethodException.class,
                () -> MessagesJNI.class.getDeclaredMethod("dgettext", String.class));
    }
}
