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
 * Hermetic unit tests for the {@link TextArea} preference component (a
 * {@link javax.swing.JTextArea} inside a scroll pane). The text / columns / rows
 * / editable / scroll actuators and sensors are pure Swing state, and
 * {@code choose()} just returns the text. Constructed headless &mdash; no display
 * required.
 */
public class TextAreaTest {

    private static Element el(String name, String... kv) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element e = doc.createElement(name);
        for (int i = 0; i + 1 < kv.length; i += 2) {
            e.setAttribute(kv[i], kv[i + 1]);
        }
        return e;
    }

    @Test
    public void constructorReadsEveryAttribute() throws Exception {
        TextArea c = new TextArea(el("TextArea",
                                     "text", "Hello",
                                     "columns", "20",
                                     "rows", "4",
                                     "editable", "true",
                                     "scroll", "true"));
        assertEquals("Hello", c.text());
        assertEquals("20", c.columns());
        assertEquals("4", c.rows());
        assertEquals("true", c.editable());
        assertEquals("true", c.scroll());
    }

    @Test
    public void textActuatorAndSensorRoundTrip() throws Exception {
        TextArea c = new TextArea(el("TextArea", "text", "Hello"));
        c.text("Goodbye");
        assertEquals("Goodbye", c.text());
    }

    @Test
    public void chooseReturnsTheText() throws Exception {
        TextArea c = new TextArea(el("TextArea", "text", "payload"));
        assertEquals("payload", c.choose());
    }

    @Test
    public void columnsAndRowsParseIntegers() throws Exception {
        TextArea c = new TextArea(el("TextArea", "text", "x"));
        c.columns("33");
        c.rows("7");
        assertEquals("33", c.columns());
        assertEquals("7", c.rows());
    }

    @Test
    public void editableToggles() throws Exception {
        TextArea c = new TextArea(el("TextArea", "text", "x", "editable", "true"));
        assertEquals("true", c.editable());
        c.editable("false");
        assertEquals("false", c.editable());
        c.editable("TRUE"); // case-insensitive
        assertEquals("true", c.editable());
    }

    @Test
    public void scrollToggles() throws Exception {
        TextArea c = new TextArea(el("TextArea", "text", "x", "scroll", "true"));
        assertEquals("true", c.scroll());
        c.scroll("false");
        assertEquals("false", c.scroll());
    }

    @Test
    public void toStringIncludesTextWhenPresent() throws Exception {
        assertEquals("TextArea text='Hi'", new TextArea(el("TextArea", "text", "Hi")).toString());
    }

    @Test
    public void actuatorsAreTheDocumentedSet() throws Exception {
        assertArrayEquals(new String[] {"enable", "text", "columns", "rows", "editable", "scroll"},
                          new TextArea(el("TextArea", "text", "x")).actuators());
    }
}
