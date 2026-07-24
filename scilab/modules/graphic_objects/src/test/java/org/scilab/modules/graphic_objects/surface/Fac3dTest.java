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

package org.scilab.modules.graphic_objects.surface;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import org.scilab.modules.graphic_objects.graphicObject.GraphicObject.UpdateStatus;
import org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties;

import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.*;

/**
 * Hermetic unit tests for {@link Fac3d}: data-mapping enum conversion,
 * colour-range / colour-data-bounds copy semantics, type tag, and the
 * inherited {@link Surface} behaviour.
 */
public class Fac3dTest {

    @Test
    public void typeIsFac3d() {
        assertEquals(Integer.valueOf(GraphicObjectProperties.__GO_FAC3D__), new Fac3d().getType());
    }

    @Test
    public void constructorDefaults() {
        Fac3d f = new Fac3d();
        // Default mapping is DIRECT which is ordinal 1.
        assertEquals(Integer.valueOf(1), f.getDataMapping());
        assertArrayEquals(new Double[] {0.0, 0.0}, f.getCDataBounds());
        assertArrayEquals(new Integer[] {0, 0}, f.getColorRange());
    }

    @Test
    public void dataMappingIntConversionScaledAndDirect() {
        Fac3d f = new Fac3d();
        assertEquals(UpdateStatus.Success, f.setDataMapping(0));
        assertEquals(Integer.valueOf(0), f.getDataMapping()); // SCALED
        assertEquals(UpdateStatus.Success, f.setDataMapping(1));
        assertEquals(Integer.valueOf(1), f.getDataMapping()); // DIRECT
    }

    @Test
    public void invalidDataMappingProducesNullEnum() {
        // Defect characterization: intToEnum returns null for out-of-range values,
        // so getDataMappingAsEnum() becomes null and getDataMapping() then NPEs.
        Fac3d f = new Fac3d();
        assertEquals(UpdateStatus.Success, f.setDataMapping(99));
        assertNull(f.getDataMappingAsEnum());
        assertThrows(NullPointerException.class, f::getDataMapping);
    }

    @Test
    public void cDataBoundsRoundTripAndReturnsDefensiveCopy() {
        Fac3d f = new Fac3d();
        assertEquals(UpdateStatus.Success, f.setCDataBounds(new Double[] {-1.5, 3.5}));
        assertArrayEquals(new Double[] {-1.5, 3.5}, f.getCDataBounds());

        Double[] a = f.getCDataBounds();
        Double[] b = f.getCDataBounds();
        assertNotSame(a, b, "each read must return a fresh array");
        a[0] = 999.0;
        assertEquals(-1.5, f.getCDataBounds()[0], 0.0, "mutating a returned array must not leak into the object");
    }

    @Test
    public void colorRangeRoundTripAndReturnsDefensiveCopy() {
        Fac3d f = new Fac3d();
        assertEquals(UpdateStatus.Success, f.setColorRange(new Integer[] {2, 8}));
        assertArrayEquals(new Integer[] {2, 8}, f.getColorRange());

        Integer[] a = f.getColorRange();
        a[1] = -100;
        assertArrayEquals(new Integer[] {2, 8}, f.getColorRange());
    }

    @Test
    public void propertyNameLookupRoundTrips() {
        Fac3d f = new Fac3d();

        Object mapping = f.getPropertyFromName(__GO_DATA_MAPPING__);
        assertEquals(UpdateStatus.Success, f.setProperty(mapping, Integer.valueOf(0)));
        assertEquals(Integer.valueOf(0), f.getProperty(mapping));

        Object bounds = f.getPropertyFromName(__GO_CDATA_BOUNDS__);
        assertEquals(UpdateStatus.Success, f.setProperty(bounds, new Double[] {1.0, 2.0}));
        assertArrayEquals(new Double[] {1.0, 2.0}, (Double[]) f.getProperty(bounds));

        Object range = f.getPropertyFromName(__GO_COLOR_RANGE__);
        assertEquals(UpdateStatus.Success, f.setProperty(range, new Integer[] {4, 5}));
        assertArrayEquals(new Integer[] {4, 5}, (Integer[]) f.getProperty(range));
    }

    @Test
    public void inheritsSurfaceDefaultsAndSetters() {
        Fac3d f = new Fac3d();
        assertFalse(f.getSurfaceMode());
        assertEquals(Integer.valueOf(0), f.getColorMode());
        assertEquals(UpdateStatus.Success, f.setSurfaceMode(true));
        assertTrue(f.getSurfaceMode());
        // Surface property lookup still works through the subclass.
        Object surfaceMode = f.getPropertyFromName(__GO_SURFACE_MODE__);
        assertEquals(Boolean.TRUE, f.getProperty(surfaceMode));
    }
}
