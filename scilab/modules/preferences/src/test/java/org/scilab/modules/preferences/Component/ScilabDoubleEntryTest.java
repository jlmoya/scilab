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
 * Hermetic unit tests for {@link ScilabDoubleEntry} (a
 * {@link javax.swing.JFormattedTextField} restricted to a single Scilab double).
 * The value round-trip, the scientific {@code choose()} formatting and the
 * fail-soft parsing are pure {@code BigDecimal}/{@code DecimalFormat} logic; no
 * native code or display is required.
 */
public class ScilabDoubleEntryTest {

    private static Element el(String name, String... kv) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element e = doc.createElement(name);
        for (int i = 0; i + 1 < kv.length; i += 2) {
            e.setAttribute(kv[i], kv[i + 1]);
        }
        return e;
    }

    @Test
    public void defaultValueIsZeroAndEditable() throws Exception {
        ScilabDoubleEntry c = new ScilabDoubleEntry(el("ScilabDoubleEntry"));
        assertEquals("0", c.value());
        assertTrue(c.isEditable());
    }

    @Test
    public void constructorReadsTheValueAttribute() throws Exception {
        ScilabDoubleEntry c = new ScilabDoubleEntry(el("ScilabDoubleEntry", "value", "1.5"));
        assertEquals("1.5", c.value());
    }

    @Test
    public void valueActuatorAndSensorRoundTrip() throws Exception {
        ScilabDoubleEntry c = new ScilabDoubleEntry(el("ScilabDoubleEntry"));
        c.value("2.5");
        assertEquals("2.5", c.value());
    }

    @Test
    public void valueActuatorSilentlyIgnoresGarbage() throws Exception {
        // Unlike NumericalSpinner.value, this one swallows the format error.
        ScilabDoubleEntry c = new ScilabDoubleEntry(el("ScilabDoubleEntry", "value", "3.25"));
        c.value("not-a-number");
        assertEquals("3.25", c.value(), "an unparsable value leaves the previous value untouched");
    }

    @Test
    public void chooseFormatsInScilabScientificNotation() throws Exception {
        ScilabDoubleEntry c = new ScilabDoubleEntry(el("ScilabDoubleEntry", "value", "2.5"));
        assertEquals("2.5E00", c.choose());
    }

    @Test
    public void editableAttributeIsHonoured() throws Exception {
        ScilabDoubleEntry c = new ScilabDoubleEntry(el("ScilabDoubleEntry", "editable", "false"));
        assertFalse(c.isEditable());
    }

    @Test
    public void actuatorsAreTheDocumentedSet() throws Exception {
        assertArrayEquals(new String[] {"enable", "value", "editable"},
                          new ScilabDoubleEntry(el("ScilabDoubleEntry")).actuators());
    }
}
