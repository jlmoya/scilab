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
 * Hermetic unit tests for {@link ColorTriplet}: the ambient/diffuse/specular
 * colour holder used for lighting, with [0,1] validation and change tracking.
 */
public class ColorTripletTest {

    private static final double EPS = 1e-12;

    @Test
    public void defaultColoursAreBlack() {
        ColorTriplet c = new ColorTriplet();
        assertArrayEquals(new Double[] {0.0, 0.0, 0.0}, c.getAmbientColor());
        assertArrayEquals(new Double[] {0.0, 0.0, 0.0}, c.getDiffuseColor());
        assertArrayEquals(new Double[] {0.0, 0.0, 0.0}, c.getSpecularColor());
    }

    @Test
    public void settingANewValidColourSucceedsAndIsReadBack() {
        ColorTriplet c = new ColorTriplet();
        assertEquals(UpdateStatus.Success, c.setAmbientColor(new Double[] {0.1, 0.2, 0.3}));
        Double[] got = c.getAmbientColor();
        assertEquals(0.1, got[0], EPS);
        assertEquals(0.2, got[1], EPS);
        assertEquals(0.3, got[2], EPS);
    }

    @Test
    public void settingTheSameValueReportsNoChange() {
        ColorTriplet c = new ColorTriplet();
        // Default is already {0,0,0}.
        assertEquals(UpdateStatus.NoChange, c.setDiffuseColor(new Double[] {0.0, 0.0, 0.0}));
        assertEquals(UpdateStatus.Success, c.setDiffuseColor(new Double[] {0.5, 0.5, 0.5}));
        assertEquals(UpdateStatus.NoChange, c.setDiffuseColor(new Double[] {0.5, 0.5, 0.5}));
    }

    @Test
    public void wrongLengthFails() {
        ColorTriplet c = new ColorTriplet();
        assertEquals(UpdateStatus.Fail, c.setSpecularColor(new Double[] {0.5, 0.5}));
        assertEquals(UpdateStatus.Fail, c.setSpecularColor(new Double[] {0.1, 0.2, 0.3, 0.4}));
        // Rejected -> still the default black.
        assertArrayEquals(new Double[] {0.0, 0.0, 0.0}, c.getSpecularColor());
    }

    @Test
    public void outOfRangeComponentsFail() {
        ColorTriplet c = new ColorTriplet();
        assertEquals(UpdateStatus.Fail, c.setAmbientColor(new Double[] {1.5, 0.0, 0.0}));
        assertEquals(UpdateStatus.Fail, c.setAmbientColor(new Double[] {0.0, -0.1, 0.0}));
        // Boundary values 0.0 and 1.0 are accepted.
        assertEquals(UpdateStatus.Success, c.setAmbientColor(new Double[] {0.0, 1.0, 0.0}));
    }

    @Test
    public void getterReturnsDefensiveCopy() {
        ColorTriplet c = new ColorTriplet();
        c.setDiffuseColor(new Double[] {0.4, 0.4, 0.4});
        Double[] got = c.getDiffuseColor();
        got[0] = 0.9;
        // Mutating the returned array must not corrupt the internal state.
        assertEquals(0.4, c.getDiffuseColor()[0], EPS);
    }

    @Test
    public void copyConstructorThrowsBecauseChannelArraysStartNull() {
        // Characterisation of a real defect in the copy constructor: it allocates
        // ambient/diffuse/specular as new Double[3] (all-null elements) and then
        // calls the setters, whose change-check dereferences the CURRENT element
        // (ambient[0].equals(...)) before assigning it. Because the source's
        // default colours are valid, the setter passes its length/range guard and
        // reaches that dereference, hitting a NullPointerException. It therefore
        // fails for any source built through the public API.
        ColorTriplet src = new ColorTriplet();
        src.setAmbientColor(new Double[] {0.1, 0.2, 0.3});
        assertThrows(NullPointerException.class, () -> new ColorTriplet(src));

        // Even a pristine, default-constructed source triggers it (its {0,0,0}
        // channels are valid, so the guard does not short-circuit the setter).
        assertThrows(NullPointerException.class, () -> new ColorTriplet(new ColorTriplet()));
    }

    @Test
    public void propertyEnumHasThreeChannels() {
        assertEquals(3, ColorTriplet.ColorTripletProperty.values().length);
    }
}
