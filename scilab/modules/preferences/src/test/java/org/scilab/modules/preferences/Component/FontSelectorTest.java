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

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Hermetic unit tests for the {@link FontSelector} preference component. The
 * font-name / font-face / font-size sensors and actuators are pure
 * {@link java.awt.Font} manipulation (the interactive font-chooser button is the
 * only display-bound part and is not exercised). {@code choose()} returns the
 * [name, face, size] triple. Constructed headless &mdash; no display required.
 */
public class FontSelectorTest {

    private static Element el(String name, String... kv) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element e = doc.createElement(name);
        for (int i = 0; i + 1 < kv.length; i += 2) {
            e.setAttribute(kv[i], kv[i + 1]);
        }
        return e;
    }

    @Test
    public void defaultFontIsMonospacedPlainTwelve() throws Exception {
        FontSelector c = new FontSelector(el("FontSelector"));
        assertEquals("Monospaced", c.fontname());
        assertEquals("plain", c.fontface());
        assertEquals("12", c.fontsize());
    }

    @Test
    public void constructorAppliesNameFaceAndSize() throws Exception {
        FontSelector c = new FontSelector(el("FontSelector",
                                             "font-name", "Serif",
                                             "font-face", "bold",
                                             "font-size", "18"));
        assertEquals("Serif", c.fontname());
        assertEquals("bold", c.fontface());
        assertEquals("18", c.fontsize());
    }

    @Test
    public void chooseReturnsTheNameFaceSizeTriple() throws Exception {
        FontSelector c = new FontSelector(el("FontSelector",
                                             "font-name", "Serif",
                                             "font-face", "bold",
                                             "font-size", "18"));
        assertArrayEquals(new String[] {"Serif", "bold", "18"}, (String[]) c.choose());
    }

    @Test
    public void fontFaceActuatorMapsEveryStyleKeyword() throws Exception {
        FontSelector c = new FontSelector(el("FontSelector"));
        c.fontface("italic");
        assertEquals("italic", c.fontface());
        c.fontface("bold italic");
        assertEquals("bold italic", c.fontface());
        c.fontface("bold");
        assertEquals("bold", c.fontface());
    }

    @Test
    public void unknownFontFaceFallsBackToPlain() throws Exception {
        FontSelector c = new FontSelector(el("FontSelector", "font-face", "bold"));
        c.fontface("wibble");
        assertEquals("plain", c.fontface(), "an unrecognised face resets the style to PLAIN");
    }

    @Test
    public void fontSizeActuatorParsesAndIgnoresGarbage() throws Exception {
        FontSelector c = new FontSelector(el("FontSelector"));
        c.fontsize("24");
        assertEquals("24", c.fontsize());
        c.fontsize("not-a-size"); // NumberFormatException is swallowed
        assertEquals("24", c.fontsize(), "a non-numeric size leaves the previous size untouched");
    }

    @Test
    public void fontNameActuatorChangesTheName() throws Exception {
        FontSelector c = new FontSelector(el("FontSelector"));
        c.fontname("Dialog");
        assertEquals("Dialog", c.fontname());
    }

    @Test
    public void refreshUpdatesFromNewAttributes() throws Exception {
        FontSelector c = new FontSelector(el("FontSelector", "font-name", "Serif"));
        c.refresh(el("FontSelector", "font-name", "Dialog", "font-face", "italic", "font-size", "20"));
        assertEquals("Dialog", c.fontname());
        assertEquals("italic", c.fontface());
        assertEquals("20", c.fontsize());
    }

    @Test
    public void toStringListsEveryFontProperty() throws Exception {
        FontSelector c = new FontSelector(el("FontSelector",
                                             "font-name", "Serif",
                                             "font-face", "bold",
                                             "font-size", "18"));
        assertEquals("FontSelector font-name='Serif' font-face='bold' font-size='18'", c.toString());
    }

    @Test
    public void actuatorsAreTheDocumentedSet() throws Exception {
        assertArrayEquals(new String[] {"font-name", "font-face", "font-size", "enable"},
                          new FontSelector(el("FontSelector")).actuators());
    }
}
