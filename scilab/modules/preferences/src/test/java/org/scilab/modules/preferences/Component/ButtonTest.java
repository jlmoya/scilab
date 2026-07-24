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
import org.scilab.modules.preferences.XCommonManager;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Hermetic unit tests for the {@link Button} preference component (a
 * {@link javax.swing.JButton}). The text/enable actuators and the {@code toString}
 * signature are pure Swing state; the attribute reads route through
 * {@code getAttribute} (declared on {@code XCommonManager}), so no native code or
 * display is required.
 */
public class ButtonTest {

    private static Element el(String name, String... kv) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element e = doc.createElement(name);
        for (int i = 0; i + 1 < kv.length; i += 2) {
            e.setAttribute(kv[i], kv[i + 1]);
        }
        return e;
    }

    @Test
    public void textActuatorAndSensorRoundTrip() throws Exception {
        Button c = new Button(el("Button"));
        c.text("Go");
        assertEquals("Go", c.text());
        assertEquals("Go", c.getText());
    }

    @Test
    public void constructorReadsTextAttribute() throws Exception {
        Button c = new Button(el("Button", "text", "Save"));
        assertEquals("Save", c.text());
    }

    @Test
    public void absentTextBecomesNavSentinel() throws Exception {
        Button c = new Button(el("Button"));
        assertEquals(XCommonManager.NAV, c.text());
    }

    @Test
    public void toStringOmitsTextWhenNav() throws Exception {
        // No 'text' attribute => text() is NAV => the signature drops the text clause.
        assertEquals("Button", new Button(el("Button")).toString());
    }

    @Test
    public void toStringIncludesTextWhenPresent() throws Exception {
        assertEquals("Button text='Save'", new Button(el("Button", "text", "Save")).toString());
    }

    @Test
    public void enableAttributeControlsEnabledState() throws Exception {
        assertFalse(new Button(el("Button", "enable", "false")).isEnabled());
        assertTrue(new Button(el("Button")).isEnabled(), "absent 'enable' defaults to true");
    }

    @Test
    public void actuatorsAreTheDocumentedSet() throws Exception {
        assertArrayEquals(new String[] {"enable", "text"}, new Button(el("Button")).actuators());
    }
}
