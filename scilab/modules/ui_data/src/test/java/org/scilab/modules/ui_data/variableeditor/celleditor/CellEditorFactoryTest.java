/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Scilab
 *
 * Hermetic JUnit 6 unit tests for the ui_data module.
 * This file is hereby licensed under the terms of the GNU GPL v2.0.
 */

package org.scilab.modules.ui_data.variableeditor.celleditor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.scilab.modules.ui_data.EditVar;

/**
 * Tests {@link CellEditorFactory#createCellEditor(String)}: the mapping from an
 * {@link EditVar} type label to the concrete cell-editor class (exact-class assertions,
 * since the editor hierarchy shares supertypes).
 *
 * {@code EditVar.*} are compile-time String constants, so referencing them here does not
 * trigger loading of the {@code EditVar} class at test runtime.
 */
public class CellEditorFactoryTest {

    @Test
    public void mapsEachTypeToItsEditorClass() {
        assertEquals(ScilabStringCellEditor.class,
                     CellEditorFactory.createCellEditor(EditVar.STRING).getClass());
        assertEquals(ScilabComplexCellEditor.class,
                     CellEditorFactory.createCellEditor(EditVar.COMPLEX).getClass());
        assertEquals(ScilabDoubleCellEditor.class,
                     CellEditorFactory.createCellEditor(EditVar.DOUBLE).getClass());
        assertEquals(ScilabBooleanCellEditor.class,
                     CellEditorFactory.createCellEditor(EditVar.BOOLEAN).getClass());
        assertEquals(ScilabIntegerCellEditor.class,
                     CellEditorFactory.createCellEditor(EditVar.INTEGER).getClass());
    }

    @Test
    public void sparseTypesReuseTheirDenseEditors() {
        assertEquals(ScilabDoubleCellEditor.class,
                     CellEditorFactory.createCellEditor(EditVar.SPARSE).getClass());
        assertEquals(ScilabComplexCellEditor.class,
                     CellEditorFactory.createCellEditor(EditVar.COMPLEXSPARSE).getClass());
        assertEquals(ScilabBooleanCellEditor.class,
                     CellEditorFactory.createCellEditor(EditVar.BOOLEANSPARSE).getClass());
    }

    @Test
    public void unknownTypeFallsBackToStringEditor() {
        assertEquals(ScilabStringCellEditor.class,
                     CellEditorFactory.createCellEditor("no-such-type").getClass());
    }

    @Test
    public void nullTypeThrows() {
        // The factory dereferences type.equals(...) without a null guard.
        assertThrows(NullPointerException.class, () -> CellEditorFactory.createCellEditor(null));
    }
}
