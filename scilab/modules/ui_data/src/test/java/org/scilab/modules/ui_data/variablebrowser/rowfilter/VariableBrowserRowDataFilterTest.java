/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Scilab
 *
 * Hermetic JUnit 6 unit tests for the ui_data module.
 * This file is hereby licensed under the terms of the GNU GPL v2.0.
 */

package org.scilab.modules.ui_data.variablebrowser.rowfilter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.swing.RowFilter;

import org.junit.jupiter.api.Test;
import org.scilab.modules.ui_data.BrowseVar;

/**
 * Tests {@link VariableBrowserRowDataFilter}, the "hide Scilab variables" row filter.
 *
 * The filter reads the boolean at column {@link BrowseVar#FROM_SCILAB_COLUMN_INDEX}
 * ({@code true} == a from-Scilab/user variable). Its rule reduces to:
 * exclude the row iff {@code filteredValues == TRUE} and the row value is {@code false}.
 */
public class VariableBrowserRowDataFilterTest {

    /** Minimal {@link RowFilter.Entry} whose {@code getValue()} returns a fixed object. */
    private static RowFilter.Entry<Object, Object> entryReturning(final Object value) {
        return new RowFilter.Entry<Object, Object>() {
            @Override
            public Object getModel() {
                return null;
            }

            @Override
            public int getValueCount() {
                return BrowseVar.FROM_SCILAB_COLUMN_INDEX + 1;
            }

            @Override
            public Object getValue(int index) {
                return value;
            }

            @Override
            public Object getIdentifier() {
                return Integer.valueOf(0);
            }
        };
    }

    @Test
    public void filterTrueExcludesFalseRows() {
        VariableBrowserRowDataFilter filter = new VariableBrowserRowDataFilter(Boolean.TRUE);
        // filteredValues==TRUE and row==false => excluded.
        assertFalse(filter.include(entryReturning(Boolean.FALSE)));
    }

    @Test
    public void filterTrueIncludesTrueRows() {
        VariableBrowserRowDataFilter filter = new VariableBrowserRowDataFilter(Boolean.TRUE);
        assertTrue(filter.include(entryReturning(Boolean.TRUE)));
    }

    @Test
    public void filterFalseIncludesEverything() {
        VariableBrowserRowDataFilter filter = new VariableBrowserRowDataFilter(Boolean.FALSE);
        assertTrue(filter.include(entryReturning(Boolean.TRUE)));
        assertTrue(filter.include(entryReturning(Boolean.FALSE)));
    }

    @Test
    public void defaultConstructorLeavesFilterValueNullAndIncludeThrowsNpe() {
        // Documented defect: the no-arg constructor never sets filteredValues, so the
        // primitive comparison inside include() unboxes null and throws NPE.
        VariableBrowserRowDataFilter filter = new VariableBrowserRowDataFilter();
        assertThrows(NullPointerException.class, () -> filter.include(entryReturning(Boolean.FALSE)));
    }
}
