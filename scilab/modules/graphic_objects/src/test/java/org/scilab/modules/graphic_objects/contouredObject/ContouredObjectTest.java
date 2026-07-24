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

import org.scilab.modules.graphic_objects.contouredObject.ContouredObject.ContouredObjectPropertyType;
import org.scilab.modules.graphic_objects.contouredObject.Line.LinePropertyType;
import org.scilab.modules.graphic_objects.contouredObject.Line.LineType;
import org.scilab.modules.graphic_objects.contouredObject.Mark.MarkPropertyType;
import org.scilab.modules.graphic_objects.contouredObject.Mark.MarkSizeUnitType;
import org.scilab.modules.graphic_objects.graphicObject.GraphicObject.GraphicObjectPropertyType;
import org.scilab.modules.graphic_objects.graphicObject.GraphicObject.UpdateStatus;
import org.scilab.modules.graphic_objects.graphicObject.Visitor;

/**
 * Hermetic unit tests for the abstract {@link ContouredObject}, exercised
 * through a minimal concrete stub. It owns a {@link Line} and a {@link Mark}
 * plus per-vertex mark size/color arrays, and routes them through fast property
 * dispatch.
 */
public class ContouredObjectTest {

    private static final class Stub extends ContouredObject {
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
        assertFalse(o.getFillMode());
        assertEquals(Integer.valueOf(0), o.getBackground());
        assertEquals(Integer.valueOf(0), o.getMarkOffset());
        assertEquals(Integer.valueOf(1), o.getMarkStride());
        assertFalse(o.getSelected());
        assertEquals(Integer.valueOf(-3), o.getSelectedColor());
        assertEquals(0, o.getNumMarkSizes());
        assertEquals(0, o.getNumMarkForegrounds());
        assertEquals(0, o.getNumMarkBackgrounds());
        // Delegated line defaults.
        assertEquals(Integer.valueOf(-1), o.getLineColor());
        assertFalse(o.getLineMode());
        assertEquals(Double.valueOf(1.0), o.getLineThickness());
        assertEquals(Integer.valueOf(1), o.getLineStyle()); // SOLID -> 1
        // Delegated mark defaults.
        assertFalse(o.getMarkMode());
        assertEquals(Integer.valueOf(0), o.getMarkStyle());
        assertEquals(Integer.valueOf(0), o.getMarkSize());
        assertEquals(Integer.valueOf(0), o.getMarkSizeUnit()); // POINT -> 0
        assertEquals(Integer.valueOf(0), o.getMarkForeground());
        assertEquals(Integer.valueOf(0), o.getMarkBackground());
    }

    @Test
    public void fillModeChangeDetection() {
        Stub o = new Stub();
        assertEquals(UpdateStatus.NoChange, o.setFillMode(false));
        assertEquals(UpdateStatus.Success, o.setFillMode(true));
        assertTrue(o.getFillMode());
        assertEquals(UpdateStatus.NoChange, o.setFillMode(true));
    }

    @Test
    public void backgroundSetterAlwaysSucceeds() {
        Stub o = new Stub();
        assertEquals(UpdateStatus.Success, o.setBackground(9));
        assertEquals(Integer.valueOf(9), o.getBackground());
        // No change detection: same value still reports Success.
        assertEquals(UpdateStatus.Success, o.setBackground(9));
    }

    @Test
    public void selectedChangeDetection() {
        Stub o = new Stub();
        assertEquals(UpdateStatus.NoChange, o.setSelected(false));
        assertEquals(UpdateStatus.Success, o.setSelected(true));
        assertTrue(o.getSelected());
        assertEquals(UpdateStatus.NoChange, o.setSelected(true));
    }

    @Test
    public void markOffsetSetsPositiveDetectsNoChange() {
        Stub o = new Stub();
        assertEquals(UpdateStatus.Success, o.setMarkOffset(3));
        assertEquals(Integer.valueOf(3), o.getMarkOffset());
        assertEquals(UpdateStatus.NoChange, o.setMarkOffset(3));
    }

    /**
     * Characterization: a negative mark offset is clamped to 0, yet the setter
     * still reports Success because the change check runs against the raw
     * (unclamped) argument.
     */
    @Test
    public void negativeMarkOffsetClampsToZeroButReportsSuccess() {
        Stub o = new Stub();
        o.setMarkOffset(3);
        assertEquals(UpdateStatus.Success, o.setMarkOffset(-5));
        assertEquals(Integer.valueOf(0), o.getMarkOffset());
    }

    @Test
    public void markStrideSetsPositiveDetectsNoChange() {
        Stub o = new Stub();
        assertEquals(UpdateStatus.Success, o.setMarkStride(5));
        assertEquals(Integer.valueOf(5), o.getMarkStride());
        assertEquals(UpdateStatus.NoChange, o.setMarkStride(5));
    }

    /**
     * Characterization: a mark stride below 1 is clamped to 1, yet the setter
     * still reports Success.
     */
    @Test
    public void subUnitMarkStrideClampsToOneButReportsSuccess() {
        Stub o = new Stub();
        o.setMarkStride(5);
        assertEquals(UpdateStatus.Success, o.setMarkStride(0));
        assertEquals(Integer.valueOf(1), o.getMarkStride());
    }

    @Test
    public void lineDelegation() {
        Stub o = new Stub();
        assertEquals(UpdateStatus.Success, o.setLineColor(7));
        assertEquals(Integer.valueOf(7), o.getLineColor());
        assertEquals(UpdateStatus.NoChange, o.setLineColor(7));

        assertEquals(UpdateStatus.Success, o.setLineStyle(7)); // 7 -> DOT
        assertEquals(LineType.DOT, o.getLineStyleAsEnum());
        assertEquals(Integer.valueOf(7), o.getLineStyle());

        assertEquals(UpdateStatus.Success, o.setLineMode(true));
        assertTrue(o.getLineMode());

        assertEquals(UpdateStatus.Success, o.setLineThickness(3.0));
        assertEquals(Double.valueOf(3.0), o.getLineThickness());
    }

    @Test
    public void markDelegation() {
        Stub o = new Stub();
        assertEquals(UpdateStatus.Success, o.setMarkMode(true));
        assertTrue(o.getMarkMode());

        assertEquals(UpdateStatus.Success, o.setMarkStyle(4));
        assertEquals(Integer.valueOf(4), o.getMarkStyle());

        assertEquals(UpdateStatus.Success, o.setMarkSizeUnit(1)); // TABULATED
        assertEquals(MarkSizeUnitType.TABULATED, o.getMarkSizeUnitAsEnum());
        assertEquals(Integer.valueOf(1), o.getMarkSizeUnit());
    }

    @Test
    public void markSizesArrayAndSingleSizeAreMutuallyExclusive() {
        Stub o = new Stub();
        assertEquals(UpdateStatus.Success, o.setMarkSizes(new Integer[] {1, 2, 3}));
        assertEquals(3, o.getNumMarkSizes());
        assertArrayEquals(new Integer[] {1, 2, 3}, o.getMarkSizes());

        // Setting a single size clears the per-mark size array.
        assertEquals(UpdateStatus.Success, o.setMarkSize(9));
        assertEquals(Integer.valueOf(9), o.getMarkSize());
        assertEquals(0, o.getNumMarkSizes());
    }

    @Test
    public void markForegroundsArraySetsSentinelColorAndSingleClearsArray() {
        Stub o = new Stub();
        assertEquals(UpdateStatus.Success, o.setMarkForegrounds(new Integer[] {4, 5}));
        assertEquals(2, o.getNumMarkForegrounds());
        // The single-color slot is set to the -3 "multiple colors" sentinel.
        assertEquals(Integer.valueOf(-3), o.getMarkForeground());

        assertEquals(UpdateStatus.Success, o.setMarkForeground(2));
        assertEquals(Integer.valueOf(2), o.getMarkForeground());
        assertEquals(0, o.getNumMarkForegrounds());
    }

    @Test
    public void markBackgroundsArraySetsSentinelColorAndSingleClearsArray() {
        Stub o = new Stub();
        assertEquals(UpdateStatus.Success, o.setMarkBackgrounds(new Integer[] {6, 7, 8}));
        assertEquals(3, o.getNumMarkBackgrounds());
        assertEquals(Integer.valueOf(-3), o.getMarkBackground());

        assertEquals(UpdateStatus.Success, o.setMarkBackground(1));
        assertEquals(Integer.valueOf(1), o.getMarkBackground());
        assertEquals(0, o.getNumMarkBackgrounds());
    }

    @Test
    public void setMarkClearsAllPerMarkArrays() {
        Stub o = new Stub();
        o.setMarkSizes(new Integer[] {1});
        o.setMarkForegrounds(new Integer[] {2});
        o.setMarkBackgrounds(new Integer[] {3});

        Mark replacement = new Mark();
        assertEquals(UpdateStatus.Success, o.setMark(replacement));
        assertSame(replacement, o.getMark());
        assertEquals(0, o.getNumMarkSizes());
        assertEquals(0, o.getNumMarkForegrounds());
        assertEquals(0, o.getNumMarkBackgrounds());
    }

    @Test
    public void fastPropertyDispatch() {
        Stub o = new Stub();
        assertEquals(Boolean.FALSE, o.getProperty(ContouredObjectPropertyType.FILLMODE));
        assertEquals(UpdateStatus.Success,
                     o.setProperty(ContouredObjectPropertyType.FILLMODE, true));
        assertEquals(Boolean.TRUE, o.getProperty(ContouredObjectPropertyType.FILLMODE));

        assertEquals(UpdateStatus.Success,
                     o.setProperty(ContouredObjectPropertyType.BACKGROUND, 5));
        assertEquals(Integer.valueOf(5), o.getProperty(ContouredObjectPropertyType.BACKGROUND));

        assertSame(o.getLine(), o.getProperty(ContouredObjectPropertyType.LINE));
        assertSame(o.getMark(), o.getProperty(ContouredObjectPropertyType.MARK));

        assertEquals(UpdateStatus.Success, o.setProperty(LinePropertyType.COLOR, 8));
        assertEquals(Integer.valueOf(8), o.getProperty(LinePropertyType.COLOR));

        assertEquals(UpdateStatus.Success, o.setProperty(MarkPropertyType.SIZE, 6));
        assertEquals(Integer.valueOf(6), o.getProperty(MarkPropertyType.SIZE));
    }

    @Test
    public void basePropertyDispatchDelegatesToSuper() {
        Stub o = new Stub();
        assertEquals(UpdateStatus.Success, o.setProperty(GraphicObjectPropertyType.TAG, "cTag"));
        assertEquals("cTag", o.getTag());
        assertEquals("cTag", o.getProperty(GraphicObjectPropertyType.TAG));
        assertEquals(Boolean.TRUE, o.getProperty(GraphicObjectPropertyType.VISIBLE));
    }

    @Test
    public void cloneDeepCopiesLineAndMark() {
        Stub o = new Stub();
        o.setLineColor(5);
        o.setMarkStyle(9);
        o.setFillMode(true);
        o.setBackground(7);
        o.setMarkOffset(3);
        o.setSelected(true);

        ContouredObject clone = o.clone();
        assertEquals(Integer.valueOf(5), clone.getLineColor());
        assertEquals(Integer.valueOf(9), clone.getMarkStyle());
        assertTrue(clone.getFillMode());
        assertEquals(Integer.valueOf(7), clone.getBackground());
        assertEquals(Integer.valueOf(3), clone.getMarkOffset());
        assertTrue(clone.getSelected());

        // Line and Mark are deep-copied: mutating the clone leaves the source.
        assertNotSame(o.getLine(), clone.getLine());
        assertNotSame(o.getMark(), clone.getMark());
        clone.setLineColor(100);
        clone.setMarkStyle(200);
        assertEquals(Integer.valueOf(5), o.getLineColor());
        assertEquals(Integer.valueOf(9), o.getMarkStyle());
    }
}
