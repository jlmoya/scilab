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

package org.scilab.modules.graphic_objects.contouredObject;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import org.scilab.modules.graphic_objects.contouredObject.ClippableContouredObject.ClippableContouredObjectPropertyType;
import org.scilab.modules.graphic_objects.contouredObject.ContouredObject.ContouredObjectPropertyType;
import org.scilab.modules.graphic_objects.graphicObject.ClippableProperty;
import org.scilab.modules.graphic_objects.graphicObject.ClippableProperty.ClipStateType;
import org.scilab.modules.graphic_objects.graphicObject.ClippableProperty.ClippablePropertyType;
import org.scilab.modules.graphic_objects.graphicObject.GraphicObject.GraphicObjectPropertyType;
import org.scilab.modules.graphic_objects.graphicObject.GraphicObject.UpdateStatus;
import org.scilab.modules.graphic_objects.graphicObject.Visitor;

import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_CLIP_PROPERTY__;
import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_CLIP_STATE__;

/**
 * Hermetic unit tests for the abstract {@link ClippableContouredObject}: a
 * {@link ContouredObject} that also carries a {@link ClippableProperty}.
 */
public class ClippableContouredObjectTest {

    private static final class Stub extends ClippableContouredObject {
        public Integer getType() {
            return -1;
        }

        public void accept(Visitor visitor) {
            // no-op
        }
    }

    @Test
    public void defaults() {
        Stub o = new Stub();
        assertNotNull(o.getClipProperty());
        assertEquals(ClipStateType.OFF, o.getClipStateAsEnum());
        assertEquals(Integer.valueOf(0), o.getClipState());
        assertFalse(o.getClipBoxSet());
        assertEquals(4, o.getClipBox().length);
    }

    @Test
    public void setClipStateOnWithoutBoxIsDemotedToClipgrf() {
        Stub o = new Stub();
        assertEquals(UpdateStatus.Success, o.setClipState(2));
        assertEquals(ClipStateType.CLIPGRF, o.getClipStateAsEnum());
        assertEquals(Integer.valueOf(1), o.getClipState());
    }

    @Test
    public void setClipStateOnWithBoxStaysOn() {
        Stub o = new Stub();
        o.setClipBoxSet(true);
        o.setClipState(2);
        assertEquals(Integer.valueOf(2), o.getClipState());
    }

    @Test
    public void setClipBoxAlwaysReportsSuccessAndRoundTrips() {
        Stub o = new Stub();
        assertEquals(UpdateStatus.Success, o.setClipBox(new Double[] {1.0, 2.0, 3.0, 4.0}));
        assertTrue(o.getClipBoxSet());
        assertArrayEquals(new Double[] {1.0, 2.0, 3.0, 4.0}, o.getClipBox());
        // This wrapper reports Success even when the underlying box is unchanged.
        assertEquals(UpdateStatus.Success, o.setClipBox(new Double[] {1.0, 2.0, 3.0, 4.0}));
    }

    @Test
    public void setClipPropertyReplacesBackingObject() {
        Stub o = new Stub();
        ClippableProperty replacement = new ClippableProperty();
        replacement.setClipBoxSet(true);
        assertEquals(UpdateStatus.Success, o.setClipProperty(replacement));
        assertSame(replacement, o.getClipProperty());
        assertTrue(o.getClipBoxSet());
    }

    @Test
    public void fastPropertyDispatchForClipMembers() {
        Stub o = new Stub();
        assertSame(o.getClipProperty(),
                   o.getProperty(ClippableContouredObjectPropertyType.CLIPPROPERTY));

        o.setClipBoxSet(true);
        o.setProperty(ClippablePropertyType.CLIPSTATE, 2);
        assertEquals(Integer.valueOf(2), o.getProperty(ClippablePropertyType.CLIPSTATE));
        assertEquals(Boolean.TRUE, o.getProperty(ClippablePropertyType.CLIPBOXSET));

        assertEquals(ClippablePropertyType.CLIPSTATE, o.getPropertyFromName(__GO_CLIP_STATE__));
        assertEquals(ClippableContouredObjectPropertyType.CLIPPROPERTY,
                     o.getPropertyFromName(__GO_CLIP_PROPERTY__));
    }

    @Test
    public void basePropertyDispatchDelegatesThroughContouredToGraphicObject() {
        Stub o = new Stub();
        // ContouredObject property.
        assertEquals(UpdateStatus.Success,
                     o.setProperty(ContouredObjectPropertyType.FILLMODE, true));
        assertTrue(o.getFillMode());
        // GraphicObject base property.
        o.setProperty(GraphicObjectPropertyType.TAG, "ccTag");
        assertEquals("ccTag", o.getTag());
    }

    /**
     * Characterization: {@link ClippableContouredObject#clone()} does not
     * duplicate the ClippableProperty, so the clone and the original share it
     * and observe each other's clip changes.
     */
    @Test
    public void cloneSharesClipProperty() {
        Stub o = new Stub();
        ClippableContouredObject clone = o.clone();
        assertSame(o.getClipProperty(), clone.getClipProperty());
        o.setClipBoxSet(true);
        assertTrue(clone.getClipBoxSet());
    }

    /**
     * Characterization: an out-of-range clip state resolves to a null enum,
     * which the ordinal-based integer accessor then dereferences.
     */
    @Test
    public void outOfRangeClipStateThenGetClipStateThrows() {
        Stub o = new Stub();
        o.setClipState(99);
        assertNull(o.getClipStateAsEnum());
        assertThrows(NullPointerException.class, () -> o.getClipState());
    }
}
