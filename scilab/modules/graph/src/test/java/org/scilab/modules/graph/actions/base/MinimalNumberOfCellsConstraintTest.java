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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.event.ActionEvent;
import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;
import org.scilab.modules.graph.ScilabGraph;

/**
 * Hermetic unit tests for {@link MinimalNumberOfCellsConstraint}.
 *
 * {@code invoke} casts its sender to a {@link ScilabGraph} and reads the graph
 * model, so it cannot be driven without a live Swing graph and is not exercised
 * here. What is fully testable off the event loop is (a) the constructor's
 * bookkeeping - it silently adds 2 (the root plus the default parent) to the
 * caller-supplied minimum - and (b) the inherited {@link ActionConstraint}
 * enable/disable propagation to the constrained action.
 */
public class MinimalNumberOfCellsConstraintTest {

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

    private static int minimalCountOf(MinimalNumberOfCellsConstraint c) throws Exception {
        Field f = MinimalNumberOfCellsConstraint.class.getDeclaredField("minimalCount");
        f.setAccessible(true);
        return f.getInt(c);
    }

    private static void injectAction(ActionConstraint c, DefaultAction a) throws Exception {
        Field f = ActionConstraint.class.getDeclaredField("action");
        f.setAccessible(true);
        f.set(c, a);
    }

    @Test
    public void constructorAddsRootAndDefaultParentToTheRequestedMinimum() throws Exception {
        // A requested minimum of 1 real cell becomes 3 internally (1 + root +
        // default parent).
        assertEquals(3, minimalCountOf(new MinimalNumberOfCellsConstraint(1)));
        assertEquals(2, minimalCountOf(new MinimalNumberOfCellsConstraint(0)));
        assertEquals(7, minimalCountOf(new MinimalNumberOfCellsConstraint(5)));
    }

    @Test
    public void numberOfCellsStartsAtZero() throws Exception {
        MinimalNumberOfCellsConstraint c = new MinimalNumberOfCellsConstraint(3);
        Field f = MinimalNumberOfCellsConstraint.class.getDeclaredField("numberOfCells");
        f.setAccessible(true);
        assertEquals(0, f.getInt(c));
    }

    @Test
    public void setEnabledPropagatesToTheConstrainedActionAndBack() throws Exception {
        MinimalNumberOfCellsConstraint c = new MinimalNumberOfCellsConstraint(1);
        Probe probe = new Probe();
        injectAction(c, probe);

        c.setEnabled(true);
        assertTrue(c.isEnabled());
        assertTrue(probe.isEnabled());

        c.setEnabled(false);
        assertFalse(c.isEnabled());
        assertFalse(probe.isEnabled());
    }
}
