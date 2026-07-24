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
import org.scilab.modules.graphic_objects.graphicObject.GraphicObject.UpdateStatus;

/**
 * Hermetic unit tests for {@link ClippableProperty}: the clip-state / clip-box /
 * clip-box-set holder that backs clipping on graphic objects.
 */
public class ClippablePropertyTest {

    @Test
    public void defaults() {
        ClippableProperty p = new ClippableProperty();
        assertEquals(ClipStateType.OFF, p.getClipState());
        assertFalse(p.getClipBoxSet());
        Double[] box = p.getClipBox();
        assertEquals(4, box.length);
        for (Double v : box) {
            assertEquals(Double.valueOf(0.0), v);
        }
    }

    @Test
    public void clipStateIntToEnum() {
        assertEquals(ClipStateType.OFF, ClipStateType.intToEnum(0));
        assertEquals(ClipStateType.CLIPGRF, ClipStateType.intToEnum(1));
        assertEquals(ClipStateType.ON, ClipStateType.intToEnum(2));
        assertNull(ClipStateType.intToEnum(3));
        assertNull(ClipStateType.intToEnum(-1));
    }

    @Test
    public void clipStateOrdinalsMatchScilabEncoding() {
        assertEquals(0, ClipStateType.OFF.ordinal());
        assertEquals(1, ClipStateType.CLIPGRF.ordinal());
        assertEquals(2, ClipStateType.ON.ordinal());
    }

    @Test
    public void getClipBoxReturnsDefensiveCopy() {
        ClippableProperty p = new ClippableProperty();
        Double[] box = p.getClipBox();
        box[0] = 123.0;
        // Mutating the returned array must not affect the property.
        assertEquals(Double.valueOf(0.0), p.getClipBox()[0]);
    }

    @Test
    public void firstSetClipBoxAlwaysSucceedsAndMarksSet() {
        ClippableProperty p = new ClippableProperty();
        assertEquals(UpdateStatus.Success,
                     p.setClipBox(new Double[] {1.0, 2.0, 3.0, 4.0}));
        assertTrue(p.getClipBoxSet());
        Double[] box = p.getClipBox();
        assertArrayEquals(new Double[] {1.0, 2.0, 3.0, 4.0}, box);
    }

    @Test
    public void repeatSetClipBoxDetectsChange() {
        ClippableProperty p = new ClippableProperty();
        p.setClipBox(new Double[] {1.0, 2.0, 3.0, 4.0});
        // Identical values on a second call: no change.
        assertEquals(UpdateStatus.NoChange,
                     p.setClipBox(new Double[] {1.0, 2.0, 3.0, 4.0}));
        // A differing element makes it a change.
        assertEquals(UpdateStatus.Success,
                     p.setClipBox(new Double[] {1.0, 2.0, 3.0, 9.0}));
        assertEquals(Double.valueOf(9.0), p.getClipBox()[3]);
    }

    @Test
    public void clipBoxSetChangeDetection() {
        ClippableProperty p = new ClippableProperty();
        assertEquals(UpdateStatus.NoChange, p.setClipBoxSet(false));
        assertEquals(UpdateStatus.Success, p.setClipBoxSet(true));
        assertTrue(p.getClipBoxSet());
        assertEquals(UpdateStatus.NoChange, p.setClipBoxSet(true));
    }

    @Test
    public void setClipStateOnWithoutBoxIsDemotedToClipgrf() {
        ClippableProperty p = new ClippableProperty();
        // ON requested while the clip box has never been set: demoted to CLIPGRF.
        assertEquals(UpdateStatus.Success, p.setClipState(ClipStateType.ON));
        assertEquals(ClipStateType.CLIPGRF, p.getClipState());
        // Requesting ON again keeps demoting to CLIPGRF: no further change.
        assertEquals(UpdateStatus.NoChange, p.setClipState(ClipStateType.ON));
    }

    @Test
    public void setClipStateOnWithBoxStaysOn() {
        ClippableProperty p = new ClippableProperty();
        p.setClipBoxSet(true);
        assertEquals(UpdateStatus.Success, p.setClipState(ClipStateType.ON));
        assertEquals(ClipStateType.ON, p.getClipState());
    }

    @Test
    public void setClipStateChangeDetection() {
        ClippableProperty p = new ClippableProperty();
        // Already OFF.
        assertEquals(UpdateStatus.NoChange, p.setClipState(ClipStateType.OFF));
        assertEquals(UpdateStatus.Success, p.setClipState(ClipStateType.CLIPGRF));
        assertEquals(UpdateStatus.NoChange, p.setClipState(ClipStateType.CLIPGRF));
        assertEquals(UpdateStatus.Success, p.setClipState(ClipStateType.OFF));
    }

    @Test
    public void copyConstructorDuplicatesStateIndependently() {
        ClippableProperty src = new ClippableProperty();
        src.setClipBox(new Double[] {5.0, 6.0, 7.0, 8.0});
        src.setClipState(ClipStateType.ON); // box set, so stays ON

        ClippableProperty copy = new ClippableProperty(src);
        assertEquals(ClipStateType.ON, copy.getClipState());
        assertTrue(copy.getClipBoxSet());
        assertArrayEquals(new Double[] {5.0, 6.0, 7.0, 8.0}, copy.getClipBox());

        // Mutating the copy leaves the source untouched.
        copy.setClipBox(new Double[] {0.0, 0.0, 0.0, 0.0});
        assertEquals(Double.valueOf(5.0), src.getClipBox()[0]);
    }

    /**
     * Characterization: the internal clip box is a fixed length-4 array, so
     * passing the "6-element array" mentioned in the field docs overruns it.
     */
    @Test
    public void setClipBoxWithSixElementsThrows() {
        ClippableProperty p = new ClippableProperty();
        assertThrows(ArrayIndexOutOfBoundsException.class,
        () -> p.setClipBox(new Double[] {1.0, 2.0, 3.0, 4.0, 5.0, 6.0}));
    }

    /**
     * Characterization: {@code intToEnum} switches on an unboxed int, so a null
     * argument throws a NullPointerException.
     */
    @Test
    public void intToEnumNullThrows() {
        assertThrows(NullPointerException.class, () -> ClipStateType.intToEnum(null));
    }
}
