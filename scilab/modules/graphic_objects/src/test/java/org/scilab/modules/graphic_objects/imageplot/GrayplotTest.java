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
 * Hermetic unit tests for {@link Grayplot}: the data-mapping enum conversion,
 * the type tag, and the inherited {@link Imageplot} scale/translate.
 */
public class GrayplotTest {

    @Test
    public void typeIsGrayplot() {
        assertEquals(Integer.valueOf(GraphicObjectProperties.__GO_GRAYPLOT__), new Grayplot().getType());
    }

    @Test
    public void defaultDataMappingIsDirect() {
        // DIRECT is ordinal 1.
        assertEquals(Integer.valueOf(1), new Grayplot().getDataMapping());
    }

    @Test
    public void dataMappingIntConversion() {
        Grayplot g = new Grayplot();
        assertEquals(UpdateStatus.Success, g.setDataMapping(0));
        assertEquals(Integer.valueOf(0), g.getDataMapping()); // SCALED
        assertEquals(UpdateStatus.Success, g.setDataMapping(1));
        assertEquals(Integer.valueOf(1), g.getDataMapping()); // DIRECT
    }

    @Test
    public void invalidDataMappingProducesNullEnum() {
        // Defect characterization: out-of-range index leaves a null enum, and the
        // integer getter then dereferences it.
        Grayplot g = new Grayplot();
        assertEquals(UpdateStatus.Success, g.setDataMapping(7));
        assertNull(g.getDataMappingAsEnum());
        assertThrows(NullPointerException.class, g::getDataMapping);
    }

    @Test
    public void inheritsImageplotDefaults() {
        Grayplot g = new Grayplot();
        assertArrayEquals(new Double[] {1.0, 1.0}, g.getScale());
        assertArrayEquals(new Double[] {0.5, 0.5}, g.getTranslate());
        assertEquals(UpdateStatus.Success, g.setScale(new Double[] {3.0, 4.0}));
        assertArrayEquals(new Double[] {3.0, 4.0}, g.getScale());
    }

    @Test
    public void propertyNameLookupRoundTrips() {
        Grayplot g = new Grayplot();
        Object mapping = g.getPropertyFromName(__GO_DATA_MAPPING__);
        assertNotNull(mapping);
        assertEquals(UpdateStatus.Success, g.setProperty(mapping, Integer.valueOf(0)));
        assertEquals(Integer.valueOf(0), g.getProperty(mapping));

        // A property owned by the Imageplot parent still resolves.
        Object scale = g.getPropertyFromName(__GO_MATPLOT_SCALE__);
        assertEquals(UpdateStatus.Success, g.setProperty(scale, new Double[] {2.0, 2.0}));
        assertArrayEquals(new Double[] {2.0, 2.0}, (Double[]) g.getProperty(scale));
    }
}
