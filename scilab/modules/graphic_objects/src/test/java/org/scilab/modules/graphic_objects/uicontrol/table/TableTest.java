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

package org.scilab.modules.graphic_objects.uicontrol.table;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import org.scilab.modules.graphic_objects.console.Console;

import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_UICONTROL__;
import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_UI_TABLE__;

/**
 * Hermetic unit tests for {@link Table}.
 */
public class TableTest {

    @Test
    public void styleAndTypeAreLookAndFeelIndependent() {
        Table t = new Table();
        assertEquals(__GO_UI_TABLE__, t.getStyle().intValue());
        assertEquals(__GO_UICONTROL__, t.getType().intValue());
    }

    @Test
    public void deprecatedLookAndFeelUsesFlatRelief() {
        boolean saved = Console.getConsole().getUseDeprecatedLF();
        Console.getConsole().setUseDeprecatedLF(true);
        try {
            Table t = new Table();
            assertEquals("flat", t.getRelief());
            assertEquals(__GO_UI_TABLE__, t.getStyle().intValue());
        } finally {
            Console.getConsole().setUseDeprecatedLF(saved);
        }
    }
}
