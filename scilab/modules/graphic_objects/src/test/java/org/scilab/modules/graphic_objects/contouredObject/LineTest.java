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

import org.scilab.modules.graphic_objects.contouredObject.Line.LineType;
import org.scilab.modules.graphic_objects.graphicObject.GraphicObject.UpdateStatus;

/**
 * Hermetic unit tests for {@link Line}: a plain data holder plus its LineType
 * enum, which maps to/from Scilab 1-based line-style indices and to 16-bit
 * stipple patterns.
 */
public class LineTest {

    @Test
    public void defaults() {
        Line l = new Line();
        assertFalse(l.getMode());
        assertEquals(LineType.SOLID, l.getLineStyle());
        assertEquals(Double.valueOf(1.0), l.getThickness());
        assertEquals(Integer.valueOf(-1), l.getColor());
    }

    @Test
    public void fromScilabIndexMapsEveryStyle() {
        assertEquals(LineType.SOLID, LineType.fromScilabIndex(1));
        assertEquals(LineType.DASH, LineType.fromScilabIndex(2));
        assertEquals(LineType.DASH_DOT, LineType.fromScilabIndex(3));
        assertEquals(LineType.LONG_DASH_DOT, LineType.fromScilabIndex(4));
        assertEquals(LineType.BIG_DASH_DOT, LineType.fromScilabIndex(5));
        assertEquals(LineType.BIG_DASH_LONG_DASH, LineType.fromScilabIndex(6));
        assertEquals(LineType.DOT, LineType.fromScilabIndex(7));
        assertEquals(LineType.DOUBLE_DOT, LineType.fromScilabIndex(8));
        assertEquals(LineType.LONG_BLANK_DOT, LineType.fromScilabIndex(9));
        assertEquals(LineType.BIG_BLANK_DOT, LineType.fromScilabIndex(10));
    }

    @Test
    public void fromScilabIndexOutOfRangeFallsBackToSolid() {
        assertEquals(LineType.SOLID, LineType.fromScilabIndex(0));
        assertEquals(LineType.SOLID, LineType.fromScilabIndex(11));
        assertEquals(LineType.SOLID, LineType.fromScilabIndex(-1));
    }

    @Test
    public void asScilabIndexIsOneBasedOrdinal() {
        assertEquals(1, LineType.SOLID.asScilabIndex());
        assertEquals(2, LineType.DASH.asScilabIndex());
        assertEquals(10, LineType.BIG_BLANK_DOT.asScilabIndex());
    }

    @Test
    public void scilabIndexRoundTrips() {
        for (int i = 1; i <= 10; i++) {
            assertEquals(i, LineType.fromScilabIndex(i).asScilabIndex(),
                         "round trip failed for scilab index " + i);
        }
    }

    @Test
    public void asPatternProducesDocumentedStipples() {
        assertEquals((short) 0xFFFF, LineType.SOLID.asPattern());
        assertEquals((short) 0x07FF, LineType.DASH.asPattern());
        assertEquals((short) 0x0F0F, LineType.DASH_DOT.asPattern());
        assertEquals((short) 0x1FC2, LineType.LONG_DASH_DOT.asPattern());
        assertEquals((short) 0x3FC9, LineType.BIG_DASH_DOT.asPattern());
        assertEquals((short) 0x3FC6, LineType.BIG_DASH_LONG_DASH.asPattern());
        assertEquals((short) 0x5555, LineType.DOT.asPattern());
        assertEquals((short) 0x3333, LineType.DOUBLE_DOT.asPattern());
        assertEquals((short) 0x1111, LineType.LONG_BLANK_DOT.asPattern());
        assertEquals((short) 0x0101, LineType.BIG_BLANK_DOT.asPattern());
    }

    @Test
    public void setColorChangeDetection() {
        Line l = new Line();
        assertEquals(UpdateStatus.Success, l.setColor(5));
        assertEquals(Integer.valueOf(5), l.getColor());
        assertEquals(UpdateStatus.NoChange, l.setColor(5));
        // Setting back to the constructed default is a genuine change here.
        assertEquals(UpdateStatus.Success, l.setColor(-1));
    }

    @Test
    public void setLineStyleChangeDetection() {
        Line l = new Line();
        assertEquals(UpdateStatus.NoChange, l.setLineStyle(LineType.SOLID));
        assertEquals(UpdateStatus.Success, l.setLineStyle(LineType.DOT));
        assertEquals(LineType.DOT, l.getLineStyle());
        assertEquals(UpdateStatus.NoChange, l.setLineStyle(LineType.DOT));
    }

    @Test
    public void setModeChangeDetection() {
        Line l = new Line();
        assertEquals(UpdateStatus.NoChange, l.setMode(false));
        assertEquals(UpdateStatus.Success, l.setMode(true));
        assertTrue(l.getMode());
        assertEquals(UpdateStatus.NoChange, l.setMode(true));
    }

    @Test
    public void setThicknessChangeDetection() {
        Line l = new Line();
        assertEquals(UpdateStatus.NoChange, l.setThickness(1.0));
        assertEquals(UpdateStatus.Success, l.setThickness(2.5));
        assertEquals(Double.valueOf(2.5), l.getThickness());
        assertEquals(UpdateStatus.NoChange, l.setThickness(2.5));
    }

    @Test
    public void copyConstructorDuplicatesStateIndependently() {
        Line src = new Line();
        src.setMode(true);
        src.setLineStyle(LineType.DASH_DOT);
        src.setThickness(4.0);
        src.setColor(9);

        Line copy = new Line(src);
        assertTrue(copy.getMode());
        assertEquals(LineType.DASH_DOT, copy.getLineStyle());
        assertEquals(Double.valueOf(4.0), copy.getThickness());
        assertEquals(Integer.valueOf(9), copy.getColor());

        // Mutating the copy must not affect the source.
        copy.setColor(100);
        assertEquals(Integer.valueOf(9), src.getColor());
    }
}
