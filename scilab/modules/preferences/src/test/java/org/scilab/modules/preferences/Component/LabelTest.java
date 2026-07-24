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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.scilab.modules.preferences.XCommonManager;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Hermetic unit tests for the {@link Label} preference component.
 *
 * <p>{@code Label} extends {@link javax.swing.JLabel} and carries the module's
 * font / colour / text sensor+actuator logic. Every method exercised here is pure
 * Swing state manipulation: the attribute reads route through {@code getAttribute}
 * (declared on {@code XCommonManager}, not the JNI-loading {@code XConfigManager}),
 * so no native code, display or running Scilab is needed. Nodes come from JAXP.
 * The {@code background()} sensor's {@code getParent()} branch is deliberately
 * avoided by driving the actuators directly and reading back the Swing state.
 */
public class LabelTest {

    private static Element el(String name, String... kv) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element e = doc.createElement(name);
        for (int i = 0; i + 1 < kv.length; i += 2) {
            e.setAttribute(kv[i], kv[i + 1]);
        }
        return e;
    }

    @Test
    public void constructorReadsTextAttribute() throws Exception {
        Label c = new Label(el("Label", "text", "Hello"));
        assertEquals("Hello", c.text());
        assertEquals("Hello", c.getText());
    }

    @Test
    public void absentTextBecomesNavSentinel() throws Exception {
        // No 'text' attribute => getAttribute returns NAV, which is pushed into setText.
        Label c = new Label(el("Label"));
        assertEquals(XCommonManager.NAV, c.text());
    }

    @Test
    public void actuatorsAreTheDocumentedSet() throws Exception {
        Label c = new Label(el("Label"));
        assertArrayEquals(new String[] {"text", "foreground", "background", "tooltip",
                                        "font-family", "font-face", "font-size", "enable"},
                          c.actuators());
    }

    @Test
    public void fontSizeActuatorAndSensorRoundTrip() throws Exception {
        Label c = new Label(el("Label"));
        c.fontSize("20");
        assertEquals("20", c.fontSize());
        assertEquals(20, c.getFont().getSize());
    }

    @Test
    public void fontFaceMapsEveryStyleName() throws Exception {
        Label c = new Label(el("Label"));
        c.fontFace("plain");
        assertEquals("plain", c.fontFace());
        c.fontFace("italic");
        assertEquals("italic", c.fontFace());
        c.fontFace("bold");
        assertEquals("bold", c.fontFace());
        c.fontFace("bold italic");
        assertEquals("bold italic", c.fontFace());
    }

    @Test
    public void fontFamilyActuatorAndSensorRoundTrip() throws Exception {
        Label c = new Label(el("Label"));
        c.fontFamily("Monospaced");
        assertEquals("Monospaced", c.fontFamily());
    }

    @Test
    public void constructorAppliesFontAttributes() throws Exception {
        Label c = new Label(el("Label", "font-size", "18", "font-face", "italic", "font-family", "Serif"));
        assertEquals("18", c.fontSize());
        assertEquals("italic", c.fontFace());
        assertEquals("Serif", c.fontFamily());
    }

    @Test
    public void foregroundActuatorAndSensorRoundTrip() throws Exception {
        Label c = new Label(el("Label"));
        c.foreground("#123456");
        assertEquals("#123456", c.foreground());
        assertEquals(new Color(0x12, 0x34, 0x56), c.getForeground());
    }

    @Test
    public void foregroundNavClearsTheColour() throws Exception {
        Label c = new Label(el("Label"));
        c.foreground("#123456");
        c.foreground(XCommonManager.NAV);
        assertNull(c.getForeground(), "the NAV sentinel maps to a null (inherited) foreground");
    }

    @Test
    public void backgroundColourMakesTheLabelOpaque() throws Exception {
        Label c = new Label(el("Label"));
        c.background("#0a0b0c");
        assertTrue(c.isOpaque());
        assertEquals(new Color(10, 11, 12), c.getBackground());
    }

    @Test
    public void backgroundNavMakesTheLabelTransparent() throws Exception {
        Label c = new Label(el("Label"));
        c.background("#0a0b0c");
        c.background(XCommonManager.NAV);
        assertFalse(c.isOpaque());
        assertNull(c.getBackground());
    }

    @Test
    public void tooltipNavOrEmptyStringClearsTheTooltip() throws Exception {
        Label c = new Label(el("Label"));
        c.tooltip("hint");
        assertEquals("hint", c.tooltip());
        c.tooltip("");
        assertEquals("", c.tooltip(), "empty tooltip reads back as empty string, not null");
        c.tooltip("hint");
        c.tooltip(XCommonManager.NAV);
        assertEquals("", c.tooltip(), "NAV clears the tooltip");
    }

    @Test
    public void enableAttributeControlsEnabledState() throws Exception {
        assertFalse(new Label(el("Label", "enable", "false")).isEnabled());
        assertTrue(new Label(el("Label")).isEnabled(), "absent 'enable' defaults to true");
        assertTrue(new Label(el("Label", "enable", "true")).isEnabled());
    }
}
