/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Scilab
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 *
 */

package org.scilab.forge.scirenderer.implementation.g2d.lighting;

import org.junit.jupiter.api.Test;
import org.scilab.forge.scirenderer.lightning.Light;
import org.scilab.forge.scirenderer.shapes.appearance.Material;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hermetic unit tests for the display-independent surface of
 * {@link G2DLightManager}: light-slot management, the lighting-enabled flag and
 * the material accessor. The camera/vertex/normal transform helpers require a
 * live {@code G2DDrawingTools} pipeline and are out of scope; a {@code null}
 * drawing-tools reference is enough for everything tested here.
 */
public class G2DLightManagerTest {

    @Test
    public void thereAreSixteenLightSlots() {
        assertEquals(16, new G2DLightManager(null).getLightNumber());
    }

    @Test
    public void getLightLazilyCreatesAG2DLightWithTheRequestedIndex() {
        G2DLightManager manager = new G2DLightManager(null);
        Light light = manager.getLight(3);
        assertNotNull(light);
        assertInstanceOf(G2DLight.class, light);
        assertEquals(3, light.getIndex());
    }

    @Test
    public void getLightReturnsTheSameCachedInstance() {
        G2DLightManager manager = new G2DLightManager(null);
        assertSame(manager.getLight(0), manager.getLight(0));
    }

    @Test
    public void outOfRangeIndicesReturnNull() {
        G2DLightManager manager = new G2DLightManager(null);
        assertNull(manager.getLight(-1));
        assertNull(manager.getLight(16));
        assertNotNull(manager.getLight(15), "index 15 is the last valid slot");
    }

    @Test
    public void lightingIsDisabledByDefaultAndRoundTrips() {
        G2DLightManager manager = new G2DLightManager(null);
        assertFalse(manager.isLightningEnable());
        manager.setLightningEnable(true);
        assertTrue(manager.isLightningEnable());
        manager.setLightningEnable(false);
        assertFalse(manager.isLightningEnable());
    }

    @Test
    public void materialRoundTrips() {
        G2DLightManager manager = new G2DLightManager(null);
        assertNull(manager.getMaterial());
        Material material = new Material();
        manager.setMaterial(material);
        assertSame(material, manager.getMaterial());
    }
}
