/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Scilab
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 *
 */

package org.scilab.modules.preferences.Component;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Hermetic unit tests for the {@link Color} preference component (a
 * {@link javax.swing.JButton} whose foreground <em>is</em> the chosen colour). The
 * {@code color} sensor/actuator round-trips through the button foreground and the
 * module's {@code #rrggbb} string form. The colour chooser is opened only from the
 * action callback (never during construction), so no display is touched here; the
 * class name {@code Color} refers to the component, {@code java.awt.Color} is used
 * fully qualified.
 */
public class ColorTest {

    private static Element el(String name, String... kv) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element e = doc.createElement(name);
        for (int i = 0; i + 1 < kv.length; i += 2) {
            e.setAttribute(kv[i], kv[i + 1]);
        }
        return e;
    }

    @Test
    public void constructorReadsTheColourAttribute() throws Exception {
        Color c = new Color(el("Color", "color", "#ff0000"));
        assertEquals("#ff0000", c.color());
    }

    @Test
    public void colorActuatorAndSensorRoundTrip() throws Exception {
        Color c = new Color(el("Color", "color", "#ff0000"));
        c.color("#00ff00");
        assertEquals("#00ff00", c.color());
    }

    @Test
    public void actuatorSetsTheButtonForeground() throws Exception {
        Color c = new Color(el("Color", "color", "#000000"));
        c.color("#123456");
        assertEquals(new java.awt.Color(0x12, 0x34, 0x56), c.getForeground());
    }

    @Test
    public void absentColourDefaultsToBlack() throws Exception {
        // Default attribute "000000" decodes (octal 0) to black.
        Color c = new Color(el("Color"));
        assertEquals("#000000", c.color());
    }

    @Test
    public void enableAttributeControlsEnabledState() throws Exception {
        assertFalse(new Color(el("Color", "color", "#000000", "enable", "false")).isEnabled());
        assertTrue(new Color(el("Color", "color", "#000000")).isEnabled(), "absent 'enable' defaults to true");
    }

    @Test
    public void actuatorsAndToString() throws Exception {
        Color c = new Color(el("Color", "color", "#ff0000"));
        assertArrayEquals(new String[] {"enable", "color"}, c.actuators());
        assertEquals("Color color='#ff0000'", c.toString());
    }
}
