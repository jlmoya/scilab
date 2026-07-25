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

import org.scilab.modules.graphic_objects.lighting.Light.LightType;
import org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties;
import org.scilab.modules.graphic_objects.graphicObject.GraphicObject.UpdateStatus;

import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.*;

/**
 * Hermetic unit tests for {@link Light}: a graphic object with a position,
 * direction, type and an ambient/diffuse/specular colour triplet.
 */
public class LightTest {

    @Test
    public void constructorDefaults() {
        Light l = new Light();
        assertArrayEquals(new Double[] {0.0, 0.0, 1.0}, l.getPosition());
        assertArrayEquals(new Double[] {0.0, 0.0, 1.0}, l.getDirection());
        assertEquals(LightType.POINT, l.getLightType());
        assertEquals(Integer.valueOf(1), l.getLightTypeAsInteger()); // POINT ordinal
        // Constructor seeds ambient = dark grey, diffuse/specular = white.
        assertArrayEquals(new Double[] {0.1, 0.1, 0.1}, l.getAmbientColor());
        assertArrayEquals(new Double[] {1.0, 1.0, 1.0}, l.getDiffuseColor());
        assertArrayEquals(new Double[] {1.0, 1.0, 1.0}, l.getSpecularColor());
    }

    @Test
    public void typeIsLight() {
        assertEquals(GraphicObjectProperties.__GO_LIGHT__, new Light().getType());
    }

    @Test
    public void setPositionValidatesLengthAndTracksChange() {
        Light l = new Light();
        // Same as default -> NoChange.
        assertEquals(UpdateStatus.NoChange, l.setPosition(new Double[] {0.0, 0.0, 1.0}));
        assertEquals(UpdateStatus.Success, l.setPosition(new Double[] {1.0, 2.0, 3.0}));
        assertArrayEquals(new Double[] {1.0, 2.0, 3.0}, l.getPosition());
        // Wrong length -> Fail, state unchanged.
        assertEquals(UpdateStatus.Fail, l.setPosition(new Double[] {1.0, 2.0}));
        assertArrayEquals(new Double[] {1.0, 2.0, 3.0}, l.getPosition());
    }

    @Test
    public void setDirectionValidatesLengthAndTracksChange() {
        Light l = new Light();
        assertEquals(UpdateStatus.NoChange, l.setDirection(new Double[] {0.0, 0.0, 1.0}));
        assertEquals(UpdateStatus.Success, l.setDirection(new Double[] {-1.0, 0.0, 0.0}));
        assertArrayEquals(new Double[] {-1.0, 0.0, 0.0}, l.getDirection());
        assertEquals(UpdateStatus.Fail, l.setDirection(new Double[] {1.0, 2.0, 3.0, 4.0}));
    }

    @Test
    public void setLightTypeEnumTracksChange() {
        Light l = new Light();
        assertEquals(UpdateStatus.NoChange, l.setLightType(LightType.POINT));
        assertEquals(UpdateStatus.Success, l.setLightType(LightType.DIRECTIONAL));
        assertEquals(LightType.DIRECTIONAL, l.getLightType());
    }

    @Test
    public void setLightTypeAsIntegerValidatesRange() {
        Light l = new Light();
        // Default POINT (1) -> setting 1 again is a no-op.
        assertEquals(UpdateStatus.NoChange, l.setLightTypeAsInteger(1));
        assertEquals(UpdateStatus.Success, l.setLightTypeAsInteger(0));
        assertEquals(Integer.valueOf(0), l.getLightTypeAsInteger());
        // Out-of-range indices are rejected.
        assertEquals(UpdateStatus.Fail, l.setLightTypeAsInteger(5));
        assertEquals(UpdateStatus.Fail, l.setLightTypeAsInteger(-1));
    }

    @Test
    public void lightTypeIntToEnumBounds() {
        assertEquals(LightType.DIRECTIONAL, LightType.intToEnum(0));
        assertEquals(LightType.POINT, LightType.intToEnum(1));
        assertNull(LightType.intToEnum(2));
        assertNull(LightType.intToEnum(-1));
    }

    @Test
    public void colorSettersDelegateToTripletWithValidation() {
        Light l = new Light();
        assertEquals(UpdateStatus.Success, l.setAmbientColor(new Double[] {0.2, 0.3, 0.4}));
        assertArrayEquals(new Double[] {0.2, 0.3, 0.4}, l.getAmbientColor());
        // Out-of-range is rejected.
        assertEquals(UpdateStatus.Fail, l.setDiffuseColor(new Double[] {2.0, 0.0, 0.0}));
    }

    @Test
    public void propertyDispatchRoundTripsForPositionAndType() {
        Light l = new Light();
        assertEquals(UpdateStatus.Success,
                     l.setProperty(Light.LightProperty.POSITION, new Double[] {4.0, 5.0, 6.0}));
        assertArrayEquals(new Double[] {4.0, 5.0, 6.0},
                          (Double[]) l.getProperty(Light.LightProperty.POSITION));

        l.setProperty(Light.LightProperty.TYPE, Integer.valueOf(0));
        assertEquals(Integer.valueOf(0), l.getProperty(Light.LightProperty.TYPE));
    }

    @Test
    public void propertyDispatchRoundTripsForAmbientColour() {
        Light l = new Light();
        l.setProperty(ColorTriplet.ColorTripletProperty.AMBIENTCOLOR, new Double[] {0.5, 0.5, 0.5});
        assertArrayEquals(new Double[] {0.5, 0.5, 0.5},
                          (Double[]) l.getProperty(ColorTriplet.ColorTripletProperty.AMBIENTCOLOR));
    }

    @Test
    public void copyConstructorThrowsBecauseOfColorTripletCopyBug() {
        // Characterisation: Light's copy constructor builds its ColorTriplet via
        // the ColorTriplet copy constructor, which NPEs (its channel arrays start
        // as null elements and the setters dereference the current element). So
        // copying any Light -- even a default one -- throws NPE.
        Light src = new Light();
        assertThrows(NullPointerException.class, () -> new Light(src));
    }

    /* ---- getPropertyFromName-driven dispatch coverage ---- */

    @Test
    public void colourAndVectorPropertiesRoundTripViaGetPropertyFromName() {
        Light l = new Light();

        Object diffuse = l.getPropertyFromName(__GO_DIFFUSECOLOR__);
        l.setProperty(diffuse, new Double[] {0.2, 0.3, 0.4});
        assertArrayEquals(new Double[] {0.2, 0.3, 0.4}, (Double[]) l.getProperty(diffuse));

        Object specular = l.getPropertyFromName(__GO_SPECULARCOLOR__);
        l.setProperty(specular, new Double[] {0.6, 0.7, 0.8});
        assertArrayEquals(new Double[] {0.6, 0.7, 0.8}, (Double[]) l.getProperty(specular));

        Object ambient = l.getPropertyFromName(__GO_AMBIENTCOLOR__);
        assertArrayEquals(new Double[] {0.1, 0.1, 0.1}, (Double[]) l.getProperty(ambient));

        Object direction = l.getPropertyFromName(__GO_DIRECTION__);
        l.setProperty(direction, new Double[] {-1.0, 0.0, 0.0});
        assertArrayEquals(new Double[] {-1.0, 0.0, 0.0}, (Double[]) l.getProperty(direction));

        Object position = l.getPropertyFromName(__GO_POSITION__);
        l.setProperty(position, new Double[] {4.0, 5.0, 6.0});
        assertArrayEquals(new Double[] {4.0, 5.0, 6.0}, (Double[]) l.getProperty(position));

        Object type = l.getPropertyFromName(__GO_LIGHT_TYPE__);
        l.setProperty(type, Integer.valueOf(0)); // DIRECTIONAL
        assertEquals(Integer.valueOf(0), l.getProperty(type));
    }
}
