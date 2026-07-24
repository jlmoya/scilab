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

package org.scilab.modules.graphic_objects.uicontrol.edit;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import org.scilab.modules.graphic_objects.console.Console;

import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_UICONTROL__;
import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_UI_EDIT__;

/**
 * Hermetic unit tests for {@link Edit}.
 */
public class EditTest {

    @Test
    public void styleAndTypeAreLookAndFeelIndependent() {
        Edit e = new Edit();
        assertEquals(__GO_UI_EDIT__, e.getStyle().intValue());
        assertEquals(__GO_UICONTROL__, e.getType().intValue());
    }

    @Test
    public void deprecatedLookAndFeelUsesSunkenRelief() {
        boolean saved = Console.getConsole().getUseDeprecatedLF();
        Console.getConsole().setUseDeprecatedLF(true);
        try {
            Edit e = new Edit();
            assertEquals("sunken", e.getRelief());
            assertEquals(__GO_UI_EDIT__, e.getStyle().intValue());
        } finally {
            Console.getConsole().setUseDeprecatedLF(saved);
        }
    }
}
