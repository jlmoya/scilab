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

package org.scilab.modules.preferences;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/**
 * Hermetic unit tests for {@link XUpdateVisitor#isVisible(Node)}, the pure
 * predicate that decides which DOM nodes map to a Swing component and which are
 * "invisible" bookkeeping nodes (event descriptors, chooser options, table
 * descriptors, pure-whitespace text, ...).
 *
 * <p>The rest of {@code XUpdateVisitor} drives live Swing containers and the
 * config manager, so it is out of scope here; {@code isVisible} touches only the
 * node's name/value and needs no Scilab runtime. Nodes are built with JAXP.
 */
public class XUpdateVisitorTest {

    private static Document newDoc() throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
    }

    private XUpdateVisitor visitor() {
        Map<Component, XSentinel> empty = new HashMap<Component, XSentinel>();
        return new XUpdateVisitor(empty);
    }

    @Test
    public void eventDescriptorNodesAreInvisible() throws Exception {
        Document doc = newDoc();
        XUpdateVisitor v = visitor();
        assertFalse(v.isVisible(doc.createElement("mouseClicked")));
        assertFalse(v.isVisible(doc.createElement("actionPerformed")));
        assertFalse(v.isVisible(doc.createElement("entryChanged")));
    }

    @Test
    public void propertyChangeNodesAreInvisibleByPrefix() throws Exception {
        Document doc = newDoc();
        XUpdateVisitor v = visitor();
        assertFalse(v.isVisible(doc.createElement("propertyChange")));
        // Matched by startsWith(...), so a suffixed variant is invisible too.
        assertFalse(v.isVisible(doc.createElement("propertyChangeName")));
    }

    @Test
    public void chooserAndDescriptorNodesAreInvisible() throws Exception {
        Document doc = newDoc();
        XUpdateVisitor v = visitor();
        assertFalse(v.isVisible(doc.createElement("option")));
        assertFalse(v.isVisible(doc.createElement("listElement")));
        assertFalse(v.isVisible(doc.createElement("html")));
    }

    @Test
    public void tableDescriptorNodesAreInvisibleByPrefix() throws Exception {
        Document doc = newDoc();
        XUpdateVisitor v = visitor();
        assertFalse(v.isVisible(doc.createElement("table")));
        assertFalse(v.isVisible(doc.createElement("tableColumn")));
    }

    @Test
    public void whitespaceOnlyTextNodesAreInvisible() throws Exception {
        Document doc = newDoc();
        XUpdateVisitor v = visitor();
        assertFalse(v.isVisible(doc.createTextNode("   ")));
        assertFalse(v.isVisible(doc.createTextNode("\t\n ")));
        assertFalse(v.isVisible(doc.createTextNode("")), "empty text node is invisible");
    }

    @Test
    public void textNodeWithVisibleCharactersIsVisible() throws Exception {
        Document doc = newDoc();
        XUpdateVisitor v = visitor();
        assertTrue(v.isVisible(doc.createTextNode("hello")));
        // Not entirely whitespace => the whitespace-only rule does not fire.
        assertTrue(v.isVisible(doc.createTextNode("  hi  ")));
    }

    @Test
    public void ordinaryComponentNodesAreVisible() throws Exception {
        Document doc = newDoc();
        XUpdateVisitor v = visitor();
        assertTrue(v.isVisible(doc.createElement("VBox")));
        assertTrue(v.isVisible(doc.createElement("Button")));
        assertTrue(v.isVisible(doc.createElement("Label")));
    }
}
