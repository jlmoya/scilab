/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Scilab
 *
 * Hermetic JUnit 6 unit tests for the ui_data module.
 * This file is hereby licensed under the terms of the GNU GPL v2.0.
 */

package org.scilab.modules.ui_data.filebrowser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.RowSorter;
import javax.swing.SortOrder;

import org.junit.jupiter.api.Test;

/**
 * Tests {@link FileBrowserRowSorter}: the row-index passthrough, the model/row-count
 * delegation to the injected {@link JTable}/{@link JTree}, and the sort-key bookkeeping.
 *
 * The sorter is built with a bare {@code new JTree()}/{@code new JTable()} (both construct
 * fine under {@code java.awt.headless=true}); no display, no Scilab runtime. The
 * {@code toggleSortOrder} method is deliberately NOT exercised — it reaches into a live
 * {@code ScilabFileBrowserModel}/{@code SwingScilabWindow} ancestor and FileNode, none of
 * which are hermetic.
 */
public class FileBrowserRowSorterTest {

    private FileBrowserRowSorter newSorter(JTree tree, JTable table) {
        return new FileBrowserRowSorter(tree, table);
    }

    // ---- index conversion is the identity (this sorter never reorders indices) ----

    @Test
    public void convertRowIndexToModelIsIdentity() {
        FileBrowserRowSorter sorter = newSorter(new JTree(), new JTable());
        assertEquals(0, sorter.convertRowIndexToModel(0));
        assertEquals(7, sorter.convertRowIndexToModel(7));
        assertEquals(-3, sorter.convertRowIndexToModel(-3));
    }

    @Test
    public void convertRowIndexToViewIsIdentity() {
        FileBrowserRowSorter sorter = newSorter(new JTree(), new JTable());
        assertEquals(0, sorter.convertRowIndexToView(0));
        assertEquals(7, sorter.convertRowIndexToView(7));
        assertEquals(-3, sorter.convertRowIndexToView(-3));
    }

    // ---- delegation to the injected widgets ----

    @Test
    public void getModelReturnsTheTablesModel() {
        JTable table = new JTable();
        FileBrowserRowSorter sorter = newSorter(new JTree(), table);
        assertSame(table.getModel(), sorter.getModel());
    }

    @Test
    public void rowCountsAreReadFromTheTree() {
        JTree tree = new JTree();
        FileBrowserRowSorter sorter = newSorter(tree, new JTable());
        // getViewRowCount() is documented to equal getModelRowCount(), and both read the tree.
        assertEquals(tree.getRowCount(), sorter.getModelRowCount());
        assertEquals(tree.getRowCount(), sorter.getViewRowCount());
        assertEquals(sorter.getModelRowCount(), sorter.getViewRowCount());
    }

    // ---- sort keys ----

    @Test
    public void defaultSortKeyIsAscendingOnColumnZero() {
        FileBrowserRowSorter sorter = newSorter(new JTree(), new JTable());
        List<RowSorter.SortKey> keys = sorter.getSortKeys();
        assertNotNull(keys);
        assertEquals(1, keys.size());
        assertEquals(0, keys.get(0).getColumn());
        assertEquals(SortOrder.ASCENDING, keys.get(0).getSortOrder());
    }

    @Test
    public void returnedSortKeyListIsUnmodifiable() {
        FileBrowserRowSorter sorter = newSorter(new JTree(), new JTable());
        List<RowSorter.SortKey> keys = sorter.getSortKeys();
        assertThrows(UnsupportedOperationException.class,
                     () -> keys.add(new RowSorter.SortKey(1, SortOrder.DESCENDING)));
    }

    @Test
    public void setSortKeysStoresACopyThatSurvivesMutationOfTheSource() {
        FileBrowserRowSorter sorter = newSorter(new JTree(), new JTable());
        List<RowSorter.SortKey> source = new ArrayList<RowSorter.SortKey>();
        source.add(new RowSorter.SortKey(2, SortOrder.DESCENDING));
        sorter.setSortKeys(source);

        // Mutating the caller's list afterward must not disturb the sorter (defensive copy).
        source.clear();

        List<RowSorter.SortKey> stored = sorter.getSortKeys();
        assertEquals(1, stored.size());
        assertEquals(2, stored.get(0).getColumn());
        assertEquals(SortOrder.DESCENDING, stored.get(0).getSortOrder());
    }

    @Test
    public void setSortKeysNullThrowsNPE() {
        // Defect characterization: setSortKeys wraps its argument in `new ArrayList<>(keys)`
        // with no null guard, so a null argument surfaces as a NullPointerException.
        FileBrowserRowSorter sorter = newSorter(new JTree(), new JTable());
        assertThrows(NullPointerException.class, () -> sorter.setSortKeys(null));
    }

    @Test
    public void noOpNotificationsDoNotThrow() {
        FileBrowserRowSorter sorter = newSorter(new JTree(), new JTable());
        // All of these are documented no-ops on this sorter; assert they stay harmless.
        sorter.allRowsChanged();
        sorter.modelStructureChanged();
        sorter.rowsInserted(0, 1);
        sorter.rowsDeleted(0, 1);
        sorter.rowsUpdated(0, 1);
        sorter.rowsUpdated(0, 1, 0);
    }
}
