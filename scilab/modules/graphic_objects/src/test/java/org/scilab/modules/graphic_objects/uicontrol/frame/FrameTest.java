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

package org.scilab.modules.graphic_objects.uicontrol.frame;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_UICONTROL__;
import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_UI_FRAME__;

import org.scilab.modules.graphic_objects.axes.AxesContainer;
import org.scilab.modules.graphic_objects.figure.ColorMap;
import org.scilab.modules.graphic_objects.uicontrol.Uicontrol.UicontrolStyle;

/**
 * Hermetic unit tests for {@link Frame}. The constructor consults the (pure,
 * singleton) Console and Swing UIManager defaults, neither of which needs the
 * native runtime. The AxesContainer accessors resolve against an empty
 * GraphicModel (no parent figure) and thus return documented fallbacks.
 */
public class FrameTest {

    @Test
    public void styleIsFrame() {
        Frame f = new Frame();
        assertEquals(Integer.valueOf(__GO_UI_FRAME__), f.getStyle());
        assertEquals(UicontrolStyle.FRAME, f.getStyleAsEnum());
    }

    @Test
    public void typeInheritsGenericUicontrol() {
        // Frame does not override getType(): it reports the base uicontrol type.
        assertEquals(Integer.valueOf(__GO_UICONTROL__), new Frame().getType());
    }

    @Test
    public void isAnAxesContainer() {
        assertTrue(new Frame() instanceof AxesContainer);
    }

    @Test
    public void constructorSetsAlignment() {
        Frame f = new Frame();
        assertEquals("left", f.getHorizontalAlignment());
        assertEquals("middle", f.getVerticalAlignment());
    }

    @Test
    public void axesSizeDerivesFromDefaultPositionWidthHeight() {
        // Default uicontrol position is {x=20, y=40, w=40, h=20}; axes size is
        // {w, h} truncated to int.
        Frame f = new Frame();
        Integer[] size = f.getAxesSize();
        assertArrayEquals(new Integer[] {40, 20}, size);
    }

    @Test
    public void axesSizeTracksPosition() {
        Frame f = new Frame();
        f.setUiPosition(new Double[] {0.0, 0.0, 100.5, 50.9});
        // intValue() truncates toward zero.
        assertArrayEquals(new Integer[] {100, 50}, f.getAxesSize());
    }

    @Test
    public void accessorsFallBackWhenNoParentFigure() {
        // With no parent figure registered, the figure-delegating accessors
        // return their documented fallbacks.
        Frame f = new Frame();
        assertEquals(Integer.valueOf(0), f.getAntialiasing());
        assertEquals(Integer.valueOf(-2), f.getBackground());
        ColorMap cm = f.getColorMap();
        assertNotNull(cm);
    }
}
