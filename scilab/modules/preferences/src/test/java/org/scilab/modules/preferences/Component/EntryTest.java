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
import static org.junit.jupiter.api.Assertions.assertThrows;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.scilab.modules.preferences.XCommonManager;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Hermetic unit tests for the {@link Entry} preference component (a
 * {@link javax.swing.JPasswordField}). The text/columns actuators and the
 * {@code choose()} response are pure Swing state; no native code or display is
 * required. {@code text()} reads back through {@code getPassword()}.
 */
public class EntryTest {

    private static Element el(String name, String... kv) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element e = doc.createElement(name);
        for (int i = 0; i + 1 < kv.length; i += 2) {
            e.setAttribute(kv[i], kv[i + 1]);
        }
        return e;
    }

    @Test
    public void constructorReadsTextAndColumns() throws Exception {
        Entry c = new Entry(el("Entry", "text", "abc", "columns", "10"));
        assertEquals("abc", c.text());
        assertEquals("10", c.columns());
    }

    @Test
    public void textActuatorAndSensorRoundTrip() throws Exception {
        Entry c = new Entry(el("Entry"));
        c.text("secret");
        assertEquals("secret", c.text());
    }

    @Test
    public void chooseReturnsTheCurrentText() throws Exception {
        Entry c = new Entry(el("Entry", "text", "pw"));
        assertEquals("pw", c.choose());
    }

    @Test
    public void absentTextBecomesNavSentinel() throws Exception {
        Entry c = new Entry(el("Entry"));
        assertEquals(XCommonManager.NAV, c.text());
    }

    @Test
    public void columnsActuatorAndSensorRoundTrip() throws Exception {
        Entry c = new Entry(el("Entry"));
        c.columns("12");
        assertEquals("12", c.columns());
        assertEquals(12, c.getColumns());
    }

    /**
     * Defect characterization: {@code columns(String)} does {@code Integer.parseInt}
     * with no guard, so a non-numeric value throws rather than being ignored.
     */
    @Test
    public void columnsActuatorThrowsOnNonNumericInput() throws Exception {
        Entry c = new Entry(el("Entry"));
        assertThrows(NumberFormatException.class, () -> c.columns("wide"));
    }

    @Test
    public void toStringIncludesTextWhenPresent() throws Exception {
        assertEquals("Entry text='abc'", new Entry(el("Entry", "text", "abc")).toString());
    }

    @Test
    public void actuatorsAreTheDocumentedSet() throws Exception {
        assertArrayEquals(new String[] {"enable", "text", "columns", "lines", "editable"},
                          new Entry(el("Entry")).actuators());
    }
}
