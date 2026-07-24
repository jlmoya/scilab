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

package org.scilab.modules.graphic_objects.textObject;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import org.scilab.modules.graphic_objects.textObject.Text.Alignment;
import org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties;
import org.scilab.modules.graphic_objects.graphicObject.GraphicObject.UpdateStatus;

import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_FONT_ANGLE__;
import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_BOX__;

/**
 * Hermetic unit tests for {@link Text}: a clippable text object with a 3D
 * position, four corners, alignment, a box flag and a text-box mode.
 */
public class TextTest {

    private static final double EPS = 1e-12;

    @Test
    public void constructorDefaults() {
        Text t = new Text();
        assertEquals(0.0, t.getFontAngle(), EPS);
        assertArrayEquals(new Double[] {0.0, 0.0, 0.0}, t.getPosition());
        assertEquals(Alignment.CENTER, t.getAlignmentAsEnum());
        assertEquals(Integer.valueOf(1), t.getAlignment()); // CENTER ordinal
        assertFalse(t.getBox());
        assertFalse(t.getAutoDimensioning());
        assertArrayEquals(new Double[] {0.0, 0.0}, t.getTextBox());
        assertEquals(Integer.valueOf(0), t.getTextBoxMode()); // OFF ordinal
    }

    @Test
    public void typeIsText() {
        assertEquals(GraphicObjectProperties.__GO_TEXT__, new Text().getType());
    }

    @Test
    public void alignmentIntMappingAndRoundTrip() {
        Text t = new Text();
        assertEquals(UpdateStatus.Success, t.setAlignment(0));
        assertEquals(Alignment.LEFT, t.getAlignmentAsEnum());
        t.setAlignment(2);
        assertEquals(Alignment.RIGHT, t.getAlignmentAsEnum());
        assertEquals(Integer.valueOf(2), t.getAlignment());
    }

    @Test
    public void alignmentIntToEnumBounds() {
        assertEquals(Alignment.LEFT, Alignment.intToEnum(0));
        assertEquals(Alignment.CENTER, Alignment.intToEnum(1));
        assertEquals(Alignment.RIGHT, Alignment.intToEnum(2));
        assertNull(Alignment.intToEnum(3));
        assertNull(Alignment.intToEnum(-1));
    }

    @Test
    public void invalidAlignmentIsAcceptedAsNullThenCorruptsOrdinalGetter() {
        // Characterisation: setAlignment has NO validation. An out-of-range index
        // maps to a null enum, is stored, and reported as Success -- but the
        // ordinal getter then throws NPE on the null.
        Text t = new Text();
        assertEquals(UpdateStatus.Success, t.setAlignment(7));
        assertNull(t.getAlignmentAsEnum());
        assertThrows(NullPointerException.class, () -> t.getAlignment());
    }

    @Test
    public void positionRoundTrips() {
        Text t = new Text();
        assertEquals(UpdateStatus.Success, t.setPosition(new Double[] {1.5, -2.0, 3.25}));
        assertArrayEquals(new Double[] {1.5, -2.0, 3.25}, t.getPosition());
    }

    @Test
    public void cornersRequireExactlyTwelveValues() {
        Text t = new Text();
        assertEquals(UpdateStatus.NoChange, t.setCorners(new Double[] {1.0, 2.0, 3.0}));
        // A valid 12-element set is stored and read back triplet-by-triplet.
        Double[] twelve = new Double[12];
        for (int i = 0; i < 12; i++) {
            twelve[i] = (double) i;
        }
        assertEquals(UpdateStatus.Success, t.setCorners(twelve));
        assertArrayEquals(twelve, t.getCorners());
    }

    @Test
    public void textBoxRoundTrips() {
        Text t = new Text();
        assertEquals(UpdateStatus.Success, t.setTextBox(new Double[] {4.0, 5.0}));
        assertArrayEquals(new Double[] {4.0, 5.0}, t.getTextBox());
    }

    @Test
    public void textBoxModeIntMapping() {
        Text t = new Text();
        assertEquals(UpdateStatus.Success, t.setTextBoxMode(1));
        assertEquals(Integer.valueOf(1), t.getTextBoxMode()); // CENTERED
        t.setTextBoxMode(2);
        assertEquals(Integer.valueOf(2), t.getTextBoxMode()); // FILLED
    }

    @Test
    public void invalidTextBoxModeStoresNullThenOrdinalGetterThrows() {
        // Same no-validation pattern as alignment.
        Text t = new Text();
        assertEquals(UpdateStatus.Success, t.setTextBoxMode(9));
        assertThrows(NullPointerException.class, () -> t.getTextBoxMode());
    }

    @Test
    public void fontAngleBoxAndAutoDimensioningRoundTrip() {
        Text t = new Text();
        t.setFontAngle(1.25);
        assertEquals(1.25, t.getFontAngle(), EPS);
        t.setBox(true);
        assertTrue(t.getBox());
        t.setAutoDimensioning(true);
        assertTrue(t.getAutoDimensioning());
    }

    @Test
    public void propertyDispatchRoundTripsForFontAngleAndBox() {
        Text t = new Text();
        Object angleKey = t.getPropertyFromName(__GO_FONT_ANGLE__);
        t.setProperty(angleKey, Double.valueOf(0.5));
        assertEquals(0.5, (Double) t.getProperty(angleKey), EPS);

        Object boxKey = t.getPropertyFromName(__GO_BOX__);
        t.setProperty(boxKey, Boolean.TRUE);
        assertEquals(Boolean.TRUE, t.getProperty(boxKey));
    }

    @Test
    public void inheritedTextStateStillWorks() {
        Text t = new Text();
        assertTrue(t.isEmpty());
        t.setTextStrings(new String[] {"caption"});
        assertFalse(t.isEmpty());
        assertArrayEquals(new String[] {"caption"}, t.getTextStrings());
    }
}
