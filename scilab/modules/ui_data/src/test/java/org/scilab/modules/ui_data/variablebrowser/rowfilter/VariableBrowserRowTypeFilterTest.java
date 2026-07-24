/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Scilab
 *
 * Hermetic JUnit 6 unit tests for the ui_data module.
 * This file is hereby licensed under the terms of the GNU GPL v2.0.
 */

package org.scilab.modules.ui_data.variablebrowser.rowfilter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;

import javax.swing.RowFilter;

import org.junit.jupiter.api.Test;
import org.scilab.modules.types.ScilabTypeEnum;
import org.scilab.modules.ui_data.BrowseVar;

/**
 * Tests {@link VariableBrowserRowTypeFilter}, which hides rows whose Scilab type (an
 * int at column {@link BrowseVar#TYPE_COLUMN_INDEX}, decoded via
 * {@link ScilabTypeEnum#swigToEnum(int)}) is present in the filtered-out set.
 *
 * Depends on the sibling {@code types} module (already a compile dependency of ui_data),
 * which supplies the pure-Java SWIG enum {@link ScilabTypeEnum}.
 */
public class VariableBrowserRowTypeFilterTest {

    /** Minimal {@link RowFilter.Entry} whose {@code getValue()} returns a fixed type code. */
    private static RowFilter.Entry<Object, Object> entryWithTypeCode(final int typeCode) {
        return new RowFilter.Entry<Object, Object>() {
            @Override
            public Object getModel() {
                return null;
            }

            @Override
            public int getValueCount() {
                return BrowseVar.TYPE_COLUMN_INDEX + 1;
            }

            @Override
            public Object getValue(int index) {
                return Integer.valueOf(typeCode);
            }

            @Override
            public Object getIdentifier() {
                return Integer.valueOf(0);
            }
        };
    }

    @Test
    public void emptyFilterIncludesEveryType() {
        VariableBrowserRowTypeFilter filter = new VariableBrowserRowTypeFilter();
        assertTrue(filter.include(entryWithTypeCode(ScilabTypeEnum.sci_matrix.swigValue())));
        assertTrue(filter.include(entryWithTypeCode(ScilabTypeEnum.sci_poly.swigValue())));
    }

    @Test
    public void typeInFilteredSetIsExcluded() {
        HashSet<ScilabTypeEnum> hidden = new HashSet<ScilabTypeEnum>();
        hidden.add(ScilabTypeEnum.sci_matrix);
        VariableBrowserRowTypeFilter filter = new VariableBrowserRowTypeFilter(hidden);

        // sci_matrix (code 1) is hidden -> excluded.
        assertFalse(filter.include(entryWithTypeCode(ScilabTypeEnum.sci_matrix.swigValue())));
    }

    @Test
    public void typeNotInFilteredSetIsIncluded() {
        HashSet<ScilabTypeEnum> hidden = new HashSet<ScilabTypeEnum>();
        hidden.add(ScilabTypeEnum.sci_matrix);
        VariableBrowserRowTypeFilter filter = new VariableBrowserRowTypeFilter(hidden);

        // sci_poly (code 2) is not hidden -> included.
        assertTrue(filter.include(entryWithTypeCode(ScilabTypeEnum.sci_poly.swigValue())));
    }

    @Test
    public void unknownTypeCodeIsIncludedNotThrown() {
        // 3 maps to no ScilabTypeEnum, so swigToEnum throws IllegalArgumentException;
        // the filter catches it and includes the "unknown type" row (bug #7333 behavior).
        HashSet<ScilabTypeEnum> hidden = new HashSet<ScilabTypeEnum>();
        hidden.add(ScilabTypeEnum.sci_matrix);
        VariableBrowserRowTypeFilter filter = new VariableBrowserRowTypeFilter(hidden);

        assertTrue(filter.include(entryWithTypeCode(3)));
    }
}
