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

package org.scilab.modules.graph.actions.base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.event.ActionEvent;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

import javax.swing.Action;

import org.junit.jupiter.api.Test;
import org.scilab.modules.graph.ScilabGraph;

import com.mxgraph.model.mxCell;
import com.mxgraph.util.mxEventObject;
import com.mxgraph.util.mxEventSource.mxIEventListener;
import com.mxgraph.view.mxGraph;
import com.mxgraph.view.mxGraphSelectionModel;

/**
 * Hermetic unit tests for {@link VertexSelectionDependantAction} and its private
 * {@code VertexSelectionDependantConstraint}.
 *
 * The constraint enables the action iff the current selection contains at least
 * one {@link mxCell} that is a vertex. Its {@code install(...)} needs a live
 * Swing {@link ScilabGraph}, so it is never called here. Instead the private
 * nested constraint is instantiated reflectively (its no-arg constructor is
 * accessible once {@code setAccessible(true)} is applied), the constrained
 * {@link DefaultAction} is injected into the inherited {@code action} field, and
 * {@code invoke(...)} - visible through the {@link mxIEventListener} supertype -
 * is driven with a {@link mxGraphSelectionModel} whose {@code getCells()} is
 * overridden. Nothing touches the AWT event loop.
 */
public class VertexSelectionDependantActionTest {

    /** Public concrete subclass so the outer constructor's null path runs. */
    public static class VProbe extends VertexSelectionDependantAction {
        public static final String NAME = "Vertex probe";
        public static final String SMALL_ICON = "";
        public static final int MNEMONIC_KEY = 0;
        public static final int ACCELERATOR_KEY = 0;

        public VProbe(ScilabGraph graph) {
            super(graph);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            // no-op
        }
    }

    /** Public so DefaultAction's reflective installProperties() can read NAME. */
    public static class Probe extends DefaultAction {
        public static final String NAME = "Probe";
        public static final String SMALL_ICON = "";
        public static final int MNEMONIC_KEY = 0;
        public static final int ACCELERATOR_KEY = 0;

        public Probe() {
            super((ScilabGraph) null);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            // no-op
        }
    }

    /** Reflectively build the private VertexSelectionDependantConstraint. */
    private static ActionConstraint newConstraint() throws Exception {
        Class<?> nested = Class.forName(
                "org.scilab.modules.graph.actions.base.VertexSelectionDependantAction$VertexSelectionDependantConstraint");
        Constructor<?> ctor = nested.getDeclaredConstructor();
        ctor.setAccessible(true);
        return (ActionConstraint) ctor.newInstance();
    }

    /** Inject the constrained action (no public setter exists). */
    private static void injectAction(ActionConstraint c, DefaultAction a) throws Exception {
        Field f = ActionConstraint.class.getDeclaredField("action");
        f.setAccessible(true);
        f.set(c, a);
    }

    /** A selection model whose reported selection is fully controlled. */
    private static mxGraphSelectionModel selectionReturning(final Object[] controlled) {
        // NB: the parameter must not be named "cells" - mxGraphSelectionModel
        // has an inherited field of that name which would shadow it.
        return new mxGraphSelectionModel(new mxGraph()) {
            @Override
            public Object[] getCells() {
                return controlled;
            }
        };
    }

    private static mxCell vertex() {
        mxCell c = new mxCell();
        c.setVertex(true);
        return c;
    }

    private static mxCell edge() {
        mxCell c = new mxCell();
        c.setEdge(true);
        return c;
    }

    @Test
    public void nullGraphConstructorSkipsConstraintInstallationAndInstallsProperties() {
        // With a null graph the outer constructor takes its guarded branch and
        // never builds the constraint, yet DefaultAction still installs NAME.
        VProbe action = new VProbe(null);
        assertNotNull(action);
        assertEquals("Vertex probe", action.getValue(Action.NAME));
    }

    @Test
    public void enablesWhenSelectionContainsAVertex() throws Exception {
        ActionConstraint c = newConstraint();
        Probe probe = new Probe();
        injectAction(c, probe);

        c.invoke(selectionReturning(new Object[] {vertex()}), new mxEventObject("t"));

        assertTrue(c.isEnabled());
        assertTrue(probe.isEnabled());
    }

    @Test
    public void disablesWhenSelectionHasOnlyAnEdge() throws Exception {
        ActionConstraint c = newConstraint();
        Probe probe = new Probe();
        injectAction(c, probe);

        c.invoke(selectionReturning(new Object[] {edge()}), new mxEventObject("t"));

        assertFalse(c.isEnabled());
        assertFalse(probe.isEnabled());
    }

    @Test
    public void findsTheVertexAmongAMixedSelection() throws Exception {
        ActionConstraint c = newConstraint();
        Probe probe = new Probe();
        injectAction(c, probe);

        // An edge first, then a vertex, then a non-cell: the vertex must win.
        c.invoke(selectionReturning(new Object[] {edge(), vertex(), new Object()}), new mxEventObject("t"));

        assertTrue(c.isEnabled());
        assertTrue(probe.isEnabled());
    }

    @Test
    public void nonCellSelectionLeavesTheActionDisabled() throws Exception {
        ActionConstraint c = newConstraint();
        Probe probe = new Probe();
        injectAction(c, probe);

        c.invoke(selectionReturning(new Object[] {"x", new Object()}), new mxEventObject("t"));

        assertFalse(c.isEnabled());
        assertFalse(probe.isEnabled());
    }

    @Test
    public void emptySelectionDisablesTheAction() throws Exception {
        ActionConstraint c = newConstraint();
        Probe probe = new Probe();
        injectAction(c, probe);

        c.invoke(selectionReturning(new Object[0]), new mxEventObject("t"));

        assertFalse(c.isEnabled());
        assertFalse(probe.isEnabled());
    }

    @Test
    public void nullSelectionIsANoOpAndKeepsTheDefaultDisabledState() throws Exception {
        ActionConstraint c = newConstraint();
        Probe probe = new Probe();
        injectAction(c, probe);

        // cells == null: the guarded body never calls setEnabled(...), so the
        // constraint keeps its constructed default (disabled) and never touches
        // the action.
        c.invoke(selectionReturning(null), new mxEventObject("t"));

        assertFalse(c.isEnabled());
    }
}
