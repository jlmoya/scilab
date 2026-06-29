/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Scilab macOS/2027 modernization
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 */

package org.scilab.forge.scirenderer.implementation.bgfx.lighting;

import org.scilab.forge.scirenderer.lightning.Light;
import org.scilab.forge.scirenderer.shapes.appearance.Color;
import org.scilab.forge.scirenderer.tranformations.Vector3d;

/**
 * bgfx light: stores light state (first slice renders flat / vertex-colored, so the state is
 * recorded but not yet consumed by a lighting shader).
 */
public class BgfxLight implements Light {

    private final int index;
    private boolean enable;
    private Color ambientColor;
    private Color diffuseColor;
    private Color specularColor;
    private Vector3d position;
    private Vector3d direction;
    private Vector3d spotDirection;
    private float spotAngle;

    public BgfxLight(int index) {
        this.index = index;
    }

    @Override
    public boolean isEnable() {
        return enable;
    }

    @Override
    public void setEnable(boolean enable) {
        this.enable = enable;
    }

    @Override
    public Color getAmbientColor() {
        return ambientColor;
    }

    @Override
    public void setAmbientColor(Color color) {
        this.ambientColor = color;
    }

    @Override
    public Color getDiffuseColor() {
        return diffuseColor;
    }

    @Override
    public void setDiffuseColor(Color color) {
        this.diffuseColor = color;
    }

    @Override
    public Color getSpecularColor() {
        return specularColor;
    }

    @Override
    public void setSpecularColor(Color color) {
        this.specularColor = color;
    }

    @Override
    public Vector3d getPosition() {
        return position;
    }

    @Override
    public void setPosition(Vector3d position) {
        this.position = position;
    }

    @Override
    public Vector3d getDirection() {
        return direction;
    }

    @Override
    public void setDirection(Vector3d direction) {
        this.direction = direction;
    }

    @Override
    public Vector3d getSpotDirection() {
        return spotDirection;
    }

    @Override
    public void setSpotDirection(Vector3d spotDirection) {
        this.spotDirection = spotDirection;
    }

    @Override
    public float getSpotAngle() {
        return spotAngle;
    }

    @Override
    public void setSpotAngle(float angle) {
        this.spotAngle = angle;
    }

    @Override
    public int getIndex() {
        return index;
    }
}
