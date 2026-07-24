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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Component;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Hermetic unit tests for the {@link VBox} preference component (a
 * {@link Panel} with a vertical {@code BoxLayout}). The background refresh logic
 * and the alignment-forcing {@code add} override are pure Swing state; no native
 * code or display is required.
 */
public class VBoxTest {

    private static Element el(String name, String... kv) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element e = doc.createElement(name);
        for (int i = 0; i + 1 < kv.length; i += 2) {
            e.setAttribute(kv[i], kv[i + 1]);
        }
        return e;
    }

    @Test
    public void layoutIsAVerticalBoxLayout() throws Exception {
        VBox c = new VBox(el("VBox"));
        assertTrue(c.getLayout() instanceof BoxLayout);
    }

    @Test
    public void absentBackgroundMakesItTransparent() throws Exception {
        // The constructor calls refresh(); with no 'background' attribute (NAV) the
        // panel is made non-opaque with a null background.
        VBox c = new VBox(el("VBox"));
        assertFalse(c.isOpaque());
        assertNull(c.getBackground());
    }

    @Test
    public void backgroundColourIsAppliedAndMakesItOpaque() throws Exception {
        VBox c = new VBox(el("VBox", "background", "#112233"));
        assertTrue(c.isOpaque());
        assertEquals(new Color(0x11, 0x22, 0x33), c.getBackground());
    }

    @Test
    public void addForcesLeftTopAlignmentOnChildren() throws Exception {
        VBox c = new VBox(el("VBox"));
        JLabel child = new JLabel("x");
        c.add(child, null);
        assertEquals(Component.LEFT_ALIGNMENT, child.getAlignmentX(), 0.0f);
        assertEquals(Component.TOP_ALIGNMENT, child.getAlignmentY(), 0.0f);
        assertEquals(1, c.getComponentCount());
    }

    @Test
    public void actuatorsAreEmptyAndToStringIsTheLabel() throws Exception {
        VBox c = new VBox(el("VBox"));
        assertArrayEquals(new String[] {}, c.actuators());
        assertEquals("VBox", c.toString());
    }
}
