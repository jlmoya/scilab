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

package org.scilab.forge.scirenderer.shapes.appearance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hermetic unit tests for {@link Material}, the lighting material value holder.
 */
public class MaterialTest {

    @Test
    public void freshMaterialHasNullColorsAndZeroShininess() {
        Material m = new Material();
        assertNull(m.getAmbientColor());
        assertNull(m.getDiffuseColor());
        assertNull(m.getSpecularColor());
        assertEquals(0f, m.getShininess(), 0f);
    }

    @Test
    public void colorMaterialIsEnabledByDefault() {
        assertTrue(new Material().isColorMaterialEnable());
    }

    @Test
    public void settersRoundTrip() {
        Material m = new Material();
        Color ambient = new Color(0.1f, 0.1f, 0.1f);
        Color diffuse = new Color(0.2f, 0.2f, 0.2f);
        Color specular = new Color(0.3f, 0.3f, 0.3f);

        m.setAmbientColor(ambient);
        m.setDiffuseColor(diffuse);
        m.setSpecularColor(specular);
        m.setShininess(64f);
        m.setColorMaterialEnable(false);

        assertSame(ambient, m.getAmbientColor());
        assertSame(diffuse, m.getDiffuseColor());
        assertSame(specular, m.getSpecularColor());
        assertEquals(64f, m.getShininess(), 0f);
        assertFalse(m.isColorMaterialEnable());
    }
}
