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
 * Hermetic unit tests for the SWIG-generated {@link LocaleToLCID} wrapper.
 *
 * <p>{@code LocaleToLCID} is a non-instantiable utility facade whose only method,
 * {@link LocaleToLCID#convert(String)}, delegates to the {@code native} method
 * {@code LocaleToLCIDJNI.convert}. This test exercises ONLY the pure-Java surface that
 * needs no {@code scilocalization} native library:
 * <ul>
 *   <li>the non-instantiability contract (the {@code protected} constructor throws), and</li>
 *   <li>the shape of the public API (name / modifiers / signature) via reflection.</li>
 * </ul>
 *
 * <p>Crucially, none of these tests <em>invoke</em> {@code convert}; a reflective
 * {@link Class#getDeclaredMethod} lookup does not trigger initialization of the
 * {@code LocaleToLCIDJNI} class, so no {@code System.loadLibrary} ever runs and the tests
 * stay hermetic. The native call path is deliberately out of scope.
 */
public class LocaleToLCIDTest {

    @Test
    public void constructorThrowsUnsupportedOperation() {
        // Same package => the protected constructor is directly reachable, and it
        // must reject every instantiation attempt (utility-class idiom).
        assertThrows(UnsupportedOperationException.class, () -> new LocaleToLCID());
    }

    @Test
    public void soleConstructorIsProtectedNoArgAndThrowsViaReflection() throws Exception {
        Constructor<?>[] ctors = LocaleToLCID.class.getDeclaredConstructors();
        assertEquals(1, ctors.length, "SWIG stub should declare exactly one constructor");

        Constructor<LocaleToLCID> ctor = LocaleToLCID.class.getDeclaredConstructor();
        assertTrue(Modifier.isProtected(ctor.getModifiers()), "constructor must be protected");

        ctor.setAccessible(true);
        InvocationTargetException wrapped =
                assertThrows(InvocationTargetException.class, ctor::newInstance);
        assertInstanceOf(UnsupportedOperationException.class, wrapped.getCause());
    }

    @Test
    public void classIsPublicConcreteAndNotFinal() {
        int mod = LocaleToLCID.class.getModifiers();
        assertTrue(Modifier.isPublic(mod), "facade is part of the public API");
        assertFalse(Modifier.isAbstract(mod), "facade is a concrete class");
        // Documents current shape: the class is NOT final, so the throwing constructor is
        // the sole guard against instantiation (a subclass's implicit super() would throw).
        assertFalse(Modifier.isFinal(mod));
    }

    @Test
    public void convertIsAPublicStaticNonNativeStringToStringDelegator() throws Exception {
        Method convert = LocaleToLCID.class.getDeclaredMethod("convert", String.class);

        int mod = convert.getModifiers();
        assertTrue(Modifier.isPublic(mod), "convert must be public");
        assertTrue(Modifier.isStatic(mod), "convert must be static");
        // The facade method itself is plain Java delegation; the native marker lives one
        // layer down in LocaleToLCIDJNI. This pins that architectural boundary.
        assertFalse(Modifier.isNative(mod), "the wrapper method must not itself be native");

        assertEquals(String.class, convert.getReturnType());
        assertArrayEquals(new Class<?>[] {String.class}, convert.getParameterTypes());
    }
}
