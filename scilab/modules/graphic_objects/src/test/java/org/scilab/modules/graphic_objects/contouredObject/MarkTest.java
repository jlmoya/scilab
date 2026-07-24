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

import org.scilab.modules.graphic_objects.contouredObject.Mark.MarkSizeUnitType;
import org.scilab.modules.graphic_objects.graphicObject.GraphicObject.UpdateStatus;

/**
 * Hermetic unit tests for {@link Mark}: a plain data holder whose setters
 * return an {@link UpdateStatus}, plus its MarkSizeUnitType enum converter.
 */
public class MarkTest {

    @Test
    public void defaultsAreZeroAndPointUnit() {
        Mark m = new Mark();
        assertFalse(m.getMode());
        assertEquals(Integer.valueOf(0), m.getStyle());
        assertEquals(Integer.valueOf(0), m.getSize());
        assertEquals(Integer.valueOf(0), m.getForeground());
        assertEquals(Integer.valueOf(0), m.getBackground());
        assertEquals(MarkSizeUnitType.POINT, m.getMarkSizeUnit());
    }

    @Test
    public void markSizeUnitIntToEnum() {
        assertEquals(MarkSizeUnitType.POINT, MarkSizeUnitType.intToEnum(0));
        assertEquals(MarkSizeUnitType.TABULATED, MarkSizeUnitType.intToEnum(1));
        assertNull(MarkSizeUnitType.intToEnum(2));
        assertNull(MarkSizeUnitType.intToEnum(-1));
    }

    @Test
    public void settingNewValueSucceedsSettingSameValueNoChange() {
        Mark m = new Mark();

        assertEquals(UpdateStatus.Success, m.setBackground(7));
        assertEquals(Integer.valueOf(7), m.getBackground());
        assertEquals(UpdateStatus.NoChange, m.setBackground(7));

        assertEquals(UpdateStatus.Success, m.setForeground(3));
        assertEquals(Integer.valueOf(3), m.getForeground());
        assertEquals(UpdateStatus.NoChange, m.setForeground(3));

        assertEquals(UpdateStatus.Success, m.setStyle(4));
        assertEquals(Integer.valueOf(4), m.getStyle());
        assertEquals(UpdateStatus.NoChange, m.setStyle(4));

        assertEquals(UpdateStatus.Success, m.setSize(12));
        assertEquals(Integer.valueOf(12), m.getSize());
        assertEquals(UpdateStatus.NoChange, m.setSize(12));
    }

    @Test
    public void settingDefaultValueAgainIsNoChange() {
        Mark m = new Mark();
        // Freshly constructed background is already 0.
        assertEquals(UpdateStatus.NoChange, m.setBackground(0));
        assertEquals(UpdateStatus.NoChange, m.setMode(false));
        assertEquals(UpdateStatus.NoChange, m.setMarkSizeUnit(MarkSizeUnitType.POINT));
    }

    @Test
    public void modeToggles() {
        Mark m = new Mark();
        assertEquals(UpdateStatus.Success, m.setMode(true));
        assertTrue(m.getMode());
        assertEquals(UpdateStatus.NoChange, m.setMode(true));
        assertEquals(UpdateStatus.Success, m.setMode(false));
        assertFalse(m.getMode());
    }

    @Test
    public void markSizeUnitSetter() {
        Mark m = new Mark();
        assertEquals(UpdateStatus.Success, m.setMarkSizeUnit(MarkSizeUnitType.TABULATED));
        assertEquals(MarkSizeUnitType.TABULATED, m.getMarkSizeUnit());
        assertEquals(UpdateStatus.NoChange, m.setMarkSizeUnit(MarkSizeUnitType.TABULATED));
    }

    @Test
    public void copyConstructorDuplicatesStateIndependently() {
        Mark src = new Mark();
        src.setMode(true);
        src.setStyle(5);
        src.setSize(9);
        src.setForeground(2);
        src.setBackground(8);
        src.setMarkSizeUnit(MarkSizeUnitType.TABULATED);

        Mark copy = new Mark(src);
        assertTrue(copy.getMode());
        assertEquals(Integer.valueOf(5), copy.getStyle());
        assertEquals(Integer.valueOf(9), copy.getSize());
        assertEquals(Integer.valueOf(2), copy.getForeground());
        assertEquals(Integer.valueOf(8), copy.getBackground());
        assertEquals(MarkSizeUnitType.TABULATED, copy.getMarkSizeUnit());

        // Mutating the copy must not affect the source.
        copy.setStyle(100);
        assertEquals(Integer.valueOf(5), src.getStyle());
    }
}
