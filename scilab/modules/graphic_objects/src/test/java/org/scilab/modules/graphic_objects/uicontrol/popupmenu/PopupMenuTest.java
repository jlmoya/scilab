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

package org.scilab.modules.graphic_objects.uicontrol.popupmenu;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import org.scilab.modules.graphic_objects.console.Console;
import org.scilab.modules.graphic_objects.graphicObject.GraphicObject.UpdateStatus;

import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_UICONTROL__;
import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_UI_POPUPMENU__;

/**
 * Hermetic unit tests for {@link PopupMenu}. Like the listbox it splits a
 * pipe-separated single-element string into its items.
 */
public class PopupMenuTest {

    @Test
    public void styleTypeAndEmptyDefaultString() {
        PopupMenu p = new PopupMenu();
        assertEquals(__GO_UI_POPUPMENU__, p.getStyle().intValue());
        assertEquals(__GO_UICONTROL__, p.getType().intValue());
        assertNotNull(p.getString());
        assertEquals(0, p.getString().length);
    }

    @Test
    public void deprecatedLookAndFeelUsesFlatRelief() {
        boolean saved = Console.getConsole().getUseDeprecatedLF();
        Console.getConsole().setUseDeprecatedLF(true);
        try {
            PopupMenu p = new PopupMenu();
            assertEquals("flat", p.getRelief());
            assertEquals(__GO_UI_POPUPMENU__, p.getStyle().intValue());
        } finally {
            Console.getConsole().setUseDeprecatedLF(saved);
        }
    }

    @Test
    public void pipeSeparatedStringIsTokenised() {
        PopupMenu p = new PopupMenu();
        assertEquals(UpdateStatus.Success, p.setString(new String[] {"red|green|blue"}));
        assertArrayEquals(new String[] {"red", "green", "blue"}, p.getString());
    }
}
