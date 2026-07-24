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
package org.scilab.modules.xcos.modelica.listener;

import static org.junit.jupiter.api.Assertions.*;

import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableModel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.scilab.modules.xcos.modelica.ModelStatistics;

/**
 * Hermetic unit tests for {@link StatisticsUpdater}.
 *
 * <p>{@code StatisticsUpdater} is a {@link TableModelListener} that mutates a
 * {@link ModelStatistics} tally, but only in response to the specialised
 * {@code TerminalTableModel.TerminalTableModelEvent}. Every other
 * ({@code "classic"}) {@link TableModelEvent} is filtered out by the leading
 * {@code instanceof} guard and must be a strict no-op.</p>
 *
 * <p><b>Why only the filter path is covered.</b> The event-processing branch
 * calls {@code TerminalAccessor.values()}, which forces initialisation of the
 * {@code TerminalAccessor} enum. Each of that enum's constants is built from a
 * {@code ModelicaMessages} constant, and every {@code ModelicaMessages} field
 * is initialised by {@code Messages.gettext(...)} &rarr;
 * {@code MessagesJNI.gettext(...)}, a {@code native} method backed by the
 * {@code scilocalization} library. Exercising that branch therefore requires
 * the Scilab native runtime and cannot run hermetically. These tests
 * deliberately stay on the {@code instanceof}-filtered path, where a plain
 * {@code TableModelEvent} is merely <em>resolved</em> against the inner event
 * type (a pure-Swing class) and rejected &mdash; {@code TerminalAccessor} is
 * never initialised. The tests below pin that no-op contract.</p>
 */
public class StatisticsUpdaterTest {

    /** A source model for plain events; its contents are irrelevant. */
    private static TableModelEvent classicEvent() {
        return new TableModelEvent(new DefaultTableModel(3, 6));
    }

    @Test
    @DisplayName("constructor yields a usable TableModelListener")
    public void constructorProducesTableModelListener() {
        StatisticsUpdater updater = new StatisticsUpdater(new ModelStatistics());
        assertNotNull(updater);
        assertTrue(updater instanceof TableModelListener,
                   "StatisticsUpdater must be a TableModelListener");
    }

    @Test
    @DisplayName("a classic event leaves fresh statistics empty")
    public void classicEventLeavesEmptyStatisticsUntouched() {
        ModelStatistics stats = new ModelStatistics();
        StatisticsUpdater updater = new StatisticsUpdater(stats);

        assertTrue(stats.isEmpty(), "precondition: fresh statistics are empty");
        updater.tableChanged(classicEvent());

        assertTrue(stats.isEmpty(),
                   "a non-TerminalTableModelEvent must not modify the statistics");
        assertEquals(0L, stats.getEquations());
        assertEquals(0L, stats.getFixedParameters());
        assertEquals(0L, stats.getRelaxedParameters());
        assertEquals(0L, stats.getFixedVariables());
        assertEquals(0L, stats.getRelaxedVariables());
    }

    @Test
    @DisplayName("a classic event leaves pre-populated statistics unchanged")
    public void classicEventLeavesPrepopulatedStatisticsUntouched() {
        ModelStatistics stats = new ModelStatistics();
        stats.incEquations(5);
        stats.incInputs(3);
        stats.incFixedParameters(2);
        stats.incRelaxedVariables(7);
        assertFalse(stats.isEmpty());

        StatisticsUpdater updater = new StatisticsUpdater(stats);
        updater.tableChanged(classicEvent());

        assertEquals(5L, stats.getEquations());
        assertEquals(3L, stats.getInputs());
        assertEquals(2L, stats.getFixedParameters());
        assertEquals(7L, stats.getRelaxedVariables());
        assertFalse(stats.isEmpty());
    }

    @Test
    @DisplayName("a classic event does not fire a ChangeEvent")
    public void classicEventDoesNotFireChange() {
        ModelStatistics stats = new ModelStatistics();
        final int[] fired = {0};
        stats.addChangeListener(e -> fired[0]++);

        StatisticsUpdater updater = new StatisticsUpdater(stats);
        updater.tableChanged(classicEvent());

        assertEquals(0, fired[0],
                     "filtered-out events must not notify statistics listeners");
    }

    @Test
    @DisplayName("a null event is a silent no-op (null instanceof X == false)")
    public void nullEventIsANoOp() {
        ModelStatistics stats = new ModelStatistics();
        final int[] fired = {0};
        stats.addChangeListener(e -> fired[0]++);
        StatisticsUpdater updater = new StatisticsUpdater(stats);

        assertDoesNotThrow(() -> updater.tableChanged(null));
        assertTrue(stats.isEmpty());
        assertEquals(0, fired[0]);
    }

    @Test
    @DisplayName("every classic TableModelEvent shape is ignored")
    public void variousEventTypesAreAllNoOps() {
        ModelStatistics stats = new ModelStatistics();
        final int[] fired = {0};
        stats.addChangeListener(e -> fired[0]++);
        StatisticsUpdater updater = new StatisticsUpdater(stats);
        DefaultTableModel model = new DefaultTableModel(4, 8);

        TableModelEvent[] events = {
            new TableModelEvent(model),
            new TableModelEvent(model, 1),
            new TableModelEvent(model, 0, 3, TableModelEvent.ALL_COLUMNS, TableModelEvent.INSERT),
            new TableModelEvent(model, 0, 3, TableModelEvent.ALL_COLUMNS, TableModelEvent.DELETE),
            new TableModelEvent(model, 0, 3, 2, TableModelEvent.UPDATE),
            // column 5 is the ordinal position of the WEIGHT accessor; a classic
            // event on that column is *still* ignored because the guard filters
            // on the event *type*, not on the column index.
            new TableModelEvent(model, 1, 1, 5, TableModelEvent.UPDATE),
            new TableModelEvent(model, TableModelEvent.HEADER_ROW),
        };

        for (TableModelEvent e : events) {
            updater.tableChanged(e);
        }

        assertTrue(stats.isEmpty(), "no classic event may touch the statistics");
        assertEquals(0, fired[0], "no classic event may fire a change");
    }

    @Test
    @DisplayName("many consecutive classic events remain no-ops")
    public void repeatedClassicEventsRemainNoOps() {
        ModelStatistics stats = new ModelStatistics();
        StatisticsUpdater updater = new StatisticsUpdater(stats);

        for (int i = 0; i < 100; i++) {
            updater.tableChanged(classicEvent());
        }

        assertTrue(stats.isEmpty());
        assertEquals(0L, stats.getUnknowns());
    }

    @Test
    @DisplayName("null statistics is stored without validation; a classic event never dereferences it")
    public void nullStatisticsIsStoredAndNotDereferencedOnClassicEvent() {
        // The constructor performs no null-check (characterization); and because
        // a classic event returns before touching the statistics field, even a
        // null-backed updater handles it without a NullPointerException.
        StatisticsUpdater updater = assertDoesNotThrow(() -> new StatisticsUpdater(null));
        assertDoesNotThrow(() -> updater.tableChanged(classicEvent()));
        assertDoesNotThrow(() -> updater.tableChanged(null));
    }
}
