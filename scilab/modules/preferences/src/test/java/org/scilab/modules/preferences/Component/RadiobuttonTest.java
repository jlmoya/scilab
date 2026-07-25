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
import org.scilab.modules.preferences.XCommonManager;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Hermetic unit tests for the {@link Radiobutton} preference component. Beyond
 * the text/checked sensors, the interesting logic is the expected-value mode:
 * when {@code checked} is absent, the button derives its selection by comparing
 * {@code value} with {@code expected-value}, and {@code choose()} then always
 * answers with the expected value. Constructed headless.
 */
public class RadiobuttonTest {

    private static Element el(String name, String... kv) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element e = doc.createElement(name);
        for (int i = 0; i + 1 < kv.length; i += 2) {
            e.setAttribute(kv[i], kv[i + 1]);
        }
        return e;
    }

    @Test
    public void constructorReadsTextAndChecked() throws Exception {
        Radiobutton c = new Radiobutton(el("Radiobutton", "text", "Opt", "checked", "checked"));
        assertEquals("Opt", c.text());
        assertEquals("checked", c.checked());
    }

    @Test
    public void explicitCheckedYieldsCheckedChoice() throws Exception {
        Radiobutton c = new Radiobutton(el("Radiobutton", "text", "Opt", "checked", "checked"));
        assertEquals("checked", c.choose(), "with no expected-value, choose reports the selection literal");
    }

    @Test
    public void matchingExpectedValueSelectsAndDrivesChoose() throws Exception {
        Radiobutton c = new Radiobutton(el("Radiobutton", "value", "X", "expected-value", "X"));
        assertEquals("checked", c.checked(), "value == expected-value selects the button");
        assertEquals("X", c.choose());
    }

    @Test
    public void expectedValueDrivesChooseEvenWhenUnselected() throws Exception {
        // Defect characterization: when expected-value is set, choose() returns it
        // UNCONDITIONALLY (before consulting the selected state), so a de-selected
        // radio still answers with its expected value.
        Radiobutton c = new Radiobutton(el("Radiobutton", "value", "Y", "expected-value", "X"));
        assertEquals("unchecked", c.checked(), "value != expected-value leaves it de-selected");
        assertEquals("X", c.choose(), "choose still returns the expected value regardless of selection");
    }

    @Test
    public void checkedActuatorTogglesSelection() throws Exception {
        Radiobutton c = new Radiobutton(el("Radiobutton"));
        c.checked("checked");
        assertEquals("checked", c.checked());
        c.checked("unchecked");
        assertEquals("unchecked", c.checked());
    }

    @Test
    public void textActuatorAndSensorRoundTrip() throws Exception {
        Radiobutton c = new Radiobutton(el("Radiobutton", "text", "Opt"));
        c.text("Other");
        assertEquals("Other", c.text());
    }

    @Test
    public void toStringListsTextAndCheckedState() throws Exception {
        Radiobutton c = new Radiobutton(el("Radiobutton", "text", "Opt", "checked", "checked"));
        assertEquals("RadioButton text='Opt' checked='checked'", c.toString());
    }

    @Test
    public void actuatorsAreTheDocumentedSet() throws Exception {
        assertArrayEquals(new String[] {"enable", "text", "checked", "value", "expected-value"},
                          new Radiobutton(el("Radiobutton")).actuators());
    }

    @Test
    public void bareRadioSelfSelectsBecauseAbsentValuesBothResolveToNav() throws Exception {
        // Defect characterization: with neither 'value' nor 'expected-value' present,
        // both read back as the NAV sentinel and compare EQUAL, so refresh SELECTS the
        // button and stores NAV as its expected value -> choose() returns the sentinel,
        // not the "unchecked" literal a caller might expect.
        Radiobutton c = new Radiobutton(el("Radiobutton", "text", "Opt"));
        assertEquals("checked", c.checked(), "value(NAV) == expected-value(NAV) selects the button");
        assertEquals(XCommonManager.NAV, c.choose());
    }
}
