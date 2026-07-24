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

package org.scilab.modules.graphic_objects.lighting;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import org.scilab.modules.graphic_objects.graphicObject.GraphicObject.UpdateStatus;

/**
 * Hermetic unit tests for {@link Material}: a {@link ColorTriplet} extended with
 * a color-material flag and a shininess parameter, with its own non-black
 * default ambient/diffuse/specular colours.
 */
public class MaterialTest {

    private static final double EPS = 1e-12;

    @Test
    public void constructorSetsDocumentedDefaults() {
        Material m = new Material();
        assertTrue(m.getColorMaterialMode());
        assertEquals(2.0, m.getShininess(), EPS);
        // The Material constructor overrides ColorTriplet's all-black defaults.
        assertArrayEquals(new Double[] {0.0, 0.0, 0.0}, m.getAmbientColor());
        assertArrayEquals(new Double[] {1.0, 1.0, 1.0}, m.getDiffuseColor());
        assertArrayEquals(new Double[] {1.0, 1.0, 1.0}, m.getSpecularColor());
    }

    @Test
    public void colorMaterialModeReportsChangeVsNoChange() {
        Material m = new Material();
        assertEquals(UpdateStatus.NoChange, m.setColorMaterialMode(true));
        assertEquals(UpdateStatus.Success, m.setColorMaterialMode(false));
        assertFalse(m.getColorMaterialMode());
        assertEquals(UpdateStatus.NoChange, m.setColorMaterialMode(false));
    }

    @Test
    public void shininessReportsChangeVsNoChange() {
        Material m = new Material();
        assertEquals(UpdateStatus.NoChange, m.setShininess(2.0));
        assertEquals(UpdateStatus.Success, m.setShininess(16.0));
        assertEquals(16.0, m.getShininess(), EPS);
        assertEquals(UpdateStatus.NoChange, m.setShininess(16.0));
    }

    @Test
    public void inheritedColorSettersValidateRange() {
        Material m = new Material();
        // Because the constructor filled the channel arrays with real values, the
        // inherited setters no longer hit the ColorTriplet copy-constructor NPE.
        assertEquals(UpdateStatus.Success, m.setAmbientColor(new Double[] {0.2, 0.3, 0.4}));
        assertArrayEquals(new Double[] {0.2, 0.3, 0.4}, m.getAmbientColor());
        // Out-of-range and wrong-length are rejected.
        assertEquals(UpdateStatus.Fail, m.setAmbientColor(new Double[] {1.5, 0.0, 0.0}));
        assertEquals(UpdateStatus.Fail, m.setAmbientColor(new Double[] {0.1, 0.2}));
    }

    @Test
    public void settingSameDiffuseColourIsNoChange() {
        Material m = new Material();
        // Default diffuse is already white.
        assertEquals(UpdateStatus.NoChange, m.setDiffuseColor(new Double[] {1.0, 1.0, 1.0}));
        assertEquals(UpdateStatus.Success, m.setDiffuseColor(new Double[] {0.5, 0.5, 0.5}));
    }

    @Test
    public void propertyEnumHasTwoEntries() {
        assertEquals(2, Material.MaterialProperty.values().length);
    }
}
