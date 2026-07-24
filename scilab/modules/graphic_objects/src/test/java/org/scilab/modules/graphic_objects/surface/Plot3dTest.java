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
 * Hermetic unit tests for {@link Plot3d}. Plot3d adds no data of its own beyond
 * a type tag, so the tests confirm the tag and that the inherited {@link Surface}
 * behaviour is reachable through the concrete subclass.
 */
public class Plot3dTest {

    @Test
    public void typeIsPlot3d() {
        assertEquals(Integer.valueOf(GraphicObjectProperties.__GO_PLOT3D__), new Plot3d().getType());
    }

    @Test
    public void inheritsSurfaceDefaults() {
        Plot3d p = new Plot3d();
        assertFalse(p.getSurfaceMode());
        assertEquals(Integer.valueOf(0), p.getColorMode());
        assertEquals(Integer.valueOf(0), p.getColorFlag());
        assertEquals(Integer.valueOf(0), p.getHiddenColor());
        assertTrue(p.getColorMaterialMode());
        assertEquals(2.0, p.getMaterialShininess(), 0.0);
    }

    @Test
    public void inheritedSettersRoundTrip() {
        Plot3d p = new Plot3d();
        assertEquals(UpdateStatus.Success, p.setColorMode(9));
        assertEquals(Integer.valueOf(9), p.getColorMode());
        assertEquals(UpdateStatus.Success, p.setSurfaceMode(true));
        assertTrue(p.getSurfaceMode());
    }

    @Test
    public void surfacePropertyLookupWorksThroughPlot3d() {
        Plot3d p = new Plot3d();
        Object colorFlag = p.getPropertyFromName(__GO_COLOR_FLAG__);
        assertNotNull(colorFlag);
        assertEquals(UpdateStatus.Success, p.setProperty(colorFlag, Integer.valueOf(2)));
        assertEquals(Integer.valueOf(2), p.getProperty(colorFlag));
    }

    @Test
    public void distinctInstancesDoNotShareMaterialState() {
        Plot3d a = new Plot3d();
        Plot3d b = new Plot3d();
        a.setMaterialShininess(50.0);
        assertEquals(50.0, a.getMaterialShininess(), 0.0);
        assertEquals(2.0, b.getMaterialShininess(), 0.0);
    }
}
