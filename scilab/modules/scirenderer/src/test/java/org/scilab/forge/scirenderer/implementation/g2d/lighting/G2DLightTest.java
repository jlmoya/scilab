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
import org.scilab.forge.scirenderer.shapes.appearance.Color;
import org.scilab.forge.scirenderer.tranformations.Vector3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hermetic unit tests for {@link G2DLight}, the pure value object holding one
 * light's state for the software renderer.
 */
public class G2DLightTest {

    @Test
    public void indexIsStored() {
        assertEquals(5, new G2DLight(5).getIndex());
    }

    @Test
    public void lightIsDisabledByDefault() {
        assertFalse(new G2DLight(0).isEnable());
    }

    @Test
    public void enableFlagRoundTrips() {
        G2DLight light = new G2DLight(0);
        light.setEnable(true);
        assertTrue(light.isEnable());
        light.setEnable(false);
        assertFalse(light.isEnable());
    }

    @Test
    public void colorsDefaultToBlack() {
        G2DLight light = new G2DLight(0);
        assertBlack(light.getAmbientColor());
        assertBlack(light.getDiffuseColor());
        assertBlack(light.getSpecularColor());
    }

    @Test
    public void colorSettersRoundTrip() {
        G2DLight light = new G2DLight(0);
        Color red = new Color(1f, 0f, 0f);
        light.setAmbientColor(red);
        light.setDiffuseColor(red);
        light.setSpecularColor(red);
        assertSame(red, light.getAmbientColor());
        assertSame(red, light.getDiffuseColor());
        assertSame(red, light.getSpecularColor());
    }

    @Test
    public void nullColorsAreIgnored() {
        G2DLight light = new G2DLight(0);
        Color red = new Color(1f, 0f, 0f);
        light.setAmbientColor(red);
        light.setAmbientColor(null);
        assertSame(red, light.getAmbientColor(), "a null colour must not overwrite the current one");
    }

    @Test
    public void defaultSpotDirectionAndAngle() {
        G2DLight light = new G2DLight(0);
        Vector3d spot = light.getSpotDirection();
        assertEquals(0.0, spot.getX(), 0.0);
        assertEquals(0.0, spot.getY(), 0.0);
        assertEquals(-1.0, spot.getZ(), 0.0);
        assertEquals(180f, light.getSpotAngle(), 0f);
    }

    @Test
    public void spotDirectionAndAngleRoundTrip() {
        G2DLight light = new G2DLight(0);
        Vector3d spot = new Vector3d(1, 0, 0);
        light.setSpotDirection(spot);
        assertSame(spot, light.getSpotDirection());
        light.setSpotAngle(45f);
        assertEquals(45f, light.getSpotAngle(), 0f);
    }

    @Test
    public void aFreshLightIsAPointLight() {
        assertTrue(new G2DLight(0).isPoint());
    }

    @Test
    public void settingADirectionMakesItDirectionalAndNormalisesIt() {
        G2DLight light = new G2DLight(0);
        light.setDirection(new Vector3d(0, 0, -5));
        assertFalse(light.isPoint());
        Vector3d dir = light.getDirection();
        // (0,0,-5) normalised is the unit vector (0,0,-1).
        assertEquals(0.0, dir.getX(), 1e-9);
        assertEquals(0.0, dir.getY(), 1e-9);
        assertEquals(-1.0, dir.getZ(), 1e-9);
    }

    @Test
    public void settingAPositionRevertsToAPointLight() {
        G2DLight light = new G2DLight(0);
        light.setDirection(new Vector3d(0, 0, -1));
        assertFalse(light.isPoint());

        Vector3d pos = new Vector3d(1, 2, 3);
        light.setPosition(pos);
        assertTrue(light.isPoint());
        assertSame(pos, light.getPosition());
    }

    @Test
    public void nullPositionAndDirectionAreIgnored() {
        G2DLight light = new G2DLight(0);
        Vector3d pos = light.getPosition();
        light.setPosition(null);
        assertSame(pos, light.getPosition());
        Vector3d dir = light.getDirection();
        light.setDirection(null);
        assertSame(dir, light.getDirection());
    }

    private static void assertBlack(Color c) {
        assertEquals(0, c.getRed());
        assertEquals(0, c.getGreen());
        assertEquals(0, c.getBlue());
    }
}
