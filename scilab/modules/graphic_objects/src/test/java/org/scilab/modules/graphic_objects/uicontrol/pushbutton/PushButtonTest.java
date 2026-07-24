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

package org.scilab.modules.graphic_objects.uicontrol.pushbutton;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import org.scilab.modules.graphic_objects.console.Console;

import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_UICONTROL__;
import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_UI_PUSHBUTTON__;

/**
 * Hermetic unit tests for {@link PushButton}. Unlike the toggle controls it
 * assigns no default value; the deprecated look and feel also gives it a raised
 * relief and a grey background.
 */
public class PushButtonTest {

    @Test
    public void styleAndTypeAreLookAndFeelIndependent() {
        PushButton p = new PushButton();
        assertEquals(__GO_UI_PUSHBUTTON__, p.getStyle().intValue());
        assertEquals(__GO_UICONTROL__, p.getType().intValue());
        // A push button carries no value array.
        assertNull(p.getUiValue());
    }

    @Test
    public void deprecatedLookAndFeelUsesRaisedReliefAndGreyBackground() {
        boolean saved = Console.getConsole().getUseDeprecatedLF();
        Console.getConsole().setUseDeprecatedLF(true);
        try {
            PushButton p = new PushButton();
            assertEquals("raised", p.getRelief());
            assertArrayEquals(new Double[] {0.6, 0.6, 0.6}, p.getBackgroundColor());
            assertEquals(__GO_UI_PUSHBUTTON__, p.getStyle().intValue());
        } finally {
            Console.getConsole().setUseDeprecatedLF(saved);
        }
    }
}
