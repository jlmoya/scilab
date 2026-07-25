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
 * Hermetic unit tests for the {@link Checkbox} preference component. The
 * checked/text sensors and actuators are pure Swing state, and {@code choose()}
 * maps the checked/unchecked state onto the configured selected-value /
 * unselected-value (falling back to the literals "checked"/"unchecked").
 * Constructed headless &mdash; no display required.
 */
public class CheckboxTest {

    private static Element el(String name, String... kv) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element e = doc.createElement(name);
        for (int i = 0; i + 1 < kv.length; i += 2) {
            e.setAttribute(kv[i], kv[i + 1]);
        }
        return e;
    }

    @Test
    public void constructorReadsTextCheckedAndValues() throws Exception {
        Checkbox c = new Checkbox(el("Checkbox",
                                     "text", "Enable",
                                     "checked", "checked",
                                     "selected-value", "on",
                                     "unselected-value", "off"));
        assertEquals("Enable", c.text());
        assertEquals("checked", c.checked());
        assertEquals("on", c.selected());
        assertEquals("off", c.unselected());
    }

    @Test
    public void chooseReturnsSelectedValueWhenChecked() throws Exception {
        Checkbox c = new Checkbox(el("Checkbox", "checked", "checked", "selected-value", "on"));
        assertEquals("on", c.choose());
    }

    @Test
    public void chooseFallsBackToCheckedLiteralWithoutASelectedValue() throws Exception {
        Checkbox c = new Checkbox(el("Checkbox", "checked", "checked"));
        assertEquals("checked", c.choose());
    }

    @Test
    public void chooseReturnsUnselectedValueWhenUnchecked() throws Exception {
        Checkbox c = new Checkbox(el("Checkbox", "unselected-value", "off"));
        assertEquals("off", c.choose(), "an unchecked box with no checked attribute yields its unselected value");
    }

    @Test
    public void chooseFallsBackToUncheckedLiteral() throws Exception {
        Checkbox c = new Checkbox(el("Checkbox"));
        assertEquals("unchecked", c.choose());
    }

    @Test
    public void checkedActuatorTogglesSelection() throws Exception {
        Checkbox c = new Checkbox(el("Checkbox"));
        c.checked("true");
        assertEquals("checked", c.checked());
        c.checked("unchecked");
        assertEquals("unchecked", c.checked());
    }

    @Test
    public void textActuatorAndSensorRoundTrip() throws Exception {
        Checkbox c = new Checkbox(el("Checkbox", "text", "Enable"));
        c.text("Disable");
        assertEquals("Disable", c.text());
    }

    @Test
    public void toStringListsTextAndCheckedState() throws Exception {
        Checkbox c = new Checkbox(el("Checkbox", "text", "Enable", "checked", "checked"));
        assertEquals("CHECKBOX text='Enable' checked='checked'", c.toString());
    }

    @Test
    public void actuatorsAreTheDocumentedSet() throws Exception {
        assertArrayEquals(new String[] {"enable", "text", "checked", "selected-value", "unselected-value"},
                          new Checkbox(el("Checkbox")).actuators());
    }
}
