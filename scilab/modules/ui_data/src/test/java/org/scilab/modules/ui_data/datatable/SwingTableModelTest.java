/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Scilab
 *
 * Hermetic JUnit 6 unit tests for the ui_data module.
 * This file is hereby licensed under the terms of the GNU GPL v2.0.
 */

package org.scilab.modules.ui_data.datatable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import javax.swing.ImageIcon;

import org.junit.jupiter.api.Test;

/**
 * Tests {@link SwingTableModel}, the {@code DefaultTableModel}-based data model used by
 * the variable browser. Construction is headless-safe and involves no native code.
 */
public class SwingTableModelTest {

    @Test
    public void columnNamesConstructorDrivesColumnCountAndNames() {
        SwingTableModel<Object> model = new SwingTableModel<Object>(new String[] {"Icon", "Name", "Size"});

        assertEquals(3, model.getColumnCount());
        assertEquals("Icon", model.getColumnName(0));
        assertEquals("Name", model.getColumnName(1));
        assertEquals("Size", model.getColumnName(2));
    }

    @Test
    public void withoutDataRowCountIsZeroAndValuesAreNull() {
        SwingTableModel<Object> model = new SwingTableModel<Object>(new String[] {"a", "b"});

        assertEquals(0, model.getRowCount());
        assertNull(model.getValueAt(0, 0));
    }

    @Test
    public void dataConstructorDrivesRowAndColumnCounts() {
        Object[][] data = new Object[][] {
            {"r0c0", "r0c1", "r0c2", "r0c3", "r0c4"},
            {"r1c0", "r1c1", "r1c2", "r1c3", "r1c4"}
        };
        SwingTableModel<Object> model = new SwingTableModel<Object>(data);

        assertEquals(2, model.getRowCount());
        assertEquals(5, model.getColumnCount());
        assertEquals("r1c3", model.getValueAt(1, 3));
    }

    @Test
    public void withoutColumnNamesColumnNameIsTheIndex() {
        Object[][] data = new Object[][] {{"x", "y"}};
        SwingTableModel<Object> model = new SwingTableModel<Object>(data);

        assertEquals("0", model.getColumnName(0));
        assertEquals("1", model.getColumnName(1));
    }

    @Test
    public void getColumnClassForcesIconAndIntegerColumns() {
        Object[][] data = new Object[][] {{"s0", "s1", "s2", "s3", "s4"}};
        SwingTableModel<Object> model = new SwingTableModel<Object>(data);

        // Column 0 is always rendered as an image icon...
        assertEquals(ImageIcon.class, model.getColumnClass(0));
        // ...columns 2 and 3 are always Integers...
        assertEquals(Integer.class, model.getColumnClass(2));
        assertEquals(Integer.class, model.getColumnClass(3));
        // ...other columns reflect the runtime class of the first row's value.
        assertEquals(String.class, model.getColumnClass(1));
        assertEquals(String.class, model.getColumnClass(4));
    }

    @Test
    public void getColumnClassFallsBackToObjectWhenValueIsNull() {
        // No data -> getValueAt(0, c) is null -> Object.class for a non-forced column.
        SwingTableModel<Object> model = new SwingTableModel<Object>(new String[] {"a", "b"});
        assertEquals(Object.class, model.getColumnClass(1));
    }

    @Test
    public void setDataReplacesTheBackingArray() {
        SwingTableModel<Object> model = new SwingTableModel<Object>(new String[] {"a", "b"});
        assertEquals(0, model.getRowCount());

        Object[][] data = new Object[][] {{"p", "q"}, {"r", "s"}};
        model.setData(data);

        assertEquals(2, model.getRowCount());
        assertSame(data[1][0], model.getValueAt(1, 0));
    }

    @Test
    public void cellsAreNeverEditable() {
        SwingTableModel<Object> model = new SwingTableModel<Object>(new Object[][] {{"a"}});
        assertFalse(model.isCellEditable(0, 0));
    }
}
