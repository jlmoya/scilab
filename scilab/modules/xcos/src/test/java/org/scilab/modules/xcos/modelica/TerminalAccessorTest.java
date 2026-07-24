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

package org.scilab.modules.xcos.modelica;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.EventListener;
import java.util.EventObject;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import org.scilab.modules.xcos.modelica.model.Terminal;

/**
 * Hermetic tests for the {@link TerminalAccessor} enum and its nested support
 * types ({@code ChangeEvent}, {@code ChangeListener}, {@code ChangeSupport}).
 *
 * <p><b>Why mostly reflection.</b> Every enum constant is declared with a
 * message name pulled from {@code ModelicaMessages}, whose
 * {@code public static final String} fields are initialised from
 * {@code org.scilab.modules.localization.Messages.gettext(...)} — a delegator to
 * a {@code native} method. <em>Any</em> active use of {@code TerminalAccessor}
 * (calling {@code values()}, {@code getData(..)}, reading a constant, …) forces
 * the enum's {@code <clinit>}, which constructs the constants, which triggers
 * {@code ModelicaMessages} initialisation, which calls native code — an
 * {@code UnsatisfiedLinkError} in the default (non-native) test profile. The
 * behavioural surface therefore belongs to {@code -Pnative-tests}; here we use
 * only class-literal / metadata queries, which <em>load and link</em> the enum
 * but never <em>initialise</em> it. (Same strategy as
 * {@code ScicosFormatExceptionTest} and {@code MessagesTest}.)
 *
 * <p><b>What is still exercised behaviourally.</b> The static nested helper
 * {@link TerminalAccessor.ChangeSupport} is an ordinary {@code static} class: it
 * carries no reference to {@code ModelicaMessages} and, per JLS 12.4, creating
 * an instance of a static nested class does <em>not</em> initialise its
 * enclosing type. Its listener-routing logic is therefore reachable hermetically
 * by keying with the {@code null} sentinel (a {@code HashMap} accepts null
 * keys), and those tests below run real add / fire / remove flows. The
 * {@code ChangeEvent} null-source rejection is likewise reachable without
 * initialising the enum.
 */
public class TerminalAccessorTest {

    /* ------------------------------------------------------------------ */
    /* Enum shape (metadata only — never initialises the enum)            */
    /* ------------------------------------------------------------------ */

    @Test
    public void terminalAccessorIsANonFinalPublicEnum() {
        int mod = TerminalAccessor.class.getModifiers();
        assertTrue(TerminalAccessor.class.isEnum(), "must be an enum");
        assertTrue(Modifier.isPublic(mod), "must be public");
        // It carries constant-specific class bodies (abstract getData/setData
        // impls), so the enum type is NOT final.
        assertFalse(Modifier.isFinal(mod), "an enum with constant bodies is not final");
        assertSame(Enum.class, TerminalAccessor.class.getSuperclass());
    }

    @Test
    public void declaresExactlyTheEightExpectedConstants() {
        Set<String> constants = enumConstantNames();
        assertEquals(new HashSet<>(Arrays.asList(
                         "NAME", "ID", "KIND", "FIXED", "INITIAL", "WEIGHT",
                         "COMMENT", "SELECTED")),
                     constants);
        assertEquals(8, constants.size());
    }

    @Test
    public void theCommentedOutMaxMinNominalColumnsAreNotConstants() {
        // MAX / MIN / NOMINAL are commented out in the source; document that the
        // current model only exposes the eight columns above.
        Set<String> constants = enumConstantNames();
        assertFalse(constants.contains("MAX"));
        assertFalse(constants.contains("MIN"));
        assertFalse(constants.contains("NOMINAL"));
    }

    /** Collect the enum-constant field names without reading their values. */
    private static Set<String> enumConstantNames() {
        Set<String> names = new HashSet<>();
        for (Field f : TerminalAccessor.class.getDeclaredFields()) {
            if (f.isEnumConstant()) {
                names.add(f.getName());
            }
        }
        return names;
    }

    @Test
    public void declaresPreciselyItsThreeNestedSupportTypes() {
        Set<String> nested = new HashSet<>();
        for (Class<?> c : TerminalAccessor.class.getDeclaredClasses()) {
            nested.add(c.getSimpleName());
        }
        assertEquals(new HashSet<>(Arrays.asList(
                         "ChangeEvent", "ChangeListener", "ChangeSupport")),
                     nested);
    }

    /* ------------------------------------------------------------------ */
    /* Accessor method / field metadata                                   */
    /* ------------------------------------------------------------------ */

    @Test
    public void staticGetDataIsAPublicStaticTwoArgLookupReturningObject() throws Exception {
        Method m = TerminalAccessor.class.getDeclaredMethod(
                       "getData", TerminalAccessor.class, Terminal.class);
        int mod = m.getModifiers();
        assertTrue(Modifier.isPublic(mod));
        assertTrue(Modifier.isStatic(mod));
        // generic <T> erases to Object
        assertSame(Object.class, m.getReturnType());
    }

    @Test
    public void instanceGetDataIsProtectedAbstractAndSingleArg() throws Exception {
        Method m = TerminalAccessor.class.getDeclaredMethod("getData", Terminal.class);
        int mod = m.getModifiers();
        assertFalse(Modifier.isStatic(mod));
        assertTrue(Modifier.isProtected(mod), "the per-constant reader is protected");
        assertTrue(Modifier.isAbstract(mod), "each constant supplies its own body");
        assertSame(Object.class, m.getReturnType());
    }

    @Test
    public void setDataIsPublicAbstractTakingObjectAndTerminalReturningVoid() throws Exception {
        Method m = TerminalAccessor.class.getDeclaredMethod(
                       "setData", Object.class, Terminal.class);
        int mod = m.getModifiers();
        assertTrue(Modifier.isPublic(mod));
        assertTrue(Modifier.isAbstract(mod));
        assertSame(void.class, m.getReturnType());
    }

    @Test
    public void nameKlassAndEditableGettersHaveTheExpectedShapes() throws Exception {
        Method name = TerminalAccessor.class.getDeclaredMethod("getName");
        assertTrue(Modifier.isPublic(name.getModifiers()));
        assertSame(String.class, name.getReturnType());
        assertEquals(0, name.getParameterCount());

        Method klass = TerminalAccessor.class.getDeclaredMethod("getKlass");
        assertTrue(Modifier.isPublic(klass.getModifiers()));
        assertSame(Class.class, klass.getReturnType());

        Method editable = TerminalAccessor.class.getDeclaredMethod("isEditable");
        assertTrue(Modifier.isPublic(editable.getModifiers()));
        assertSame(boolean.class, editable.getReturnType());
    }

    @Test
    public void addChangeListenerInstanceMethodTakesTheNestedChangeListener() throws Exception {
        Method m = TerminalAccessor.class.getDeclaredMethod(
                       "addChangeListener", TerminalAccessor.ChangeListener.class);
        assertTrue(Modifier.isPublic(m.getModifiers()));
        assertFalse(Modifier.isStatic(m.getModifiers()));
        assertSame(void.class, m.getReturnType());
    }

    @Test
    public void firePropertyChangeIsProtectedAndTakesTerminalPlusOldAndNewValues() throws Exception {
        Method m = TerminalAccessor.class.getDeclaredMethod(
                       "firePropertyChange", Terminal.class, Object.class, Object.class);
        assertTrue(Modifier.isProtected(m.getModifiers()));
        assertSame(void.class, m.getReturnType());
    }

    @Test
    public void perConstantStateFieldsAreAllPrivateInstanceFields() throws Exception {
        Field name = TerminalAccessor.class.getDeclaredField("name");
        assertTrue(Modifier.isPrivate(name.getModifiers()));
        assertFalse(Modifier.isStatic(name.getModifiers()));
        assertSame(String.class, name.getType());

        Field klass = TerminalAccessor.class.getDeclaredField("klass");
        assertTrue(Modifier.isPrivate(klass.getModifiers()));
        assertSame(Class.class, klass.getType());

        Field editable = TerminalAccessor.class.getDeclaredField("editable");
        assertTrue(Modifier.isPrivate(editable.getModifiers()));
        assertSame(boolean.class, editable.getType());
    }

    @Test
    public void everyDeclaredConstructorIsPrivate() {
        Constructor<?>[] ctors = TerminalAccessor.class.getDeclaredConstructors();
        assertTrue(ctors.length >= 1);
        for (Constructor<?> c : ctors) {
            assertTrue(Modifier.isPrivate(c.getModifiers()),
                       "enum constructors must be private");
        }
    }

    /* ------------------------------------------------------------------ */
    /* Nested ChangeEvent                                                 */
    /* ------------------------------------------------------------------ */

    @Test
    public void changeEventIsAPublicStaticSubclassOfEventObject() {
        Class<?> c = TerminalAccessor.ChangeEvent.class;
        int mod = c.getModifiers();
        assertTrue(Modifier.isPublic(mod));
        assertTrue(Modifier.isStatic(mod));
        assertFalse(Modifier.isInterface(mod));
        assertSame(EventObject.class, c.getSuperclass());
    }

    @Test
    public void changeEventConstructorAndAccessorsHaveTheExpectedShapes() throws Exception {
        Class<?> c = TerminalAccessor.ChangeEvent.class;

        Constructor<?> ctor = c.getDeclaredConstructor(
                                  TerminalAccessor.class, Terminal.class, Object.class, Object.class);
        assertTrue(Modifier.isPublic(ctor.getModifiers()));

        assertSame(Terminal.class, c.getDeclaredMethod("getTerminal").getReturnType());
        assertSame(Object.class, c.getDeclaredMethod("getOldData").getReturnType());
        assertSame(Object.class, c.getDeclaredMethod("getNewData").getReturnType());

        for (String fieldName : new String[] {"terminal", "oldData", "newData"}) {
            Field f = c.getDeclaredField(fieldName);
            int fmod = f.getModifiers();
            assertTrue(Modifier.isPrivate(fmod), fieldName + " must be private");
            assertTrue(Modifier.isFinal(fmod), fieldName + " must be final");
        }
    }

    @Test
    public void changeEventRejectsANullSource() {
        // ChangeEvent extends EventObject, whose constructor forbids a null
        // source. Constructing the *static nested* ChangeEvent does not
        // initialise the TerminalAccessor enum, so this stays hermetic.
        assertThrows(IllegalArgumentException.class,
            () -> new TerminalAccessor.ChangeEvent(null, null, null, null));
    }

    /* ------------------------------------------------------------------ */
    /* Nested ChangeListener                                              */
    /* ------------------------------------------------------------------ */

    @Test
    public void changeListenerIsAPublicInterfaceExtendingEventListener() throws Exception {
        Class<?> c = TerminalAccessor.ChangeListener.class;
        assertTrue(c.isInterface());
        assertTrue(Modifier.isPublic(c.getModifiers()));
        assertTrue(EventListener.class.isAssignableFrom(c),
                   "ChangeListener must be an EventListener");

        Method change = c.getDeclaredMethod("change", TerminalAccessor.ChangeEvent.class);
        assertSame(void.class, change.getReturnType());
    }

    /* ------------------------------------------------------------------ */
    /* Nested ChangeSupport — metadata                                    */
    /* ------------------------------------------------------------------ */

    @Test
    public void changeSupportIsAPublicStaticClassWithTheExpectedApi() throws Exception {
        Class<?> c = TerminalAccessor.ChangeSupport.class;
        int mod = c.getModifiers();
        assertTrue(Modifier.isPublic(mod));
        assertTrue(Modifier.isStatic(mod));
        assertFalse(Modifier.isInterface(mod));

        assertTrue(Modifier.isPublic(c.getDeclaredConstructor().getModifiers()));

        Method add = c.getDeclaredMethod(
                         "addChangeListener", TerminalAccessor.class, TerminalAccessor.ChangeListener.class);
        assertTrue(Modifier.isPublic(add.getModifiers()));

        Method remove = c.getDeclaredMethod(
                            "removeChangeListener", TerminalAccessor.class, TerminalAccessor.ChangeListener.class);
        assertTrue(Modifier.isPublic(remove.getModifiers()));

        Method fire = c.getDeclaredMethod(
                          "fireChangeEvent", TerminalAccessor.class, TerminalAccessor.ChangeEvent.class);
        assertTrue(Modifier.isProtected(fire.getModifiers()), "fireChangeEvent is protected");

        Field listeners = c.getDeclaredField("listeners");
        int fmod = listeners.getModifiers();
        assertTrue(Modifier.isPrivate(fmod));
        assertTrue(Modifier.isFinal(fmod));
        assertSame(java.util.Map.class, listeners.getType());
    }

    /* ------------------------------------------------------------------ */
    /* Nested ChangeSupport — real behaviour (hermetic, null-keyed)       */
    /*                                                                    */
    /* Creating a ChangeSupport instance does not initialise the enum     */
    /* (JLS 12.4); we route on the null sentinel key so no enum constant  */
    /* is ever touched.                                                   */
    /* ------------------------------------------------------------------ */

    @Test
    public void firingWithNothingRegisteredIsANoOp() {
        TerminalAccessor.ChangeSupport support = new TerminalAccessor.ChangeSupport();
        assertDoesNotThrow(() -> support.fireChangeEvent(null, null));
    }

    @Test
    public void aRegisteredListenerReceivesTheFire() {
        TerminalAccessor.ChangeSupport support = new TerminalAccessor.ChangeSupport();
        int[] hits = {0};
        support.addChangeListener(null, ev -> hits[0]++);

        support.fireChangeEvent(null, null);

        assertEquals(1, hits[0]);
    }

    @Test
    public void allListenersRegisteredUnderAKeyAreNotified() {
        TerminalAccessor.ChangeSupport support = new TerminalAccessor.ChangeSupport();
        int[] a = {0};
        int[] b = {0};
        support.addChangeListener(null, ev -> a[0]++);
        support.addChangeListener(null, ev -> b[0]++);

        support.fireChangeEvent(null, null);

        assertEquals(1, a[0]);
        assertEquals(1, b[0]);
    }

    @Test
    public void aListenerAddedTwiceIsNotifiedOncePerRegistration() {
        TerminalAccessor.ChangeSupport support = new TerminalAccessor.ChangeSupport();
        int[] hits = {0};
        TerminalAccessor.ChangeListener l = ev -> hits[0]++;

        support.addChangeListener(null, l);
        support.addChangeListener(null, l);
        support.fireChangeEvent(null, null);

        assertEquals(2, hits[0]);
    }

    @Test
    public void aRemovedListenerIsNoLongerNotified() {
        TerminalAccessor.ChangeSupport support = new TerminalAccessor.ChangeSupport();
        int[] hits = {0};
        TerminalAccessor.ChangeListener l = ev -> hits[0]++;

        support.addChangeListener(null, l);
        support.fireChangeEvent(null, null);
        assertEquals(1, hits[0]);

        support.removeChangeListener(null, l);
        support.fireChangeEvent(null, null);
        assertEquals(1, hits[0], "removed listener must not fire again");
    }

    @Test
    public void removingFromAKeyThatHasNoRegistrationsIsHarmless() {
        // listeners.get(field) is null for an unknown key; removeChangeListener
        // guards that with a null check, so this must not throw.
        TerminalAccessor.ChangeSupport support = new TerminalAccessor.ChangeSupport();
        assertDoesNotThrow(() -> support.removeChangeListener(null, ev -> { }));
    }
}
