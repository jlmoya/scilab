/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Scilab / macOS 2027 fork
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 */

package org.scilab.forge.scirenderer.implementation.vulkan.lighting;

import org.scilab.forge.scirenderer.lightning.Light;
import org.scilab.forge.scirenderer.shapes.appearance.Color;
import org.scilab.forge.scirenderer.tranformations.Vector3d;

/**
 * Plain light holder. The DrawerVisitor configures lights unconditionally, so real objects must
 * exist — but the Vulkan motor does not evaluate lighting yet (a later milestone: Phong in the
 * fragment shader, the geometry already carries normals), so this only stores the state.
 */
public class VulkanLight implements Light {

    private final int index;
    private boolean isEnable;
    private Color ambientColor = new Color(0, 0, 0);
    private Color diffuseColor = new Color(0, 0, 0);
    private Color specularColor = new Color(0, 0, 0);
    private Vector3d position = new Vector3d(0, 0, 0);
    private Vector3d spotDirection = new Vector3d(0, 0, -1);
    private Vector3d direction = new Vector3d(0, 0, 0);
    private float spotAngle = 180;
    private boolean directional = true;   // Scilab's default light type is directional (0)

    public VulkanLight(int index) {
        this.index = index;
    }

    @Override
    public boolean isEnable() {
        return isEnable;
    }

    @Override
    public void setEnable(boolean isEnable) {
        this.isEnable = isEnable;
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
        this.directional = false;
    }

    @Override
    public Vector3d getDirection() {
        return direction;
    }

    @Override
    public void setDirection(Vector3d direction) {
        this.direction = direction;
        this.directional = true;
    }

    /** True for a positional (point) light — the motor uses per-vertex rays; false = directional. */
    public boolean isPoint() {
        return !directional;
    }

    @Override
    public Vector3d getSpotDirection() {
        return spotDirection;
    }

    @Override
    public void setSpotDirection(Vector3d direction) {
        this.spotDirection = direction;
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
