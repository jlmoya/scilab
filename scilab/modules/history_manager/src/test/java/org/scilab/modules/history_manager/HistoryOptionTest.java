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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.junit.jupiter.api.Test;

import org.scilab.modules.commons.xml.XConfiguration.XConfAttribute;

/**
 * Hermetic unit tests for the private nested value class
 * {@code HistoryPrefs.HistoryOption}.
 *
 * <p>{@code HistoryOption} is the tiny data holder that {@code XConfiguration.get(...)} materializes
 * from the {@code <history-settings>} XML node and hands to {@link HistoryPrefs#configurationChanged}.
 * It is <em>pure Java</em>: no native code, no Swing, no running Scilab. It is exercised here only
 * through reflection because the class (and its {@code set} binder) are {@code private} to
 * {@code HistoryPrefs} — reading class metadata and driving a plain setter loads no shared library
 * and starts no engine, so the whole suite is hermetic.
 *
 * <p>The value of pinning this holder is that its shape is a silent contract with the reflective
 * binder in {@code org.scilab.modules.commons.xml.XConfiguration}:
 * <ul>
 *   <li>the binder instantiates it via a <b>no-arg</b> constructor
 *       ({@code type.getDeclaredConstructor(new Class[]{})}), so a static nested class with a
 *       zero-arg constructor is required — an inner (non-static) class would carry a synthetic
 *       enclosing-instance parameter and break instantiation;</li>
 *   <li>the binder rejects the whole type (returns {@code null}, so history preferences silently
 *       stop loading) unless the {@code @XConfAttribute} attribute-name array length exactly equals
 *       the annotated setter's parameter count;</li>
 *   <li>the XML attribute names {@code {"history-file", "history-lines", "enable"}} and their order
 *       are mapped positionally onto the setter parameters, so a rename or reorder here quietly
 *       misbinds every history preference.</li>
 * </ul>
 * These tests characterize exactly that shape so any future edit to the holder is a conscious one.
 */
public class HistoryOptionTest {

    private static final String[] EXPECTED_XML_ATTRIBUTES =
        {"history-file", "history-lines", "enable"};

    /** Locate the private nested {@code HistoryOption} by simple name, failing loudly if it moved. */
    private static Class<?> historyOptionClass() {
        for (Class<?> nested : HistoryPrefs.class.getDeclaredClasses()) {
            if ("HistoryOption".equals(nested.getSimpleName())) {
                return nested;
            }
        }
        fail("HistoryPrefs no longer declares a nested HistoryOption class");
        return null; // unreachable: fail() always throws
    }

    private static Object newHistoryOption() throws Exception {
        Constructor<?> ctor = historyOptionClass().getDeclaredConstructor();
        ctor.setAccessible(true);
        return ctor.newInstance();
    }

    private static Method setter() throws Exception {
        Method m = historyOptionClass()
                   .getDeclaredMethod("set", String.class, int.class, boolean.class);
        m.setAccessible(true);
        return m;
    }

    private static Object readField(Object instance, String name) throws Exception {
        Field f = historyOptionClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(instance);
    }

    // ------------------------------------------------------------------ structural contract

    @Test
    public void historyOptionIsAStaticNestedClass() {
        // The binder calls getDeclaredConstructor(new Class[]{}) — a *zero-arg* constructor. Only a
        // static nested class has one; a non-static inner class would have a synthetic HistoryPrefs
        // parameter and instantiation would throw. So "static" is load-bearing, not cosmetic.
        Class<?> opt = historyOptionClass();
        assertTrue(Modifier.isStatic(opt.getModifiers()),
                   "HistoryOption must be a static nested class for reflective no-arg instantiation");
    }

    @Test
    public void historyOptionIsPrivateToHistoryPrefs() {
        // Encapsulation characterization: the holder is an implementation detail of HistoryPrefs and
        // is reached only via reflection (both by XConfiguration and by this suite).
        Class<?> opt = historyOptionClass();
        assertTrue(Modifier.isPrivate(opt.getModifiers()),
                   "HistoryOption is expected to stay private to HistoryPrefs");
        assertEquals(HistoryPrefs.class, opt.getEnclosingClass());
    }

    @Test
    public void hasExactlyOnePrivateNoArgConstructor() {
        Constructor<?>[] ctors = historyOptionClass().getDeclaredConstructors();
        assertEquals(1, ctors.length, "expected a single (no-arg) constructor");
        Constructor<?> ctor = ctors[0];
        assertEquals(0, ctor.getParameterCount(),
                     "the binder instantiates via a zero-arg constructor");
        assertTrue(Modifier.isPrivate(ctor.getModifiers()));
    }

    @Test
    public void reflectiveNoArgInstantiationSucceeds() throws Exception {
        // Mirrors XConfiguration.get()'s constructor.setAccessible(true); constructor.newInstance().
        assertNotNull(newHistoryOption());
    }

    // ------------------------------------------------------------------ annotation / binding contract

    @Test
    public void classCarriesXConfAttributeAndIsNotStaticBinding() {
        // XConfiguration.get() does ((XConfAttribute) type.getAnnotation(...)).isStatic(); a missing
        // class annotation would NPE there. isStatic()==false selects the "instantiate one object per
        // node" path — which is exactly how HistoryPrefs consumes it (indexing element [0]).
        XConfAttribute classAnn = historyOptionClass().getAnnotation(XConfAttribute.class);
        assertNotNull(classAnn, "HistoryOption must be annotated @XConfAttribute at class level");
        assertFalse(classAnn.isStatic(),
                    "HistoryOption is an instance-per-node binding, so isStatic() must be false");
    }

    @Test
    public void setterCarriesTheThreeXmlAttributeNamesInOrder() throws Exception {
        // THE binding contract: these XML attribute names, in this order, are mapped positionally
        // onto the setter parameters. A rename/reorder silently misbinds every history preference.
        XConfAttribute ann = setter().getAnnotation(XConfAttribute.class);
        assertNotNull(ann, "the set(...) binder must be annotated @XConfAttribute");
        assertArrayEquals(EXPECTED_XML_ATTRIBUTES, ann.attributes());
    }

    @Test
    public void annotatedAttributeCountEqualsSetterArity() throws Exception {
        // Exactly the invariant XConfiguration.get() enforces (attributes.length ==
        // getParameterTypes().length); when it fails the binder returns null and history
        // preferences silently stop loading. Pin it as a named guard.
        Method set = setter();
        XConfAttribute ann = set.getAnnotation(XConfAttribute.class);
        assertEquals(set.getParameterCount(), ann.attributes().length,
                     "attribute-name count must match the setter arity or the binder returns null");
        assertEquals(3, ann.attributes().length);
    }

    @Test
    public void setterParameterTypesAreStringIntBoolean() throws Exception {
        // The parameter types must line up with the parsers XConfiguration registers (String/int/
        // boolean all have one) and with the field types they populate. Order matters: it is the
        // positional map from {history-file, history-lines, enable}.
        assertArrayEquals(new Class<?>[] {String.class, int.class, boolean.class},
                          setter().getParameterTypes());
        assertEquals(void.class, setter().getReturnType());
    }

    // ------------------------------------------------------------------ field surface

    @Test
    public void theThreeBoundFieldsArePublicInstanceFieldsWithExpectedTypes() throws Exception {
        // HistoryPrefs.configurationChanged reads opt.historyFile / opt.historyLines / opt.enable
        // directly, so these must remain public, non-static instance fields of the right type.
        assertPublicInstanceField("historyFile", String.class);
        assertPublicInstanceField("historyLines", int.class);
        assertPublicInstanceField("enable", boolean.class);
    }

    private static void assertPublicInstanceField(String name, Class<?> type) throws Exception {
        Field f = historyOptionClass().getDeclaredField(name);
        assertEquals(type, f.getType(), "field " + name + " has an unexpected type");
        assertTrue(Modifier.isPublic(f.getModifiers()), "field " + name + " must be public");
        assertFalse(Modifier.isStatic(f.getModifiers()), "field " + name + " must be an instance field");
    }

    // ------------------------------------------------------------------ behavior

    @Test
    public void freshInstanceHasJavaDefaultFieldValues() throws Exception {
        // A just-constructed holder (before set() runs) carries the JVM defaults.
        Object opt = newHistoryOption();
        assertNull(readField(opt, "historyFile"));
        assertEquals(Integer.valueOf(0), readField(opt, "historyLines"));
        assertEquals(Boolean.FALSE, readField(opt, "enable"));
    }

    @Test
    public void setAssignsEachParameterToItsLikeNamedField() throws Exception {
        // Core behavior + anti-swap guard: distinct sentinel values must each land in the matching
        // public field, proving set() maps param -> field without transposition.
        Object opt = newHistoryOption();
        setter().invoke(opt, "scilab.hist", 250, true);
        assertEquals("scilab.hist", readField(opt, "historyFile"));
        assertEquals(Integer.valueOf(250), readField(opt, "historyLines"));
        assertEquals(Boolean.TRUE, readField(opt, "enable"));
    }

    @Test
    public void setIsAPlainMutatorThatOverwritesPreviousValues() throws Exception {
        // The holder is reused/rebound, so a second set() must fully overwrite the first.
        Object opt = newHistoryOption();
        setter().invoke(opt, "first.hist", 10, true);
        setter().invoke(opt, "second.hist", 9999, false);
        assertEquals("second.hist", readField(opt, "historyFile"));
        assertEquals(Integer.valueOf(9999), readField(opt, "historyLines"));
        assertEquals(Boolean.FALSE, readField(opt, "enable"));
    }

    @Test
    public void setPerformsNoValidationAndStoresLineCountsVerbatim() throws Exception {
        // Characterization: HistoryOption is a dumb carrier. It does NOT sanity-check historyLines;
        // zero and negative counts are stored as-is. Any bounds enforcement lives downstream in
        // HistoryManagement.setSizeMaxScilabHistory, not here. Pinned so the holder stays "dumb".
        Object zero = newHistoryOption();
        setter().invoke(zero, "", 0, false);
        assertEquals(Integer.valueOf(0), readField(zero, "historyLines"));
        assertEquals("", readField(zero, "historyFile"),
                     "an empty filename is stored verbatim, not coerced to null");

        Object negative = newHistoryOption();
        setter().invoke(negative, "x", -5, true);
        assertEquals(Integer.valueOf(-5), readField(negative, "historyLines"),
                     "a negative line count is stored verbatim (no clamping in the holder)");
    }

    @Test
    public void setAcceptsNullFilenameWithoutThrowing() throws Exception {
        // The String parameter is unconstrained; a null filename is simply stored. (Downstream
        // consumers, not the holder, decide what a null history file means.)
        Object opt = newHistoryOption();
        setter().invoke(opt, new Object[] {null, 42, true});
        assertNull(readField(opt, "historyFile"));
        assertEquals(Integer.valueOf(42), readField(opt, "historyLines"));
        assertEquals(Boolean.TRUE, readField(opt, "enable"));
    }
}
