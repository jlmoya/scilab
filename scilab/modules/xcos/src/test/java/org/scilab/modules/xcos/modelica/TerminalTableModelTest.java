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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.event.TableModelEvent;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;

import org.junit.jupiter.api.Test;

/**
 * Hermetic tests for {@link TerminalTableModel}, the Swing
 * {@code AbstractTableModel} that renders a {@code List<Terminal>} through the
 * {@link TerminalAccessor} columns.
 *
 * <p><b>Why mostly reflection.</b> {@code TerminalTableModel}'s constructor and
 * every table method call {@code TerminalAccessor.values()}, which forces the
 * enum's initialisation and therefore {@code ModelicaMessages ->
 * Messages.gettext(..) -> native}, an {@code UnsatisfiedLinkError} in the
 * default (non-native) test profile (see {@code TerminalAccessorTest} for the
 * full chain). Constructing a model, and thus any of its row/column behaviour,
 * belongs to {@code -Pnative-tests}. These tests use class-literal / metadata
 * queries only, which load and link the class without initialising it, plus one
 * genuinely-hermetic behavioural check on the nested event.
 *
 * <p>Pinned here: the {@code final} + {@code TableModel} contract, the
 * getter/setter and seven overridden-method signatures, the private
 * {@code terminals} backing field, the nested {@code TerminalTableModelEvent}
 * (its two constructors, {@code beforeCommit} field, before/after accessors and
 * the {@code EventObject} null-source rejection), and the private
 * {@code ModelChangeListener} adapter shape.
 */
public class TerminalTableModelTest {

    /* ------------------------------------------------------------------ */
    /* Class shape                                                        */
    /* ------------------------------------------------------------------ */

    @Test
    public void terminalTableModelIsAPublicFinalTableModel() {
        int mod = TerminalTableModel.class.getModifiers();
        assertTrue(Modifier.isPublic(mod));
        assertTrue(Modifier.isFinal(mod));
        assertSame(AbstractTableModel.class, TerminalTableModel.class.getSuperclass());
        assertTrue(TableModel.class.isAssignableFrom(TerminalTableModel.class),
                   "must implement TableModel via AbstractTableModel");
        assertTrue(Serializable.class.isAssignableFrom(TerminalTableModel.class));
    }

    @Test
    public void hasAPublicNoArgConstructor() throws Exception {
        Constructor<?> ctor = TerminalTableModel.class.getDeclaredConstructor();
        assertTrue(Modifier.isPublic(ctor.getModifiers()));
    }

    @Test
    public void terminalsBackingFieldIsAPrivateList() throws Exception {
        Field f = TerminalTableModel.class.getDeclaredField("terminals");
        assertTrue(Modifier.isPrivate(f.getModifiers()));
        assertFalse(Modifier.isStatic(f.getModifiers()));
        assertSame(List.class, f.getType());
    }

    @Test
    public void getAndSetTerminalsHaveTheExpectedShapes() throws Exception {
        Method get = TerminalTableModel.class.getDeclaredMethod("getTerminals");
        assertTrue(Modifier.isPublic(get.getModifiers()));
        assertSame(List.class, get.getReturnType());
        assertEquals(0, get.getParameterCount());

        Method set = TerminalTableModel.class.getDeclaredMethod("setTerminals", List.class);
        assertTrue(Modifier.isPublic(set.getModifiers()));
        assertSame(void.class, set.getReturnType());
    }

    @Test
    public void overridesTheSevenTableModelMethodsWithExpectedSignatures() throws Exception {
        assertSame(int.class,
                   TerminalTableModel.class.getDeclaredMethod("getRowCount").getReturnType());
        assertSame(int.class,
                   TerminalTableModel.class.getDeclaredMethod("getColumnCount").getReturnType());
        assertSame(String.class,
                   TerminalTableModel.class.getDeclaredMethod("getColumnName", int.class).getReturnType());
        assertSame(Class.class,
                   TerminalTableModel.class.getDeclaredMethod("getColumnClass", int.class).getReturnType());
        assertSame(boolean.class,
                   TerminalTableModel.class.getDeclaredMethod("isCellEditable", int.class, int.class)
                   .getReturnType());
        assertSame(Object.class,
                   TerminalTableModel.class.getDeclaredMethod("getValueAt", int.class, int.class)
                   .getReturnType());
        Method setValueAt = TerminalTableModel.class.getDeclaredMethod(
                                "setValueAt", Object.class, int.class, int.class);
        assertSame(void.class, setValueAt.getReturnType());
        assertTrue(Modifier.isPublic(setValueAt.getModifiers()));
    }

    @Test
    public void declaresExactlyItsTwoNestedTypes() {
        Set<String> nested = new HashSet<>();
        for (Class<?> c : TerminalTableModel.class.getDeclaredClasses()) {
            nested.add(c.getSimpleName());
        }
        assertEquals(new HashSet<>(Arrays.asList(
                         "TerminalTableModelEvent", "ModelChangeListener")),
                     nested);
    }

    /* ------------------------------------------------------------------ */
    /* Nested TerminalTableModelEvent                                     */
    /* ------------------------------------------------------------------ */

    @Test
    public void eventIsAPublicStaticFinalSubclassOfTableModelEvent() {
        Class<?> c = TerminalTableModel.TerminalTableModelEvent.class;
        int mod = c.getModifiers();
        assertTrue(Modifier.isPublic(mod));
        assertTrue(Modifier.isStatic(mod));
        assertTrue(Modifier.isFinal(mod));
        assertSame(TableModelEvent.class, c.getSuperclass());
    }

    @Test
    public void eventDeclaresItsTwoConstructors() throws Exception {
        Class<?> c = TerminalTableModel.TerminalTableModelEvent.class;

        // full form: (source, firstRow, lastRow, column, type, beforeCommit)
        Constructor<?> full = c.getDeclaredConstructor(
                                  TerminalTableModel.class, int.class, int.class, int.class, int.class,
                                  boolean.class);
        assertTrue(Modifier.isPublic(full.getModifiers()));

        // shorthand: (source, rowIndex, columnIndex, beforeCommit)
        Constructor<?> shorthand = c.getDeclaredConstructor(
                                       TerminalTableModel.class, int.class, int.class, boolean.class);
        assertTrue(Modifier.isPublic(shorthand.getModifiers()));
    }

    @Test
    public void eventBeforeCommitFieldIsPrivateFinalBoolean() throws Exception {
        Field f = TerminalTableModel.TerminalTableModelEvent.class.getDeclaredField("beforeCommit");
        int mod = f.getModifiers();
        assertTrue(Modifier.isPrivate(mod));
        assertTrue(Modifier.isFinal(mod));
        assertSame(boolean.class, f.getType());
    }

    @Test
    public void eventBeforeAndAfterCommitAccessorsReturnBoolean() throws Exception {
        Class<?> c = TerminalTableModel.TerminalTableModelEvent.class;

        Method before = c.getDeclaredMethod("isBeforeCommit");
        assertTrue(Modifier.isPublic(before.getModifiers()));
        assertSame(boolean.class, before.getReturnType());
        assertEquals(0, before.getParameterCount());

        Method after = c.getDeclaredMethod("isAfterCommit");
        assertTrue(Modifier.isPublic(after.getModifiers()));
        assertSame(boolean.class, after.getReturnType());
    }

    @Test
    public void eventConstructorsRejectANullSource() {
        // Both constructors chain to EventObject, which forbids a null source.
        // Constructing the *static nested* event initialises only the event
        // class; it never runs `new TerminalTableModel()` and so never
        // initialises the TerminalAccessor enum -> this stays hermetic.
        assertThrows(IllegalArgumentException.class,
            () -> new TerminalTableModel.TerminalTableModelEvent(
                          (TerminalTableModel) null, 0, 0, false));
        assertThrows(IllegalArgumentException.class,
            () -> new TerminalTableModel.TerminalTableModelEvent(
                          (TerminalTableModel) null, 0, 0, 0, TableModelEvent.UPDATE, true));
    }

    /* ------------------------------------------------------------------ */
    /* Nested ModelChangeListener (private adapter)                       */
    /* ------------------------------------------------------------------ */

    @Test
    public void modelChangeListenerIsAPrivateStaticTerminalAccessorChangeListener() throws Exception {
        Class<?> mcl = nestedClass("ModelChangeListener");
        assertNotNull(mcl, "ModelChangeListener must exist");

        int mod = mcl.getModifiers();
        assertTrue(Modifier.isPrivate(mod), "adapter is an implementation detail");
        assertTrue(Modifier.isStatic(mod));
        assertTrue(TerminalAccessor.ChangeListener.class.isAssignableFrom(mcl),
                   "must implement TerminalAccessor.ChangeListener");

        Method change = mcl.getDeclaredMethod("change", TerminalAccessor.ChangeEvent.class);
        assertSame(void.class, change.getReturnType());

        // it wires itself to a single owning model
        Constructor<?> ctor = mcl.getDeclaredConstructor(TerminalTableModel.class);
        assertNotNull(ctor);
    }

    private static Class<?> nestedClass(String simpleName) {
        for (Class<?> c : TerminalTableModel.class.getDeclaredClasses()) {
            if (c.getSimpleName().equals(simpleName)) {
                return c;
            }
        }
        return null;
    }
}
