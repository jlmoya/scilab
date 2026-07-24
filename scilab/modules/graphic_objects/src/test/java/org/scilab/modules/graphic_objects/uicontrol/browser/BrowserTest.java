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

package org.scilab.modules.graphic_objects.uicontrol.browser;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_UICONTROL__;
import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_UI_BROWSER__;

import org.scilab.modules.graphic_objects.uicontrol.Uicontrol.UicontrolStyle;

/**
 * Hermetic unit tests for {@link Browser}: the minimal Uicontrol whose sole
 * constructor responsibility is stamping the BROWSER style. It does not touch
 * Swing defaults, so it exercises only the (pure) Console-guarded base path.
 */
public class BrowserTest {

    @Test
    public void styleIsBrowser() {
        Browser b = new Browser();
        assertEquals(Integer.valueOf(__GO_UI_BROWSER__), b.getStyle());
        assertEquals(UicontrolStyle.BROWSER, b.getStyleAsEnum());
    }

    @Test
    public void typeInheritsGenericUicontrol() {
        assertEquals(Integer.valueOf(__GO_UICONTROL__), new Browser().getType());
    }

    @Test
    public void inheritsUicontrolDefaults() {
        Browser b = new Browser();
        // Enabled by default; hidden at construction (bug #10346 guard).
        assertTrue(b.getEnable());
        assertFalse(b.getVisible());
        // Default geometry {x=20, y=40, w=40, h=20}.
        assertArrayEquals(new Double[] {20.0, 40.0, 40.0, 20.0}, b.getUiPosition());
    }

    @Test
    public void positionIsSettable() {
        Browser b = new Browser();
        Double[] pos = {5.0, 6.0, 7.0, 8.0};
        b.setUiPosition(pos);
        assertArrayEquals(pos, b.getUiPosition());
    }
}
