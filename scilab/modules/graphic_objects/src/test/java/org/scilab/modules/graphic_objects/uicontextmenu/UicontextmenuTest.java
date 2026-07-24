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

package org.scilab.modules.graphic_objects.uicontextmenu;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_UICONTEXTMENU__;

import org.scilab.modules.graphic_objects.graphicObject.GraphicObject;
import org.scilab.modules.graphic_objects.graphicObject.Visitor;

/**
 * Hermetic unit tests for {@link Uicontextmenu}: a thin GraphicObject that only
 * overrides getType() and a no-op accept().
 */
public class UicontextmenuTest {

    @Test
    public void typeIsUicontextmenu() {
        assertEquals(Integer.valueOf(__GO_UICONTEXTMENU__), new Uicontextmenu().getType());
    }

    @Test
    public void isAGraphicObject() {
        assertTrue(new Uicontextmenu() instanceof GraphicObject);
    }

    @Test
    public void inheritsGraphicObjectDefaults() {
        Uicontextmenu uic = new Uicontextmenu();
        // Unlike Uicontrol, this object is not hidden at construction.
        assertTrue(uic.getVisible());
        assertEquals(Integer.valueOf(0), uic.getIdentifier());
        assertEquals(Integer.valueOf(0), uic.getParent());
        assertEquals("", uic.getTag());
    }

    @Test
    public void acceptIsANoOp() {
        assertDoesNotThrow(() -> new Uicontextmenu().accept((Visitor) null));
    }

    @Test
    public void cloneKeepsRuntimeTypeAndResetsHierarchy() {
        Uicontextmenu uic = new Uicontextmenu();
        uic.setTag("ctx");
        GraphicObject copy = uic.clone();
        assertTrue(copy instanceof Uicontextmenu);
        assertNotSame(uic, copy);
        // Shallow-copied scalar state is carried over...
        assertEquals("ctx", copy.getTag());
        // ...while the parent link is reset to none by clone().
        assertEquals(Integer.valueOf(0), copy.getParent());
    }
}
