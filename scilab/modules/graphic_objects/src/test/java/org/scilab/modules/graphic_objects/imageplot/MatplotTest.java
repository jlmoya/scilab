/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Scilab Enterprises
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 *
 */

package org.scilab.modules.graphic_objects.imageplot;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import org.scilab.modules.graphic_objects.graphicObject.GraphicObject.UpdateStatus;
import org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties;

import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.*;

/**
 * Hermetic unit tests for {@link Matplot}. Matplot only contributes a type tag;
 * the tests confirm the tag and reach the inherited {@link Imageplot} state.
 */
public class MatplotTest {

    @Test
    public void typeIsMatplot() {
        assertEquals(Integer.valueOf(GraphicObjectProperties.__GO_MATPLOT__), new Matplot().getType());
    }

    @Test
    public void inheritsImageplotDefaults() {
        Matplot m = new Matplot();
        assertArrayEquals(new Double[] {1.0, 1.0}, m.getScale());
        assertArrayEquals(new Double[] {0.5, 0.5}, m.getTranslate());
    }

    @Test
    public void inheritedSettersRoundTrip() {
        Matplot m = new Matplot();
        assertEquals(UpdateStatus.Success, m.setScale(new Double[] {10.0, 20.0}));
        assertArrayEquals(new Double[] {10.0, 20.0}, m.getScale());
        assertEquals(UpdateStatus.Success, m.setTranslate(new Double[] {0.0, 0.0}));
        assertArrayEquals(new Double[] {0.0, 0.0}, m.getTranslate());
    }

    @Test
    public void propertyNameLookupWorksThroughMatplot() {
        Matplot m = new Matplot();
        Object translate = m.getPropertyFromName(__GO_MATPLOT_TRANSLATE__);
        assertNotNull(translate);
        assertEquals(UpdateStatus.Success, m.setProperty(translate, new Double[] {1.0, 2.0}));
        assertArrayEquals(new Double[] {1.0, 2.0}, (Double[]) m.getProperty(translate));
    }

    @Test
    public void distinctInstancesHoldIndependentScale() {
        Matplot a = new Matplot();
        Matplot b = new Matplot();
        a.setScale(new Double[] {9.0, 9.0});
        assertArrayEquals(new Double[] {9.0, 9.0}, a.getScale());
        assertArrayEquals(new Double[] {1.0, 1.0}, b.getScale());
    }
}
