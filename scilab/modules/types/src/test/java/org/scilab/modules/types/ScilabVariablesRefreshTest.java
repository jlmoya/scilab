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

package org.scilab.modules.types;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link ScilabVariablesRefresh}, the listener registry
 * behind Scilab-side variable refresh. It is backed by a {@code EventListenerList}
 * (no display / event loop needed). {@code handle} is driven end-to-end through the
 * {@link ScilabVariables} handler id so that a variable whose name is being listened
 * to reaches only the listeners that watch it. Listeners are always unregistered in
 * a {@code finally} so the shared static registry stays clean.
 */
public class ScilabVariablesRefreshTest {

    private static final class TestListener implements ScilabVariablesListener {
        final Set<String> names;
        ScilabVariablesEvent lastEvent;
        int updates;

        TestListener(String... names) {
            this.names = new HashSet<String>(Arrays.asList(names));
        }

        @Override
        public void scilabVariableUpdated(ScilabVariablesEvent event) {
            this.lastEvent = event;
            this.updates++;
        }

        @Override
        public Set<String> getListenedVariables() {
            return names;
        }
    }

    @Test
    public void refreshIdIsStableAcrossCalls() {
        int id1 = ScilabVariablesRefresh.getScilabVariablesRefreshId();
        int id2 = ScilabVariablesRefresh.getScilabVariablesRefreshId();
        assertTrue(id1 >= 0);
        assertEquals(id1, id2);
    }

    @Test
    public void listenedVariablesAreAggregatedAcrossListeners() {
        TestListener l = new TestListener("refreshVarAggA", "refreshVarAggB");
        ScilabVariablesRefresh.addScilabVariablesListener(l);
        try {
            List<String> all = new ArrayList<String>(Arrays.asList(ScilabVariablesRefresh.getAllListenedVariables()));
            assertTrue(all.contains("refreshVarAggA"));
            assertTrue(all.contains("refreshVarAggB"));
        } finally {
            ScilabVariablesRefresh.removeScilabVariablesListener(l);
        }
        // After removal the unique names are gone.
        List<String> after = new ArrayList<String>(Arrays.asList(ScilabVariablesRefresh.getAllListenedVariables()));
        assertFalse(after.contains("refreshVarAggA"));
    }

    @Test
    public void handleNotifiesOnlyTheListenersWatchingThatVariable() {
        TestListener watching = new TestListener("refreshReactVar");
        TestListener other = new TestListener("someOtherVar");
        ScilabVariablesRefresh.addScilabVariablesListener(watching);
        ScilabVariablesRefresh.addScilabVariablesListener(other);
        try {
            int refreshId = ScilabVariablesRefresh.getScilabVariablesRefreshId();
            // Route a named variable through the refresh handler.
            ScilabVariables.sendData("refreshReactVar", new int[0], new double[][] {{1.0}}, false, refreshId);

            assertNotNull(watching.lastEvent);
            assertEquals(1, watching.updates);
            assertEquals("refreshReactVar", watching.lastEvent.getScilabType().getVarName());
            // The non-watching listener is untouched.
            assertNull(other.lastEvent);
            assertEquals(0, other.updates);
        } finally {
            ScilabVariablesRefresh.removeScilabVariablesListener(watching);
            ScilabVariablesRefresh.removeScilabVariablesListener(other);
        }
    }

    @Test
    public void handleIgnoresVariablesNoListenerWatches() {
        TestListener watching = new TestListener("refreshWatchedOnly");
        ScilabVariablesRefresh.addScilabVariablesListener(watching);
        try {
            int refreshId = ScilabVariablesRefresh.getScilabVariablesRefreshId();
            ScilabVariables.sendData("refreshUnwatchedVar", new int[0], new double[][] {{1.0}}, false, refreshId);
            assertNull(watching.lastEvent);
        } finally {
            ScilabVariablesRefresh.removeScilabVariablesListener(watching);
        }
    }
}
