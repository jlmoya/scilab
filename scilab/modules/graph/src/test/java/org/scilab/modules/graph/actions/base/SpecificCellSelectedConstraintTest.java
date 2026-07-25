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

import com.mxgraph.model.mxCell;
import com.mxgraph.util.mxEventObject;
import com.mxgraph.view.mxGraph;
import com.mxgraph.view.mxGraphSelectionModel;

/**
 * Hermetic unit tests for {@link SpecificCellSelectedConstraint#invoke}, which
 * enables the action iff the selection contains at least one cell that is an
 * instance of a configured {@code kind}.
 *
 * As for the sibling constraints, {@code install(...)} needs a live
 * {@link ScilabGraph}; here the constrained action is injected reflectively and
 * the selection is supplied by a {@link mxGraphSelectionModel} with an
 * overridden {@code getCells()}.
 */
public class SpecificCellSelectedConstraintTest {

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

    /** A dedicated mxCell subtype used to test kind matching precisely. */
    public static class SpecialCell extends mxCell {
    }

    static void injectAction(ActionConstraint c, DefaultAction a) throws Exception {
        Field f = ActionConstraint.class.getDeclaredField("action");
        f.setAccessible(true);
        f.set(c, a);
    }

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
    public void enablesWhenSelectionContainsMatchingKind() throws Exception {
        SpecificCellSelectedConstraint c = new SpecificCellSelectedConstraint(mxCell.class);
        Probe probe = wireProbeTo(c);

        c.invoke(selectionReturning(new Object[] {new mxCell()}), new mxEventObject("t"));

        assertTrue(c.isEnabled());
        assertTrue(probe.isEnabled());
    }

    @Test
    public void disablesWhenNoCellMatchesKind() throws Exception {
        SpecificCellSelectedConstraint c = new SpecificCellSelectedConstraint(mxCell.class);
        Probe probe = wireProbeTo(c);

        // Plain Objects are not mxCell instances.
        c.invoke(selectionReturning(new Object[] {new Object(), "str"}), new mxEventObject("t"));

        assertFalse(c.isEnabled());
        assertFalse(probe.isEnabled());
    }

    @Test
    public void disablesOnEmptySelection() throws Exception {
        SpecificCellSelectedConstraint c = new SpecificCellSelectedConstraint(mxCell.class);
        Probe probe = wireProbeTo(c);

        c.invoke(selectionReturning(new Object[0]), new mxEventObject("t"));

        assertFalse(c.isEnabled());
        assertFalse(probe.isEnabled());
    }

    @Test
    public void matchingIsBySubtypeSoBaseCellDoesNotSatisfyASubtypeKind() throws Exception {
        // kind == SpecialCell: a plain mxCell is NOT an instance of SpecialCell.
        SpecificCellSelectedConstraint c = new SpecificCellSelectedConstraint(SpecialCell.class);
        Probe probe = wireProbeTo(c);

        c.invoke(selectionReturning(new Object[] {new mxCell()}), new mxEventObject("t"));
        assertFalse(c.isEnabled());

        // ...but a SpecialCell does.
        c.invoke(selectionReturning(new Object[] {new SpecialCell()}), new mxEventObject("t"));
        assertTrue(c.isEnabled());
        assertTrue(probe.isEnabled());
    }

    @Test
    public void findsMatchAmongMixedSelection() throws Exception {
        SpecificCellSelectedConstraint c = new SpecificCellSelectedConstraint(mxCell.class);
        Probe probe = wireProbeTo(c);

        c.invoke(selectionReturning(new Object[] {"x", new mxCell(), new Object()}), new mxEventObject("t"));

        assertTrue(c.isEnabled());
        assertTrue(probe.isEnabled());
    }
}
