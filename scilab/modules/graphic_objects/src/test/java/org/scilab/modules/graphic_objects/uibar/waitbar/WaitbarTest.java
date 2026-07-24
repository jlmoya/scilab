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

package org.scilab.modules.graphic_objects.uibar.waitbar;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_UI_MESSAGE_SIZE__;
import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_WAITBAR__;

import org.scilab.modules.graphic_objects.graphicObject.GraphicObject.UpdateStatus;
import org.scilab.modules.graphic_objects.graphicObject.Visitor;
import org.scilab.modules.graphic_objects.uibar.Uibar;

/**
 * Hermetic unit tests for {@link Waitbar}: a concrete Uibar whose only
 * specialisation is the WAITBAR type tag.
 */
public class WaitbarTest {

    @Test
    public void typeIsWaitbar() {
        assertEquals(Integer.valueOf(__GO_WAITBAR__), new Waitbar().getType());
    }

    @Test
    public void isAUibar() {
        assertTrue(new Waitbar() instanceof Uibar);
    }

    @Test
    public void inheritsUibarDefaults() {
        Waitbar bar = new Waitbar();
        assertArrayEquals(new String[] {""}, bar.getMessage());
        assertEquals(Integer.valueOf(0), bar.getValue());
    }

    @Test
    public void inheritedSettersWork() {
        Waitbar bar = new Waitbar();
        assertEquals(UpdateStatus.Success, bar.setValue(10));
        assertEquals(Integer.valueOf(10), bar.getValue());
        assertEquals(UpdateStatus.Success, bar.setMessage(new String[] {"almost done"}));
        assertArrayEquals(new String[] {"almost done"}, bar.getMessage());
    }

    @Test
    public void messageSizePropertyReflectsMultiLineMessage() {
        Waitbar bar = new Waitbar();
        bar.setMessage(new String[] {"line1", "line2"});
        Object sizeProp = bar.getPropertyFromName(__GO_UI_MESSAGE_SIZE__);
        assertEquals(Integer.valueOf(2), bar.getProperty(sizeProp));
    }

    @Test
    public void acceptIsANoOp() {
        assertDoesNotThrow(() -> new Waitbar().accept((Visitor) null));
    }
}
