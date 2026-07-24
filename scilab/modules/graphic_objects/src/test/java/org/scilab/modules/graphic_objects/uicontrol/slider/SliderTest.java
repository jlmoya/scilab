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

package org.scilab.modules.graphic_objects.uicontrol.slider;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import org.scilab.modules.graphic_objects.console.Console;

import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_UICONTROL__;
import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_UI_SLIDER__;

/**
 * Hermetic unit tests for {@link Slider}.
 */
public class SliderTest {

    @Test
    public void styleTypeAndDefaultValueAreLookAndFeelIndependent() {
        Slider s = new Slider();
        assertEquals(__GO_UI_SLIDER__, s.getStyle().intValue());
        assertEquals(__GO_UICONTROL__, s.getType().intValue());
        // Default value is the minimum.
        assertArrayEquals(new Double[] {0.0}, s.getUiValue());
    }

    @Test
    public void deprecatedLookAndFeelUsesFlatRelief() {
        boolean saved = Console.getConsole().getUseDeprecatedLF();
        Console.getConsole().setUseDeprecatedLF(true);
        try {
            Slider s = new Slider();
            assertEquals("flat", s.getRelief());
            assertEquals(__GO_UI_SLIDER__, s.getStyle().intValue());
            assertArrayEquals(new Double[] {0.0}, s.getUiValue());
        } finally {
            Console.getConsole().setUseDeprecatedLF(saved);
        }
    }
}
