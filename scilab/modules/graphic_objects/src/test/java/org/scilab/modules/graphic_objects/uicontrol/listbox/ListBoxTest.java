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

package org.scilab.modules.graphic_objects.uicontrol.listbox;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import org.scilab.modules.graphic_objects.console.Console;
import org.scilab.modules.graphic_objects.graphicObject.GraphicObject.UpdateStatus;

import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_UICONTROL__;
import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_UI_LISTBOX__;

/**
 * Hermetic unit tests for {@link ListBox}. It seeds an empty value array and,
 * being a list style, splits pipe-separated single-element strings.
 */
public class ListBoxTest {

    @Test
    public void styleTypeAndEmptyDefaultValue() {
        ListBox lb = new ListBox();
        assertEquals(__GO_UI_LISTBOX__, lb.getStyle().intValue());
        assertEquals(__GO_UICONTROL__, lb.getType().intValue());
        assertNotNull(lb.getUiValue());
        assertEquals(0, lb.getUiValue().length);
        assertEquals(0, lb.getUiValueSize().intValue());
    }

    @Test
    public void deprecatedLookAndFeelUsesFlatRelief() {
        boolean saved = Console.getConsole().getUseDeprecatedLF();
        Console.getConsole().setUseDeprecatedLF(true);
        try {
            ListBox lb = new ListBox();
            assertEquals("flat", lb.getRelief());
            assertEquals(__GO_UI_LISTBOX__, lb.getStyle().intValue());
        } finally {
            Console.getConsole().setUseDeprecatedLF(saved);
        }
    }

    @Test
    public void overriddenSetUiValueStoresValue() {
        ListBox lb = new ListBox();
        assertEquals(UpdateStatus.Success, lb.setUiValue(new Double[] {2.0, 4.0}));
        assertArrayEquals(new Double[] {2.0, 4.0}, lb.getUiValue());
    }

    @Test
    public void pipeSeparatedStringIsTokenised() {
        ListBox lb = new ListBox();
        assertEquals(UpdateStatus.Success, lb.setString(new String[] {"one|two|three"}));
        assertArrayEquals(new String[] {"one", "two", "three"}, lb.getString());
    }
}
