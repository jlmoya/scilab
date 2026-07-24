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

package org.scilab.modules.xcos.io.scicos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link ScicosFormatException} and its nested
 * exception hierarchy.
 *
 * <p><b>Why reflection only.</b> {@code ScicosFormatException} declares two
 * {@code private static final String} message templates initialised from
 * {@code org.scilab.modules.localization.Messages.gettext(...)}, whose
 * implementation is a {@code native} method (loaded from {@code scilocalization}).
 * <em>Any</em> instantiation of the class or of a subclass forces class
 * initialization, which runs those field initialisers and therefore the native
 * call — so behavioural construction is not hermetic and cannot run in the
 * default (non-native) test profile. These tests use only class literals and
 * reflective metadata queries, which load and link the classes but never
 * initialise them, so the native library is never touched. (Same strategy as
 * {@code org.scilab.modules.localization.MessagesTest}.) The full behavioural
 * suite belongs under the {@code -Pnative-tests} profile.
 */
public class ScicosFormatExceptionTest {

    @Test
    public void baseClassIsPublicAbstractAndACheckedException() {
        int mod = ScicosFormatException.class.getModifiers();
        assertTrue(Modifier.isPublic(mod), "ScicosFormatException must be public");
        assertTrue(Modifier.isAbstract(mod), "ScicosFormatException must be abstract");
        assertTrue(Exception.class.isAssignableFrom(ScicosFormatException.class),
                   "must extend Exception");
        assertFalse(RuntimeException.class.isAssignableFrom(ScicosFormatException.class),
                    "must be a checked exception, not a RuntimeException");
    }

    @Test
    public void baseClassDeclaresItsFourConstructorsWithExpectedAccess() throws Exception {
        Constructor<?>[] ctors = ScicosFormatException.class.getDeclaredConstructors();
        assertEquals(4, ctors.length, "expected exactly 4 declared constructors");

        Constructor<?> noArg = ScicosFormatException.class.getDeclaredConstructor();
        assertTrue(Modifier.isProtected(noArg.getModifiers()),
                   "the no-arg constructor is protected (subclass-only)");

        Constructor<?> msg = ScicosFormatException.class.getDeclaredConstructor(String.class);
        assertTrue(Modifier.isPublic(msg.getModifiers()));

        Constructor<?> msgCause =
                ScicosFormatException.class.getDeclaredConstructor(String.class, Throwable.class);
        assertTrue(Modifier.isPublic(msgCause.getModifiers()));

        Constructor<?> cause = ScicosFormatException.class.getDeclaredConstructor(Throwable.class);
        assertTrue(Modifier.isPublic(cause.getModifiers()));
    }

    @Test
    public void messageTemplateFieldsAreDeclaredPrivateStaticFinalStrings() throws Exception {
        // Metadata only — reading the values would initialise the class and hit native code.
        for (String name : new String[] {"UNABLE_TO_DECODE_INVALID_DATA",
                                         "UNABLE_TO_DECODE_INVALID_FIELD"}) {
            Field f = ScicosFormatException.class.getDeclaredField(name);
            int mod = f.getModifiers();
            assertTrue(Modifier.isPrivate(mod), name + " must be private");
            assertTrue(Modifier.isStatic(mod), name + " must be static");
            assertTrue(Modifier.isFinal(mod), name + " must be final");
            assertEquals(String.class, f.getType(), name + " must be a String");
        }
    }

    @Test
    public void declaresExactlyTheFourNestedExceptionTypes() {
        Set<String> names = new HashSet<>();
        for (Class<?> c : ScicosFormatException.class.getDeclaredClasses()) {
            names.add(c.getSimpleName());
        }
        assertEquals(new HashSet<>(Arrays.asList(
                         "WrongElementException", "WrongTypeException",
                         "WrongStructureException", "VersionMismatchException")),
                     names);
    }

    @Test
    public void everyNestedTypeIsAPublicStaticSubclass() {
        Class<?>[] nested = {
            ScicosFormatException.WrongElementException.class,
            ScicosFormatException.WrongTypeException.class,
            ScicosFormatException.WrongStructureException.class,
            ScicosFormatException.VersionMismatchException.class,
        };
        for (Class<?> c : nested) {
            int mod = c.getModifiers();
            assertTrue(Modifier.isPublic(mod), c.getSimpleName() + " must be public");
            assertTrue(Modifier.isStatic(mod), c.getSimpleName() + " must be static");
            assertSame(ScicosFormatException.class, c.getSuperclass(),
                       c.getSimpleName() + " must directly extend ScicosFormatException");
            assertTrue(Exception.class.isAssignableFrom(c),
                       c.getSimpleName() + " must be an Exception");
        }
    }

    @Test
    public void wrongElementExceptionHasASinglePublicNoArgConstructor() throws Exception {
        Class<?> c = ScicosFormatException.WrongElementException.class;
        assertEquals(1, c.getDeclaredConstructors().length);
        Constructor<?> ctor = c.getDeclaredConstructor();
        assertTrue(Modifier.isPublic(ctor.getModifiers()));
    }

    @Test
    public void wrongTypeExceptionDeclaresItsFourConstructors() throws Exception {
        Class<?> c = ScicosFormatException.WrongTypeException.class;
        assertEquals(4, c.getDeclaredConstructors().length);
        // no-arg
        assertTrue(Modifier.isPublic(c.getDeclaredConstructor().getModifiers()));
        // (message, cause)
        c.getDeclaredConstructor(String.class, Throwable.class);
        // (cause)
        c.getDeclaredConstructor(Throwable.class);
        // (fields, index) — the field-descriptor variant; List<String> erases to List, int stays int
        c.getDeclaredConstructor(List.class, int.class);
    }

    @Test
    public void wrongStructureExceptionDeclaresItsThreeConstructors() throws Exception {
        Class<?> c = ScicosFormatException.WrongStructureException.class;
        assertEquals(3, c.getDeclaredConstructors().length);
        c.getDeclaredConstructor();               // default
        c.getDeclaredConstructor(String.class);   // single field
        c.getDeclaredConstructor(List.class);     // field list
    }

    @Test
    public void versionMismatchExceptionHasStringCtorAccessorAndBackingField() throws Exception {
        Class<?> c = ScicosFormatException.VersionMismatchException.class;

        assertEquals(1, c.getDeclaredConstructors().length);
        Constructor<?> ctor = c.getDeclaredConstructor(String.class);
        assertTrue(Modifier.isPublic(ctor.getModifiers()));

        Method getter = c.getDeclaredMethod("getWrongVersion");
        assertTrue(Modifier.isPublic(getter.getModifiers()));
        assertEquals(String.class, getter.getReturnType());
        assertEquals(0, getter.getParameterCount());

        Field field = c.getDeclaredField("wrongVersion");
        int mod = field.getModifiers();
        assertTrue(Modifier.isPrivate(mod), "wrongVersion must be private");
        assertTrue(Modifier.isFinal(mod), "wrongVersion must be final");
        assertEquals(String.class, field.getType());
    }

    @Test
    public void allNestedTypesShareTheCommonSupertype() {
        assertTrue(ScicosFormatException.class.isAssignableFrom(
                       ScicosFormatException.WrongElementException.class));
        assertTrue(ScicosFormatException.class.isAssignableFrom(
                       ScicosFormatException.WrongTypeException.class));
        assertTrue(ScicosFormatException.class.isAssignableFrom(
                       ScicosFormatException.WrongStructureException.class));
        assertTrue(ScicosFormatException.class.isAssignableFrom(
                       ScicosFormatException.VersionMismatchException.class));
    }
}
