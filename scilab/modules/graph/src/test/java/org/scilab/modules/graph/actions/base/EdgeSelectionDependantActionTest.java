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
import com.mxgraph.view.mxGraph;
import com.mxgraph.view.mxGraphSelectionModel;

/**
 * Hermetic unit tests for {@link EdgeSelectionDependantAction} and its private
 * {@code EdgeSelectionDependantConstraint}.
 *
 * Mirror of {@link VertexSelectionDependantActionTest}: the constraint enables
 * the action iff the selection contains at least one edge {@link mxCell}. The
 * private nested constraint is built reflectively, the constrained action is
 * injected into the inherited {@code action} field, and {@code invoke(...)} is
 * driven with a selection model returning a controlled cell array.
 */
public class EdgeSelectionDependantActionTest {

    /** Public concrete subclass so the outer constructor's null path runs. */
    public static class EProbe extends EdgeSelectionDependantAction {
        public static final String NAME = "Edge probe";
        public static final String SMALL_ICON = "";
        public static final int MNEMONIC_KEY = 0;
        public static final int ACCELERATOR_KEY = 0;

        public EProbe(ScilabGraph graph) {
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

    private static ActionConstraint newConstraint() throws Exception {
        Class<?> nested = Class.forName(
                "org.scilab.modules.graph.actions.base.EdgeSelectionDependantAction$EdgeSelectionDependantConstraint");
        Constructor<?> ctor = nested.getDeclaredConstructor();
        ctor.setAccessible(true);
        return (ActionConstraint) ctor.newInstance();
    }

    private static void injectAction(ActionConstraint c, DefaultAction a) throws Exception {
        Field f = ActionConstraint.class.getDeclaredField("action");
        f.setAccessible(true);
        f.set(c, a);
    }

    private static mxGraphSelectionModel selectionReturning(final Object[] controlled) {
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
        EProbe action = new EProbe(null);
        assertNotNull(action);
        assertEquals("Edge probe", action.getValue(Action.NAME));
    }

    @Test
    public void enablesWhenSelectionContainsAnEdge() throws Exception {
        ActionConstraint c = newConstraint();
        Probe probe = new Probe();
        injectAction(c, probe);

        c.invoke(selectionReturning(new Object[] {edge()}), new mxEventObject("t"));

        assertTrue(c.isEnabled());
        assertTrue(probe.isEnabled());
    }

    @Test
    public void disablesWhenSelectionHasOnlyAVertex() throws Exception {
        ActionConstraint c = newConstraint();
        Probe probe = new Probe();
        injectAction(c, probe);

        c.invoke(selectionReturning(new Object[] {vertex()}), new mxEventObject("t"));

        assertFalse(c.isEnabled());
        assertFalse(probe.isEnabled());
    }

    @Test
    public void findsTheEdgeAmongAMixedSelection() throws Exception {
        ActionConstraint c = newConstraint();
        Probe probe = new Probe();
        injectAction(c, probe);

        c.invoke(selectionReturning(new Object[] {vertex(), edge(), "x"}), new mxEventObject("t"));

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

        c.invoke(selectionReturning(null), new mxEventObject("t"));

        assertFalse(c.isEnabled());
    }
}
