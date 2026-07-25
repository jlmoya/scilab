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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.awt.BorderLayout;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.scilab.modules.preferences.XComponent;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Hermetic unit tests for the {@link Scroll} preference component (a
 * {@link javax.swing.JScrollPane} that redirects {@code XComponent} children into
 * an inner container while letting system adds fall through to the pane). The
 * add/remove/count/get overrides are pure container bookkeeping; constructed
 * headless with a plain inner {@link javax.swing.JPanel}.
 */
public class ScrollTest {

    /** A minimal AWT component that is also an {@code XComponent}, so Scroll routes
     *  it into the inner container. */
    private static final class Stub extends JLabel implements XComponent {
        public String[] actuators() {
            return new String[0];
        }
        public void refresh(Node peer) { }
    }

    private static Element el(String name) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        return doc.createElement(name);
    }

    private static Scroll scroll() throws Exception {
        return new Scroll(el("Scroll"), new JPanel());
    }

    @Test
    public void actuatorsAreEmpty() throws Exception {
        assertEquals(0, scroll().actuators().length);
    }

    @Test
    public void toStringIsStable() throws Exception {
        assertEquals("Scroll", scroll().toString());
    }

    @Test
    public void freshScrollHasNoInnerChildren() throws Exception {
        assertEquals(0, scroll().getXComponentCount());
    }

    @Test
    public void xComponentAddsAreRoutedIntoTheInnerContainer() throws Exception {
        Scroll s = scroll();
        Stub center = new Stub();
        Stub south = new Stub();
        s.add(center, BorderLayout.CENTER);
        s.add(south, BorderLayout.SOUTH);
        assertEquals(2, s.getXComponentCount());
        assertSame(center, s.getXComponent(0));
        assertSame(south, s.getXComponent(1));
    }

    @Test
    public void removeTakesAnXComponentOutOfTheInnerContainer() throws Exception {
        Scroll s = scroll();
        Stub center = new Stub();
        Stub south = new Stub();
        s.add(center, BorderLayout.CENTER);
        s.add(south, BorderLayout.SOUTH);
        s.remove(center);
        assertEquals(1, s.getXComponentCount());
        assertSame(south, s.getXComponent(0));
    }

    @Test
    public void indexedAddInsertsAtTheGivenPosition() throws Exception {
        Scroll s = scroll();
        Stub center = new Stub();
        Stub first = new Stub();
        s.add(center, BorderLayout.CENTER);
        s.add(first, BorderLayout.SOUTH, 0); // three-arg overload, inserted at index 0
        assertEquals(2, s.getXComponentCount());
        assertSame(first, s.getXComponent(0));
    }
}
