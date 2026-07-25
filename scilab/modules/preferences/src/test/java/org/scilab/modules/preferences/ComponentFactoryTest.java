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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;

import javax.swing.JLabel;
import javax.swing.JPanel;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.scilab.modules.preferences.Component.Scroll;
import org.scilab.modules.preferences.Component.VBox;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Hermetic unit tests for the package-private {@link ComponentFactory}, whose
 * {@code getComponent(Node)} maps a DOM node to a Swing {@link Component}. Three
 * paths are pure and display-free and are exercised here:
 *
 * <ul>
 *   <li>the hard-coded <em>action</em> table ({@code #text}, {@code VSpace},
 *       {@code HSpace}, {@code Glue}, {@code Scroll}) that builds a component
 *       directly without reflection;</li>
 *   <li>the reflective path that instantiates
 *       {@code org.scilab.modules.preferences.Component.<Tag>} via its
 *       {@code (Node)} constructor, plus the constructor cache that a second
 *       lookup of the same tag hits;</li>
 *   <li>the failure path: an unknown tag yields a red-bordered {@code XStub}
 *       (a {@link JPanel}) rather than throwing.</li>
 * </ul>
 *
 * The two typed struts read an {@code int} attribute through the manager's
 * {@code getInt} helper (declared on {@code XCommonManager}, so no JNI-bound
 * {@code XConfigManager} initializer runs). Nodes come from JAXP; no Scilab
 * runtime, native code or display is required.
 */
public class ComponentFactoryTest {

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

    // ----- action table -----------------------------------------------------

    @Test
    public void textNodeBecomesAJLabelCarryingItsValue() throws Exception {
        Document doc = newDoc();
        Component c = ComponentFactory.getComponent(doc.createTextNode("hello world"));
        JLabel label = assertInstanceOf(JLabel.class, c);
        assertEquals("hello world", label.getText());
    }

    @Test
    public void vspaceBecomesAVerticalStrutOfTheGivenHeight() throws Exception {
        Document doc = newDoc();
        Component c = ComponentFactory.getComponent(el(doc, "VSpace", "height", "23"));
        assertEquals(23, c.getPreferredSize().height, "vertical strut pins its preferred height");
        assertEquals(0, c.getPreferredSize().width, "a vertical strut has no preferred width");
    }

    @Test
    public void hspaceBecomesAHorizontalStrutOfTheGivenWidth() throws Exception {
        Document doc = newDoc();
        Component c = ComponentFactory.getComponent(el(doc, "HSpace", "width", "31"));
        assertEquals(31, c.getPreferredSize().width, "horizontal strut pins its preferred width");
        assertEquals(0, c.getPreferredSize().height, "a horizontal strut has no preferred height");
    }

    @Test
    public void spaceNodesFallBackToTheDefaultGapWhenTheDimensionIsAbsent() throws Exception {
        Document doc = newDoc();
        // No "height"/"width" attribute => the SPACE default (5) is used.
        assertEquals(5, ComponentFactory.getComponent(el(doc, "VSpace")).getPreferredSize().height);
        assertEquals(5, ComponentFactory.getComponent(el(doc, "HSpace")).getPreferredSize().width);
    }

    @Test
    public void glueNodeBecomesAComponent() throws Exception {
        Document doc = newDoc();
        assertNotNull(ComponentFactory.getComponent(el(doc, "Glue")));
    }

    @Test
    public void scrollNodeBecomesAScrollPane() throws Exception {
        Document doc = newDoc();
        Component c = ComponentFactory.getComponent(el(doc, "Scroll"));
        assertInstanceOf(Scroll.class, c);
    }

    // ----- reflective construction + cache ----------------------------------

    @Test
    public void knownTagIsInstantiatedReflectivelyThroughItsNodeConstructor() throws Exception {
        Document doc = newDoc();
        Component c = ComponentFactory.getComponent(el(doc, "VBox"));
        assertInstanceOf(VBox.class, c);
    }

    @Test
    public void repeatedTagLookupReusesTheCachedConstructor() throws Exception {
        Document doc = newDoc();
        // Two independent instances, both freshly built: the second call takes
        // the "constructor already cached" branch. They must not be the same
        // object but must be the same runtime type.
        Component first = ComponentFactory.getComponent(el(doc, "VBox"));
        Component second = ComponentFactory.getComponent(el(doc, "VBox"));
        assertInstanceOf(VBox.class, first);
        assertInstanceOf(VBox.class, second);
        assertTrue(first != second, "each lookup returns a fresh component instance");
    }

    // ----- failure path -----------------------------------------------------

    @Test
    public void unknownTagYieldsAnXStubPanelInsteadOfThrowing() throws Exception {
        Document doc = newDoc();
        Component c = ComponentFactory.getComponent(el(doc, "NoSuchComponent"));
        // XStub is a private JPanel subclass whose toString() is the sentinel "STUB".
        assertInstanceOf(JPanel.class, c);
        assertEquals("STUB", c.toString());
    }

    @Test
    public void distinctUnknownTagsEachYieldTheirOwnStub() throws Exception {
        Document doc = newDoc();
        Component a = ComponentFactory.getComponent(el(doc, "Bogus1"));
        Component b = ComponentFactory.getComponent(el(doc, "Bogus2"));
        assertEquals("STUB", a.toString());
        assertEquals("STUB", b.toString());
        assertTrue(a != b);
    }

    @Test
    public void textActionIsSharedAndDrivenSolelyByNodeName() throws Exception {
        Document doc = newDoc();
        // Two text nodes with different values still both route through the
        // single "#text" action; only the value differs on the produced label.
        JLabel a = assertInstanceOf(JLabel.class, ComponentFactory.getComponent(doc.createTextNode("A")));
        JLabel b = assertInstanceOf(JLabel.class, ComponentFactory.getComponent(doc.createTextNode("B")));
        assertEquals("A", a.getText());
        assertEquals("B", b.getText());
        assertSame(JLabel.class, a.getClass());
    }
}
