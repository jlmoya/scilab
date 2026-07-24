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

package org.scilab.modules.graphic_objects.uibar.progressionbar;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_PROGRESSIONBAR__;
import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_UI_VALUE__;

import org.scilab.modules.graphic_objects.graphicObject.GraphicObject.UpdateStatus;
import org.scilab.modules.graphic_objects.graphicObject.Visitor;
import org.scilab.modules.graphic_objects.uibar.Uibar;

/**
 * Hermetic unit tests for {@link Progressionbar}: a concrete Uibar whose only
 * specialisation is the PROGRESSIONBAR type tag.
 */
public class ProgressionbarTest {

    @Test
    public void typeIsProgressionbar() {
        assertEquals(Integer.valueOf(__GO_PROGRESSIONBAR__), new Progressionbar().getType());
    }

    @Test
    public void isAUibar() {
        assertTrue(new Progressionbar() instanceof Uibar);
    }

    @Test
    public void inheritsUibarMessageAndValueDefaults() {
        Progressionbar bar = new Progressionbar();
        assertArrayEquals(new String[] {""}, bar.getMessage());
        assertEquals(Integer.valueOf(0), bar.getValue());
    }

    @Test
    public void inheritedSettersWork() {
        Progressionbar bar = new Progressionbar();
        assertEquals(UpdateStatus.Success, bar.setValue(33));
        assertEquals(Integer.valueOf(33), bar.getValue());
        assertEquals(UpdateStatus.Success, bar.setMessage(new String[] {"step 1"}));
        assertArrayEquals(new String[] {"step 1"}, bar.getMessage());
    }

    @Test
    public void valuePropertyRoundTrips() {
        Progressionbar bar = new Progressionbar();
        Object prop = bar.getPropertyFromName(__GO_UI_VALUE__);
        bar.setProperty(prop, Integer.valueOf(90));
        assertEquals(Integer.valueOf(90), bar.getProperty(prop));
    }

    @Test
    public void acceptIsANoOp() {
        assertDoesNotThrow(() -> new Progressionbar().accept((Visitor) null));
    }
}
