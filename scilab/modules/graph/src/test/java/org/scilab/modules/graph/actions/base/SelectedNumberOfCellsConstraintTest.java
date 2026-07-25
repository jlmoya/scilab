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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.event.ActionEvent;
import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;
import org.scilab.modules.graph.ScilabGraph;

import com.mxgraph.util.mxEventObject;
import com.mxgraph.view.mxGraph;
import com.mxgraph.view.mxGraphSelectionModel;

/**
 * Hermetic unit tests for {@link SelectedNumberOfCellsConstraint#invoke}.
 *
 * The constraint's {@code install(...)} needs a live Swing {@link ScilabGraph},
 * so it is not exercised here. Instead the constrained {@link DefaultAction} is
 * injected directly (the field {@code ActionConstraint#action} has no public
 * setter) and {@code invoke} is driven with a bare {@link mxGraphSelectionModel}
 * whose {@code getCells()} is overridden to return a controlled selection. This
 * keeps the whole test off the AWT event loop.
 */
public class SelectedNumberOfCellsConstraintTest {

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

    /** Inject the constrained action (no public setter exists). */
    static void injectAction(ActionConstraint c, DefaultAction a) throws Exception {
        Field f = ActionConstraint.class.getDeclaredField("action");
        f.setAccessible(true);
        f.set(c, a);
    }

    /** A selection model whose reported selection is fully controlled. */
    static mxGraphSelectionModel selectionReturning(final Object[] controlled) {
        // NB: the parameter must not be named "cells" - mxGraphSelectionModel has
        // an inherited field of that name which would shadow it inside the
        // anonymous subclass.
        return new mxGraphSelectionModel(new mxGraph()) {
            @Override
            public Object[] getCells() {
                return controlled;
            }
        };
    }

    private Probe wireProbeTo(ActionConstraint c) throws Exception {
        Probe probe = new Probe();
        injectAction(c, probe);
        return probe;
    }

    @Test
    public void enablesWhenSelectionCountEqualsThreshold() throws Exception {
        SelectedNumberOfCellsConstraint c = new SelectedNumberOfCellsConstraint(2);
        Probe probe = wireProbeTo(c);

        c.invoke(selectionReturning(new Object[] {new Object(), new Object()}), new mxEventObject("t"));

        assertTrue(c.isEnabled());
        assertTrue(probe.isEnabled());
    }

    @Test
    public void enablesWhenSelectionCountExceedsThreshold() throws Exception {
        SelectedNumberOfCellsConstraint c = new SelectedNumberOfCellsConstraint(2);
        Probe probe = wireProbeTo(c);

        c.invoke(selectionReturning(new Object[] {new Object(), new Object(), new Object()}), new mxEventObject("t"));

        assertTrue(c.isEnabled());
        assertTrue(probe.isEnabled());
    }

    @Test
    public void disablesWhenSelectionCountBelowThreshold() throws Exception {
        SelectedNumberOfCellsConstraint c = new SelectedNumberOfCellsConstraint(2);
        Probe probe = wireProbeTo(c);

        c.invoke(selectionReturning(new Object[] {new Object()}), new mxEventObject("t"));

        assertFalse(c.isEnabled());
        assertFalse(probe.isEnabled());
    }

    @Test
    public void disablesOnNullSelection() throws Exception {
        SelectedNumberOfCellsConstraint c = new SelectedNumberOfCellsConstraint(1);
        Probe probe = wireProbeTo(c);

        c.invoke(selectionReturning(null), new mxEventObject("t"));

        assertFalse(c.isEnabled());
        assertFalse(probe.isEnabled());
    }

    @Test
    public void zeroThresholdEnablesEvenOnEmptySelection() throws Exception {
        // threshold 0: any non-null selection (length >= 0) enables the action.
        SelectedNumberOfCellsConstraint c = new SelectedNumberOfCellsConstraint(0);
        Probe probe = wireProbeTo(c);

        c.invoke(selectionReturning(new Object[0]), new mxEventObject("t"));

        assertTrue(c.isEnabled());
        assertTrue(probe.isEnabled());
    }
}
