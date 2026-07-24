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

import org.scilab.modules.graphic_objects.graphicObject.ClippableProperty.ClipStateType;
import org.scilab.modules.graphic_objects.graphicObject.Visitor;
import org.scilab.modules.graphic_objects.graphicObject.GraphicObject.UpdateStatus;

import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_CLIP_STATE__;
import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_CLIP_BOX_SET__;

/**
 * Hermetic unit tests for the abstract {@link ClippableTextObject}, exercised
 * through a tiny concrete subclass. Covers the clip-state / clip-box behaviour
 * layered on top of {@link TextObject}.
 */
public class ClippableTextObjectTest {

    private static final double EPS = 1e-12;

    /** Minimal concrete subclass so the abstract base can be instantiated. */
    private static final class TestableClippableTextObject extends ClippableTextObject {
        @Override
        public void accept(Visitor visitor) { /* no-op for tests */ }
        @Override
        public Integer getType() {
            return 0;
        }
    }

    @Test
    public void defaultClippingIsOffWithUnsetBox() {
        TestableClippableTextObject o = new TestableClippableTextObject();
        assertEquals(ClipStateType.OFF, o.getClipStateAsEnum());
        assertEquals(Integer.valueOf(0), o.getClipState());
        assertFalse(o.getClipBoxSet());
        assertArrayEquals(new Double[] {0.0, 0.0, 0.0, 0.0}, o.getClipBox());
    }

    @Test
    public void alsoInheritsTextObjectStateFromSuperConstructor() {
        // The (implicit) super() must have initialised the FormattedText array.
        TestableClippableTextObject o = new TestableClippableTextObject();
        assertArrayEquals(new Integer[] {1, 1}, o.getTextArrayDimensions());
        assertTrue(o.isEmpty());
    }

    @Test
    public void requestingOnWithoutABoxDowngradesToClipgrf() {
        // Characterisation: setting ON (state 2) while the clip box has never been
        // set is downgraded to CLIPGRF (state 1) by the underlying ClippableProperty.
        TestableClippableTextObject o = new TestableClippableTextObject();
        assertEquals(UpdateStatus.Success, o.setClipState(2));
        assertEquals(ClipStateType.CLIPGRF, o.getClipStateAsEnum());
        assertEquals(Integer.valueOf(1), o.getClipState());
    }

    @Test
    public void onIsHonouredOnceABoxHasBeenSet() {
        TestableClippableTextObject o = new TestableClippableTextObject();
        assertEquals(UpdateStatus.Success, o.setClipBox(new Double[] {0.0, 0.0, 1.0, 1.0}));
        assertTrue(o.getClipBoxSet());
        assertEquals(UpdateStatus.Success, o.setClipState(2));
        assertEquals(ClipStateType.ON, o.getClipStateAsEnum());
    }

    @Test
    public void firstSetClipBoxStoresValuesAndFlipsTheSetFlag() {
        TestableClippableTextObject o = new TestableClippableTextObject();
        o.setClipBox(new Double[] {1.0, 2.0, 3.0, 4.0});
        assertTrue(o.getClipBoxSet());
        Double[] box = o.getClipBox();
        assertEquals(1.0, box[0], EPS);
        assertEquals(2.0, box[1], EPS);
        assertEquals(3.0, box[2], EPS);
        assertEquals(4.0, box[3], EPS);
    }

    @Test
    public void setClipStateAsEnumRoundTrips() {
        TestableClippableTextObject o = new TestableClippableTextObject();
        o.setClipBoxSet(true);
        assertEquals(UpdateStatus.Success, o.setClipStateAsEnum(ClipStateType.ON));
        assertEquals(ClipStateType.ON, o.getClipStateAsEnum());
    }

    @Test
    public void setClipBoxSetReportsChangeVsNoChange() {
        TestableClippableTextObject o = new TestableClippableTextObject();
        assertEquals(UpdateStatus.NoChange, o.setClipBoxSet(false));
        assertEquals(UpdateStatus.Success, o.setClipBoxSet(true));
        assertEquals(UpdateStatus.NoChange, o.setClipBoxSet(true));
    }

    @Test
    public void propertyDispatchRoundTripsForClipBoxSet() {
        TestableClippableTextObject o = new TestableClippableTextObject();
        Object key = o.getPropertyFromName(__GO_CLIP_BOX_SET__);
        assertEquals(UpdateStatus.Success, o.setProperty(key, Boolean.TRUE));
        assertEquals(Boolean.TRUE, o.getProperty(key));
    }

    @Test
    public void propertyDispatchReadsClipState() {
        TestableClippableTextObject o = new TestableClippableTextObject();
        Object key = o.getPropertyFromName(__GO_CLIP_STATE__);
        // Default OFF -> ordinal 0.
        assertEquals(Integer.valueOf(0), o.getProperty(key));
    }
}
