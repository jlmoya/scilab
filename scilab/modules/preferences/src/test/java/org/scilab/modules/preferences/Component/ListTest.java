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
 * Hermetic unit tests for {@link List} (a preference {@link javax.swing.JList}
 * host) and its DOM-backed static {@code List.Model}. The model scans
 * {@code <listElement>} children and yields their {@code name} attribute; the
 * List component's item/nb-visible-rows sensors and actuators are pure Swing
 * selection state. Constructed headless &mdash; no display required.
 */
public class ListTest {

    private static Document doc() throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
    }

    private static Element listElement(Document d, String name) {
        Element e = d.createElement("listElement");
        e.setAttribute("name", name);
        return e;
    }

    /** A &lt;List&gt; peer with the given element names, plus a noise child. */
    private static Element listPeer(Document d, String... names) {
        Element peer = d.createElement("List");
        for (String n : names) {
            peer.appendChild(listElement(d, n));
        }
        peer.appendChild(d.createElement("notAnElement"));
        return peer;
    }

    // ----- List.Model (pure DOM) -----

    @Test
    public void modelSizeCountsOnlyListElements() throws Exception {
        Document d = doc();
        List.Model m = new List.Model(listPeer(d, "Alpha", "Beta", "Gamma"));
        assertEquals(3, m.getSize(), "the trailing <notAnElement> child is ignored");
    }

    @Test
    public void modelElementAtReturnsTheNameAttribute() throws Exception {
        Document d = doc();
        List.Model m = new List.Model(listPeer(d, "Alpha", "Beta"));
        assertEquals("Alpha", m.getElementAt(0));
        assertEquals("Beta", m.getElementAt(1));
    }

    @Test
    public void modelElementAtOutOfRangeIsNull() throws Exception {
        Document d = doc();
        List.Model m = new List.Model(listPeer(d, "Alpha"));
        assertNull(m.getElementAt(5));
    }

    @Test
    public void modelSizeOfAnEmptyListIsZero() throws Exception {
        Document d = doc();
        List.Model m = new List.Model(listPeer(d));
        assertEquals(0, m.getSize());
    }

    // ----- List component -----

    @Test
    public void actuatorsAreTheDocumentedSet() throws Exception {
        Document d = doc();
        List c = new List(listPeer(d, "Alpha"));
        assertArrayEquals(new String[] {"item", "enable", "nb-visible-rows"}, c.actuators());
    }

    @Test
    public void freshListSelectsItsFirstElement() throws Exception {
        Document d = doc();
        List c = new List(listPeer(d, "Alpha", "Beta"));
        assertEquals("Alpha", c.item(), "with nothing pre-selected, item() falls back to element 0");
        assertEquals("Alpha", c.choose());
    }

    @Test
    public void itemActuatorMovesTheSelection() throws Exception {
        Document d = doc();
        List c = new List(listPeer(d, "Alpha", "Beta"));
        c.item("Beta");
        assertEquals("Beta", c.item());
        assertEquals("Beta", c.choose());
    }

    @Test
    public void nbVisibleRowsDefaultsToFiveAndIsSettable() throws Exception {
        Document d = doc();
        List c = new List(listPeer(d, "Alpha"));
        assertEquals("5", c.nbvisible(), "refresh applies the documented default of 5");
        c.nbvisible("8");
        assertEquals("8", c.nbvisible());
    }

    @Test
    public void nbVisibleRowsIgnoresNonNumericInput() throws Exception {
        Document d = doc();
        List c = new List(listPeer(d, "Alpha"));
        c.nbvisible("8");
        c.nbvisible("not-a-number"); // NumberFormatException is swallowed
        assertEquals("8", c.nbvisible(), "a bad value leaves the previous count untouched");
    }

    @Test
    public void toStringIsTheStableSignature() throws Exception {
        Document d = doc();
        assertEquals("List ...", new List(listPeer(d, "Alpha")).toString());
    }
}
