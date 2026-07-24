/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Scilab
 *
 * Hermetic JUnit 6 unit tests for the ui_data module.
 * This file is hereby licensed under the terms of the GNU GPL v2.0.
 */

package org.scilab.modules.ui_data.variableeditor.renderers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.scilab.modules.ui_data.EditVar;

/**
 * Tests {@link RendererFactory#createRenderer(String)}: the mapping from an {@link EditVar}
 * type label to the concrete renderer class (exact-class assertions, since renderers share
 * supertypes). {@code EditVar.*} are compile-time String constants, so referencing them here
 * does not load the {@code EditVar} class at test runtime.
 */
public class RendererFactoryTest {

    @Test
    public void mapsEachTypeToItsRendererClass() {
        assertEquals(ScilabStringRenderer.class,
                     RendererFactory.createRenderer(EditVar.STRING).getClass());
        assertEquals(ScilabComplexRenderer.class,
                     RendererFactory.createRenderer(EditVar.COMPLEX).getClass());
        assertEquals(ScilabDoubleRenderer.class,
                     RendererFactory.createRenderer(EditVar.DOUBLE).getClass());
        assertEquals(ScilabBooleanRenderer.class,
                     RendererFactory.createRenderer(EditVar.BOOLEAN).getClass());
        assertEquals(ScilabIntegerRenderer.class,
                     RendererFactory.createRenderer(EditVar.INTEGER).getClass());
    }

    @Test
    public void mapsSparseTypesToSparseRenderers() {
        assertEquals(ScilabSparseRenderer.class,
                     RendererFactory.createRenderer(EditVar.SPARSE).getClass());
        assertEquals(ScilabComplexSparseRenderer.class,
                     RendererFactory.createRenderer(EditVar.COMPLEXSPARSE).getClass());
        assertEquals(ScilabBooleanSparseRenderer.class,
                     RendererFactory.createRenderer(EditVar.BOOLEANSPARSE).getClass());
    }

    @Test
    public void unknownTypeFallsBackToStringRenderer() {
        assertEquals(ScilabStringRenderer.class,
                     RendererFactory.createRenderer("no-such-type").getClass());
    }

    @Test
    public void nullTypeThrows() {
        assertThrows(NullPointerException.class, () -> RendererFactory.createRenderer(null));
    }
}
