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

package org.scilab.modules.graphic_objects.graphicObject;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import org.scilab.modules.graphic_objects.graphicObject.ClippableProperty.ClipStateType;
import org.scilab.modules.graphic_objects.graphicObject.ClippableProperty.ClippablePropertyType;
import org.scilab.modules.graphic_objects.graphicObject.GraphicObject.GraphicObjectPropertyType;

import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_CLIP_PROPERTY__;
import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_CLIP_STATE__;

/**
 * Hermetic unit tests for the abstract {@link GraphicClippableObject}, exercised
 * through a minimal concrete stub. It wraps a {@link ClippableProperty} and adds
 * the integer-encoded clip-state accessors plus fast property dispatch.
 */
public class GraphicClippableObjectTest {

    private static final class Stub extends GraphicClippableObject {
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
        o.setClipState(2); // 2 == ON, but the box is unset -> CLIPGRF
        assertEquals(ClipStateType.CLIPGRF, o.getClipStateAsEnum());
        assertEquals(Integer.valueOf(1), o.getClipState());
    }

    @Test
    public void setClipStateOnWithBoxStaysOn() {
        Stub o = new Stub();
        o.setClipBoxSet(true);
        o.setClipState(2);
        assertEquals(ClipStateType.ON, o.getClipStateAsEnum());
        assertEquals(Integer.valueOf(2), o.getClipState());
    }

    @Test
    public void setClipBoxRoundTripsAndMarksSet() {
        Stub o = new Stub();
        o.setClipBox(new Double[] {1.0, 2.0, 3.0, 4.0});
        assertTrue(o.getClipBoxSet());
        assertArrayEquals(new Double[] {1.0, 2.0, 3.0, 4.0}, o.getClipBox());
    }

    @Test
    public void setClipPropertyReplacesBackingObject() {
        Stub o = new Stub();
        ClippableProperty replacement = new ClippableProperty();
        replacement.setClipBoxSet(true);
        o.setClipProperty(replacement);
        assertSame(replacement, o.getClipProperty());
        assertTrue(o.getClipBoxSet());
    }

    @Test
    public void fastPropertyDispatchForClipMembers() {
        Stub o = new Stub();
        o.setClipBoxSet(true);
        o.setClipState(2);
        assertEquals(Integer.valueOf(2), o.getProperty(ClippablePropertyType.CLIPSTATE));
        assertEquals(Boolean.TRUE, o.getProperty(ClippablePropertyType.CLIPBOXSET));

        // getPropertyFromName exposes the public clip-state key by name.
        assertEquals(ClippablePropertyType.CLIPSTATE, o.getPropertyFromName(__GO_CLIP_STATE__));
    }

    @Test
    public void clipPropertyKeyRoundTripsThroughGetProperty() {
        Stub o = new Stub();
        // The CLIPPROPERTY key enum is package-private; fetch it by name and
        // feed it back to prove the dispatch returns the backing property.
        Object key = o.getPropertyFromName(__GO_CLIP_PROPERTY__);
        assertNotNull(key);
        assertSame(o.getClipProperty(), o.getProperty(key));
    }

    @Test
    public void unknownPropertyDelegatesToSuper() {
        Stub o = new Stub();
        // TAG is a base-class property: dispatch must fall through to super.
        o.setProperty(GraphicObjectPropertyType.TAG, "clipTag");
        assertEquals("clipTag", o.getTag());
        assertEquals("clipTag", o.getProperty(GraphicObjectPropertyType.TAG));
        assertEquals(Boolean.TRUE, o.getProperty(GraphicObjectPropertyType.VISIBLE));
    }

    /**
     * Characterization: an out-of-range clip state resolves to a null enum
     * (via {@code ClipStateType.intToEnum}), which is stored as-is; the integer
     * accessor then dereferences null when computing the ordinal.
     */
    @Test
    public void outOfRangeClipStateThenGetClipStateThrows() {
        Stub o = new Stub();
        o.setClipState(99);
        assertNull(o.getClipStateAsEnum());
        assertThrows(NullPointerException.class, () -> o.getClipState());
    }
}
