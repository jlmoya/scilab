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
import org.scilab.modules.graphic_objects.graphicObject.Visitor;
import org.scilab.modules.graphic_objects.lighting.Material;

import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.*;

/**
 * Hermetic unit tests for the abstract {@link Surface} class. Because Surface is
 * abstract (its {@code accept} and {@code getType} are inherited abstract), the
 * tests exercise it through a minimal concrete stub subclass defined below.
 */
public class SurfaceTest {

    /** Minimal concrete Surface used only to instantiate the abstract class. */
    private static final class SurfaceStub extends Surface {
        @Override
        public void accept(Visitor visitor) {
            // no-op: rendering visitors are out of scope for a hermetic unit test
        }
        @Override
        public Integer getType() {
            return -1;
        }
    }

    private static Surface newSurface() {
        return new SurfaceStub();
    }

    @Test
    public void constructorInstallsDocumentedDefaults() {
        Surface s = newSurface();
        assertFalse(s.getSurfaceMode());
        assertEquals(Integer.valueOf(0), s.getColorMode());
        assertEquals(Integer.valueOf(0), s.getColorFlag());
        assertEquals(Integer.valueOf(0), s.getHiddenColor());
        assertNotNull(s.getMaterial());
    }

    @Test
    public void materialDefaultsAreExposedThroughSurface() {
        Surface s = newSurface();
        // Material() default: color-material on, shininess 2.0, ambient 0, diffuse/specular 1.
        assertTrue(s.getColorMaterialMode());
        assertEquals(2.0, s.getMaterialShininess(), 0.0);
        assertArrayEquals(new Double[] {0.0, 0.0, 0.0}, s.getMaterialAmbientColor());
        assertArrayEquals(new Double[] {1.0, 1.0, 1.0}, s.getMaterialDiffuseColor());
        assertArrayEquals(new Double[] {1.0, 1.0, 1.0}, s.getMaterialSpecularColor());
    }

    @Test
    public void scalarSettersRoundTripAndReturnSuccess() {
        Surface s = newSurface();
        assertEquals(UpdateStatus.Success, s.setSurfaceMode(true));
        assertTrue(s.getSurfaceMode());
        assertEquals(UpdateStatus.Success, s.setColorMode(5));
        assertEquals(Integer.valueOf(5), s.getColorMode());
        assertEquals(UpdateStatus.Success, s.setColorFlag(3));
        assertEquals(Integer.valueOf(3), s.getColorFlag());
        assertEquals(UpdateStatus.Success, s.setHiddenColor(7));
        assertEquals(Integer.valueOf(7), s.getHiddenColor());
    }

    @Test
    public void colorMaterialModeReportsChangeVsNoChange() {
        Surface s = newSurface();
        // Default is true -> setting true again is a NoChange.
        assertEquals(UpdateStatus.NoChange, s.setColorMaterialMode(true));
        assertEquals(UpdateStatus.Success, s.setColorMaterialMode(false));
        assertFalse(s.getColorMaterialMode());
    }

    @Test
    public void shininessReportsChangeVsNoChange() {
        Surface s = newSurface();
        assertEquals(UpdateStatus.NoChange, s.setMaterialShininess(2.0));
        assertEquals(UpdateStatus.Success, s.setMaterialShininess(9.5));
        assertEquals(9.5, s.getMaterialShininess(), 0.0);
    }

    @Test
    public void ambientColorValidationMirrorsMaterialRules() {
        Surface s = newSurface();
        assertEquals(UpdateStatus.Success, s.setMaterialAmbientColor(new Double[] {0.5, 0.5, 0.5}));
        assertArrayEquals(new Double[] {0.5, 0.5, 0.5}, s.getMaterialAmbientColor());
        // Out of [0,1] range is rejected.
        assertEquals(UpdateStatus.Fail, s.setMaterialAmbientColor(new Double[] {2.0, 0.0, 0.0}));
        // Wrong cardinality is rejected.
        assertEquals(UpdateStatus.Fail, s.setMaterialAmbientColor(new Double[] {0.1, 0.2}));
    }

    @Test
    public void setMaterialIgnoresNullButAcceptsReplacement() {
        Surface s = newSurface();
        Material original = s.getMaterial();
        // null is silently ignored: the existing material is retained.
        assertEquals(UpdateStatus.Success, s.setMaterial(null));
        assertSame(original, s.getMaterial());

        Material replacement = new Material();
        replacement.setShininess(42.0);
        assertEquals(UpdateStatus.Success, s.setMaterial(replacement));
        assertSame(replacement, s.getMaterial());
        assertEquals(42.0, s.getMaterialShininess(), 0.0);
    }

    @Test
    public void propertyNameLookupRoundTripsScalarProperties() {
        Surface s = newSurface();

        Object mode = s.getPropertyFromName(__GO_SURFACE_MODE__);
        assertNotNull(mode);
        assertEquals(UpdateStatus.Success, s.setProperty(mode, Boolean.TRUE));
        assertEquals(Boolean.TRUE, s.getProperty(mode));

        Object colorMode = s.getPropertyFromName(__GO_COLOR_MODE__);
        assertEquals(UpdateStatus.Success, s.setProperty(colorMode, Integer.valueOf(11)));
        assertEquals(Integer.valueOf(11), s.getProperty(colorMode));

        Object hidden = s.getPropertyFromName(__GO_HIDDEN_COLOR__);
        assertEquals(UpdateStatus.Success, s.setProperty(hidden, Integer.valueOf(4)));
        assertEquals(Integer.valueOf(4), s.getProperty(hidden));
    }

    @Test
    public void propertyNameLookupRoundTripsMaterialProperties() {
        Surface s = newSurface();

        Object shininess = s.getPropertyFromName(__GO_MATERIAL_SHININESS__);
        assertEquals(UpdateStatus.Success, s.setProperty(shininess, Double.valueOf(7.0)));
        assertEquals(Double.valueOf(7.0), s.getProperty(shininess));

        Object diffuse = s.getPropertyFromName(__GO_DIFFUSECOLOR__);
        assertEquals(UpdateStatus.Success, s.setProperty(diffuse, new Double[] {0.2, 0.3, 0.4}));
        assertArrayEquals(new Double[] {0.2, 0.3, 0.4}, (Double[]) s.getProperty(diffuse));
    }

    @Test
    public void unknownPropertyDelegatesToSuperClass() {
        Surface s = newSurface();
        // A base GraphicObject property must still resolve and reflect its default.
        Object visible = s.getPropertyFromName(__GO_VISIBLE__);
        assertNotNull(visible);
        assertEquals(Boolean.TRUE, s.getProperty(visible));
    }
}
