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
import static org.junit.jupiter.api.Assertions.assertNull;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Hermetic unit tests for the {@link Select} preference component (a
 * {@link javax.swing.JComboBox}). The constructor walks the {@code <option>} child
 * nodes to build the item list and a value&rarr;key map; {@code choose()} returns
 * the key of the selected item (or the value itself when no key is given). All of
 * this is pure DOM/collection logic; no native code or display is required.
 */
public class SelectTest {

    private static Document doc() throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
    }

    private static Element option(Document d, String... kv) {
        Element e = d.createElement("option");
        for (int i = 0; i + 1 < kv.length; i += 2) {
            e.setAttribute(kv[i], kv[i + 1]);
        }
        return e;
    }

    @Test
    public void constructorBuildsItemsFromOptionChildren() throws Exception {
        Document d = doc();
        Element sel = d.createElement("Select");
        sel.appendChild(option(d, "value", "Alpha"));
        sel.appendChild(option(d, "value", "Beta"));
        Select c = new Select(sel);
        assertEquals(2, c.getItemCount());
        assertEquals("Alpha", c.getItemAt(0));
        assertEquals("Beta", c.getItemAt(1));
    }

    @Test
    public void selectedOptionDrivesTheSelectedIndex() throws Exception {
        Document d = doc();
        Element sel = d.createElement("Select");
        sel.appendChild(option(d, "value", "Alpha"));
        sel.appendChild(option(d, "value", "Beta", "selected", "selected"));
        Select c = new Select(sel);
        assertEquals(1, c.getSelectedIndex());
        assertEquals("Beta", c.getSelectedItem());
    }

    @Test
    public void chooseMapsTheSelectedValueToItsKey() throws Exception {
        Document d = doc();
        Element sel = d.createElement("Select");
        sel.appendChild(option(d, "value", "Alpha", "key", "k1", "selected", "selected"));
        sel.appendChild(option(d, "value", "Beta", "key", "k2"));
        Select c = new Select(sel);
        assertEquals("k1", c.choose());
    }

    @Test
    public void chooseFallsBackToTheValueWhenNoKeyGiven() throws Exception {
        Document d = doc();
        Element sel = d.createElement("Select");
        sel.appendChild(option(d, "value", "Alpha", "selected", "selected"));
        Select c = new Select(sel);
        assertEquals("Alpha", c.choose(), "a keyless option maps its value to itself");
    }

    @Test
    public void chooseReturnsNullWhenDisabled() throws Exception {
        Document d = doc();
        Element sel = d.createElement("Select");
        sel.appendChild(option(d, "value", "Alpha", "selected", "selected"));
        Select c = new Select(sel);
        c.setEnabled(false);
        assertNull(c.choose(), "a disabled Select declines to answer");
    }

    @Test
    public void nonOptionChildrenAreIgnored() throws Exception {
        Document d = doc();
        Element sel = d.createElement("Select");
        sel.appendChild(option(d, "value", "Alpha"));
        sel.appendChild(d.createElement("comment")); // not an <option>
        Select c = new Select(sel);
        assertEquals(1, c.getItemCount());
    }

    @Test
    public void actuatorsAndToString() throws Exception {
        Document d = doc();
        Element sel = d.createElement("Select");
        Select c = new Select(sel);
        assertArrayEquals(new String[] {"enable"}, c.actuators());
        assertEquals("Select", c.toString());
    }
}
