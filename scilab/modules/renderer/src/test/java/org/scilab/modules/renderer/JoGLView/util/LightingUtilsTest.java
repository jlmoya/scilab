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

package org.scilab.modules.renderer.JoGLView.util;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.scilab.forge.scirenderer.shapes.appearance.Material;

/**
 * Hermetic unit tests for {@link LightingUtils#getMaterial}, the pure
 * converter that turns a graphic_objects lighting {@code Material} (a plain
 * {@code ColorTriplet}-backed value object) into a scirenderer
 * {@link Material}. Neither type touches the graphic controller or the GL
 * pipeline, so the conversion runs without a display.
 *
 * <p>The {@code null}-manager guard branches of {@code setLightingEnable}
 * and {@code setupLights} are covered too; the light-wiring paths that need
 * a live {@code LightManager}/{@code Axes} are intentionally out of scope.
 *
 * <p>Colour precision note: a scirenderer {@code Color} is a
 * {@code java.awt.Color}, which quantises each channel to 8 bits, hence the
 * 8-bit tolerance on fractional channels.
 */
class LightingUtilsTest {

    private static final float BIT8 = 1.0f / 255.0f;

    /** A fresh graphic_objects material carries documented defaults. */
    private static org.scilab.modules.graphic_objects.lighting.Material defaultSciMaterial() {
        return new org.scilab.modules.graphic_objects.lighting.Material();
    }

    @Test
    void getMaterialReturnsANonNullMaterialWithNonNullColours() {
        Material mtl = LightingUtils.getMaterial(defaultSciMaterial());
        assertNotNull(mtl);
        assertNotNull(mtl.getAmbientColor());
        assertNotNull(mtl.getDiffuseColor());
        assertNotNull(mtl.getSpecularColor());
    }

    @Test
    void getMaterialMapsTheDefaultTripletColoursExactly() {
        // graphic_objects default: ambient (0,0,0), diffuse (1,1,1),
        // specular (1,1,1) -> those primary values are exactly representable.
        Material mtl = LightingUtils.getMaterial(defaultSciMaterial());

        assertEquals(0.0f, mtl.getAmbientColor().getRedAsFloat(), 0.0f);
        assertEquals(0.0f, mtl.getAmbientColor().getGreenAsFloat(), 0.0f);
        assertEquals(0.0f, mtl.getAmbientColor().getBlueAsFloat(), 0.0f);

        assertEquals(1.0f, mtl.getDiffuseColor().getRedAsFloat(), 0.0f);
        assertEquals(1.0f, mtl.getDiffuseColor().getGreenAsFloat(), 0.0f);
        assertEquals(1.0f, mtl.getDiffuseColor().getBlueAsFloat(), 0.0f);

        assertEquals(1.0f, mtl.getSpecularColor().getRedAsFloat(), 0.0f);
    }

    @Test
    void getMaterialCarriesTheColorMaterialFlagAndShininess() {
        // Defaults: colour-material enabled, shininess 2.0.
        Material mtl = LightingUtils.getMaterial(defaultSciMaterial());
        assertTrue(mtl.isColorMaterialEnable());
        assertEquals(2.0f, mtl.getShininess(), 0.0f);
    }

    @Test
    void getMaterialPropagatesCustomisedSourceProperties() {
        org.scilab.modules.graphic_objects.lighting.Material src = defaultSciMaterial();
        src.setColorMaterialMode(false);
        src.setShininess(7.5);
        src.setDiffuseColor(new Double[] {0.25, 0.5, 0.75});

        Material mtl = LightingUtils.getMaterial(src);

        assertFalse(mtl.isColorMaterialEnable());
        assertEquals(7.5f, mtl.getShininess(), 0.0f);
        assertEquals(0.25f, mtl.getDiffuseColor().getRedAsFloat(), BIT8);
        assertEquals(0.5f, mtl.getDiffuseColor().getGreenAsFloat(), BIT8);
        assertEquals(0.75f, mtl.getDiffuseColor().getBlueAsFloat(), BIT8);
    }

    @Test
    void setLightingEnableWithNullManagerIsANoOp() {
        // The null guard must return before dereferencing the manager.
        assertDoesNotThrow(() -> LightingUtils.setLightingEnable(null, Boolean.TRUE));
        assertDoesNotThrow(() -> LightingUtils.setLightingEnable(null, Boolean.FALSE));
    }

    @Test
    void setupLightsWithNullManagerIsANoOp() {
        // manager == null short-circuits before the axes is ever touched.
        assertDoesNotThrow(() -> LightingUtils.setupLights(null, null));
    }
}
