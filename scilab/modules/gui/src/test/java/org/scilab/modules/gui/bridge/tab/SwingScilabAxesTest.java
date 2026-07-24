/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Jose Moya
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 *
 */

package org.scilab.modules.gui.bridge.tab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties;
import org.scilab.modules.gui.SwingViewObject;

/**
 * Hermetic unit tests for {@link SwingScilabAxes}.
 *
 * <p>The class is a tiny {@link SwingViewObject} state holder over two
 * {@code Integer} ids: its own {@code id} and — captured from
 * {@code update(__GO_PARENT__, value)} — its parent {@code figureId}.
 * {@code __GO_PARENT__} is a compile-time integer constant, so nothing here
 * loads the graphic-objects native runtime.
 */
class SwingScilabAxesTest {

    // ------------------------------------------------------------------
    // Initial state
    // ------------------------------------------------------------------

    @Test
    void freshAxesHasNullIds() {
        SwingScilabAxes axes = new SwingScilabAxes();

        assertNull(axes.getId());
        assertNull(axes.getFigureId());
    }

    // ------------------------------------------------------------------
    // id
    // ------------------------------------------------------------------

    @Test
    void setIdRoundTrips() {
        SwingScilabAxes axes = new SwingScilabAxes();

        axes.setId(Integer.valueOf(7));

        assertEquals(Integer.valueOf(7), axes.getId());
    }

    /** The exact reference is stored, not a copy. */
    @Test
    void setIdStoresExactReference() {
        SwingScilabAxes axes = new SwingScilabAxes();
        Integer ref = Integer.valueOf(100000); // beyond the Integer cache

        axes.setId(ref);

        assertSame(ref, axes.getId());
    }

    @Test
    void setIdAcceptsNull() {
        SwingScilabAxes axes = new SwingScilabAxes();

        axes.setId(Integer.valueOf(5));
        axes.setId(null);

        assertNull(axes.getId());
    }

    // ------------------------------------------------------------------
    // update -> figureId
    // ------------------------------------------------------------------

    @Test
    void updateParentSetsFigureId() {
        SwingScilabAxes axes = new SwingScilabAxes();

        axes.update(GraphicObjectProperties.__GO_PARENT__, Integer.valueOf(42));

        assertEquals(Integer.valueOf(42), axes.getFigureId());
    }

    @Test
    void updateParentStoresExactReference() {
        SwingScilabAxes axes = new SwingScilabAxes();
        Integer ref = Integer.valueOf(2000000);

        axes.update(GraphicObjectProperties.__GO_PARENT__, ref);

        assertSame(ref, axes.getFigureId());
    }

    /** A non-parent property must not disturb the figure id. */
    @Test
    void updateOfOtherPropertyLeavesFigureIdUntouched() {
        SwingScilabAxes axes = new SwingScilabAxes();

        axes.update(GraphicObjectProperties.__GO_PARENT__, Integer.valueOf(42));
        axes.update(GraphicObjectProperties.__GO_PARENT__ + 1, Integer.valueOf(99));

        assertEquals(Integer.valueOf(42), axes.getFigureId());
    }

    @Test
    void updateOfOtherPropertyOnFreshAxesKeepsFigureIdNull() {
        SwingScilabAxes axes = new SwingScilabAxes();

        axes.update(GraphicObjectProperties.__GO_PARENT__ + 1, Integer.valueOf(99));

        assertNull(axes.getFigureId());
    }

    @Test
    void updateParentWithNullClearsFigureId() {
        SwingScilabAxes axes = new SwingScilabAxes();

        axes.update(GraphicObjectProperties.__GO_PARENT__, Integer.valueOf(42));
        axes.update(GraphicObjectProperties.__GO_PARENT__, null);

        assertNull(axes.getFigureId());
    }

    /**
     * Defect characterization: {@code update} unconditionally casts the parent
     * value to {@code Integer}, so a non-Integer payload throws
     * {@link ClassCastException} rather than being ignored or coerced.
     */
    @Test
    void updateParentWithNonIntegerThrows() {
        SwingScilabAxes axes = new SwingScilabAxes();

        assertThrows(ClassCastException.class,
                     () -> axes.update(GraphicObjectProperties.__GO_PARENT__, "notAnInteger"));
    }

    // ------------------------------------------------------------------
    // id vs figureId are independent slots
    // ------------------------------------------------------------------

    @Test
    void idAndFigureIdAreIndependent() {
        SwingScilabAxes axes = new SwingScilabAxes();

        axes.setId(Integer.valueOf(1));
        axes.update(GraphicObjectProperties.__GO_PARENT__, Integer.valueOf(2));

        assertEquals(Integer.valueOf(1), axes.getId());
        assertEquals(Integer.valueOf(2), axes.getFigureId());
    }

    @Test
    void settingIdDoesNotAffectFigureId() {
        SwingScilabAxes axes = new SwingScilabAxes();

        axes.update(GraphicObjectProperties.__GO_PARENT__, Integer.valueOf(2));
        axes.setId(Integer.valueOf(1));

        assertEquals(Integer.valueOf(2), axes.getFigureId());
    }

    // ------------------------------------------------------------------
    // Type contract
    // ------------------------------------------------------------------

    @Test
    void isASwingViewObject() {
        assertTrue(new SwingScilabAxes() instanceof SwingViewObject);
    }
}
