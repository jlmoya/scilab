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

package org.scilab.modules.graphic_objects.uicontrol.radiobutton;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import org.scilab.modules.graphic_objects.console.Console;

import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_UICONTROL__;
import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_UI_RADIOBUTTON__;

/**
 * Hermetic unit tests for {@link RadioButton}.
 */
public class RadioButtonTest {

    @Test
    public void styleTypeAndDefaultValueAreLookAndFeelIndependent() {
        RadioButton r = new RadioButton();
        assertEquals(__GO_UI_RADIOBUTTON__, r.getStyle().intValue());
        assertEquals(__GO_UICONTROL__, r.getType().intValue());
        // Default value is the (unchecked) minimum.
        assertArrayEquals(new Double[] {0.0}, r.getUiValue());
    }

    @Test
    public void deprecatedLookAndFeelUsesFlatRelief() {
        boolean saved = Console.getConsole().getUseDeprecatedLF();
        Console.getConsole().setUseDeprecatedLF(true);
        try {
            RadioButton r = new RadioButton();
            assertEquals("flat", r.getRelief());
            assertEquals(__GO_UI_RADIOBUTTON__, r.getStyle().intValue());
            assertArrayEquals(new Double[] {0.0}, r.getUiValue());
        } finally {
            Console.getConsole().setUseDeprecatedLF(saved);
        }
    }
}
