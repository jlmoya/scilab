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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Hermetic unit tests for the {@link HBox} preference component (a {@link Panel}
 * with a horizontal {@code BoxLayout}). The alignment-forcing {@code add} override,
 * the empty actuator set and the no-op {@code refresh} are pure Swing state; no
 * native code or display is required.
 */
public class HBoxTest {

    private static Element el(String name) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        return doc.createElement(name);
    }

    @Test
    public void layoutIsAHorizontalBoxLayout() throws Exception {
        HBox c = new HBox(el("HBox"));
        assertTrue(c.getLayout() instanceof BoxLayout);
    }

    @Test
    public void addForcesLeftTopAlignmentOnChildren() throws Exception {
        HBox c = new HBox(el("HBox"));
        JLabel child = new JLabel("x");
        c.add(child, null);
        assertEquals(Component.LEFT_ALIGNMENT, child.getAlignmentX(), 0.0f);
        assertEquals(Component.TOP_ALIGNMENT, child.getAlignmentY(), 0.0f);
        assertEquals(1, c.getComponentCount());
    }

    @Test
    public void refreshIsANoOp() throws Exception {
        HBox c = new HBox(el("HBox"));
        c.refresh(el("HBox"));
        assertEquals(0, c.getComponentCount(), "refresh changes nothing");
    }

    @Test
    public void actuatorsAreEmptyAndToStringIsTheLabel() throws Exception {
        HBox c = new HBox(el("HBox"));
        assertArrayEquals(new String[] {}, c.actuators());
        assertEquals("HBox", c.toString());
    }
}
