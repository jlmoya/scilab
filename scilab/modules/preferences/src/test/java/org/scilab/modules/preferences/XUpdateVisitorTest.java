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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Hermetic unit tests for the pure, display-free surface of
 * {@link XUpdateVisitor}:
 *
 * <ul>
 *   <li>{@link XUpdateVisitor#isVisible(Node)} — the predicate deciding which
 *       DOM nodes map to a Swing component and which are "invisible" bookkeeping
 *       nodes (event descriptors, chooser options, table descriptors,
 *       pure-whitespace text, ...);</li>
 *   <li>{@code getLayoutConstraints} — the constraint object handed to
 *       {@code Container.add} (border side / the node itself for a Grid / null);</li>
 *   <li>{@code buildPeerFor} / {@code build} — component synthesis into a plain
 *       AWT container;</li>
 *   <li>{@code forget} — removal from both the container and the correspondence
 *       map;</li>
 *   <li>{@code addListeners} — wiring an {@link XSentinel} onto a component
 *       according to the node's {@code listener} attribute.</li>
 * </ul>
 *
 * <p>The full {@code visit} diff drives live config-manager state, so it is out
 * of scope; everything above needs only JAXP nodes and lightweight Swing
 * containers built headless. No Scilab runtime is required.
 */
public class XUpdateVisitorTest {

    private static Document newDoc() throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
    }

    private static Element el(Document doc, String name, String... kv) {
        Element e = doc.createElement(name);
        for (int i = 0; i + 1 < kv.length; i += 2) {
            e.setAttribute(kv[i], kv[i + 1]);
        }
        return e;
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

    // ----- getLayoutConstraints ---------------------------------------------

    @Test
    public void borderLayoutParentYieldsTheChildBorderSide() throws Exception {
        Document doc = newDoc();
        XUpdateVisitor v = visitor();
        Element parent = el(doc, "Panel", "layout", "border");
        Element child = el(doc, "Button", "border-side", "North");
        assertEquals("North", v.getLayoutConstraints(parent, child));
    }

    @Test
    public void gridParentYieldsTheChildNodeItselfAsConstraint() throws Exception {
        Document doc = newDoc();
        XUpdateVisitor v = visitor();
        Element parent = el(doc, "Grid");
        Element child = el(doc, "Button");
        // The Grid layout uses the node itself (it reads gridx/gridy off it).
        assertSame(child, v.getLayoutConstraints(parent, child));
    }

    @Test
    public void ordinaryParentYieldsNoConstraint() throws Exception {
        Document doc = newDoc();
        XUpdateVisitor v = visitor();
        // Neither a border layout nor a Grid => no constraint object.
        assertNull(v.getLayoutConstraints(el(doc, "VBox"), el(doc, "Button")));
        assertNull(v.getLayoutConstraints(el(doc, "Panel", "layout", "vbox"), el(doc, "Button")));
    }

    // ----- buildPeerFor / build ---------------------------------------------

    @Test
    public void buildPeerForMapsATextNodeToALabel() throws Exception {
        Document doc = newDoc();
        XUpdateVisitor v = visitor();
        Component c = v.buildPeerFor(doc.createTextNode("caption"));
        JLabel label = assertInstanceOf(JLabel.class, c);
        assertEquals("caption", label.getText());
    }

    @Test
    public void buildInsertsTheSynthesizedComponentIntoTheContainer() throws Exception {
        Document doc = newDoc();
        XUpdateVisitor v = visitor();
        JPanel view = new JPanel();
        Element peer = el(doc, "VBox");
        assertEquals(0, view.getComponentCount());

        // -1 => append; item is a text node so the built peer is a JLabel.
        Component built = v.build(view, peer, doc.createTextNode("row"), -1);
        assertInstanceOf(JLabel.class, built);
        assertEquals(1, view.getComponentCount());
        assertSame(built, view.getComponent(0), "the built component is the one that was added");
    }

    // ----- forget -----------------------------------------------------------

    @Test
    public void forgetRemovesFromBothContainerAndCorrespondence() throws Exception {
        Document doc = newDoc();
        Map<Component, XSentinel> matching = new HashMap<Component, XSentinel>();
        XUpdateVisitor v = new XUpdateVisitor(matching);

        JPanel view = new JPanel();
        JLabel child = new JLabel("x");
        view.add(child);
        matching.put(child, new XSentinel(child, el(doc, "Label")));
        assertEquals(1, view.getComponentCount());
        assertTrue(matching.containsKey(child));

        v.forget(view, child);
        assertEquals(0, view.getComponentCount(), "container no longer holds the component");
        assertFalse(matching.containsKey(child), "correspondence map no longer holds the component");
    }

    // ----- addListeners -----------------------------------------------------

    @Test
    public void actionListenerNodeWiresTheSentinelOntoAButton() throws Exception {
        Document doc = newDoc();
        XUpdateVisitor v = visitor();
        JButton button = new JButton();
        Node node = el(doc, "Button", "listener", "ActionListener");
        XSentinel sentinel = new XSentinel(button, node);

        assertEquals(0, button.getActionListeners().length);
        v.addListeners(button, node, sentinel);
        assertEquals(1, button.getActionListeners().length);
        assertSame(sentinel, button.getActionListeners()[0]);
    }

    @Test
    public void keyListenerNodeWiresTheSentinelAsAKeyListener() throws Exception {
        Document doc = newDoc();
        XUpdateVisitor v = visitor();
        JLabel comp = new JLabel();
        Node node = el(doc, "Label", "listener", "KeyListener");
        XSentinel sentinel = new XSentinel(comp, node);

        int before = comp.getKeyListeners().length;
        v.addListeners(comp, node, sentinel);
        assertEquals(before + 1, comp.getKeyListeners().length);
    }

    @Test
    public void mouseListenerNodeWiresTheSentinelAsAMouseListener() throws Exception {
        Document doc = newDoc();
        XUpdateVisitor v = visitor();
        JLabel comp = new JLabel();
        Node node = el(doc, "Label", "listener", "MouseListener");
        XSentinel sentinel = new XSentinel(comp, node);

        int before = comp.getMouseListeners().length;
        v.addListeners(comp, node, sentinel);
        assertEquals(before + 1, comp.getMouseListeners().length);
    }

    @Test
    public void propertyChangeListenerWithNamedPropertyIsScopedToThatProperty() throws Exception {
        Document doc = newDoc();
        XUpdateVisitor v = visitor();
        JButton comp = new JButton();
        Node node = el(doc, "Button", "listener", "PropertyChangeListener#enabled");
        XSentinel sentinel = new XSentinel(comp, node);

        assertEquals(0, comp.getPropertyChangeListeners("enabled").length);
        v.addListeners(comp, node, sentinel);
        assertEquals(1, comp.getPropertyChangeListeners("enabled").length,
                     "the sentinel is registered only for the named property");
    }

    @Test
    public void absentListenerAttributeWiresNothing() throws Exception {
        Document doc = newDoc();
        XUpdateVisitor v = visitor();
        JButton button = new JButton();
        Node node = el(doc, "Button"); // no "listener" attribute
        XSentinel sentinel = new XSentinel(button, node);

        v.addListeners(button, node, sentinel);
        assertEquals(0, button.getActionListeners().length, "no listener attribute => no wiring");
    }
}
