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

import java.awt.Dimension;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Hermetic unit tests for the {@link Panel} preference component (a
 * {@link javax.swing.JPanel}). The interesting pure logic is the
 * {@code fixed-width}/{@code fixed-height} constraint switch, observable through
 * {@code getMaximumSize()} / {@code getMinimumSize()}: a "fixed" axis is pinned to
 * the preferred size (0 for an empty panel), a non-fixed axis passes the
 * superclass value through. No native code or display is required.
 */
public class PanelTest {

    private static Element el(String name, String... kv) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element e = doc.createElement(name);
        for (int i = 0; i + 1 < kv.length; i += 2) {
            e.setAttribute(kv[i], kv[i + 1]);
        }
        return e;
    }

    @Test
    public void defaultsPinHeightButNotWidth() throws Exception {
        // Defaults: fixedHeight == true, fixedWidth == false.
        Panel c = new Panel(el("Panel"));
        Dimension max = c.getMaximumSize();
        assertEquals(Integer.MAX_VALUE, max.width, "non-fixed width passes the superclass max through");
        assertEquals(0, max.height, "fixed height is pinned to the (empty) preferred height");
        assertEquals(new Dimension(0, 0), c.getMinimumSize());
    }

    @Test
    public void fixedWidthAttributeFlipsTheConstraint() throws Exception {
        Panel c = new Panel(el("Panel", "fixed-width", "true", "fixed-height", "false"));
        Dimension max = c.getMaximumSize();
        assertEquals(0, max.width, "fixed width is now pinned to the (empty) preferred width");
        assertEquals(Integer.MAX_VALUE, max.height, "non-fixed height passes the superclass max through");
    }

    @Test
    public void actuatorsAreEmpty() throws Exception {
        assertArrayEquals(new String[] {}, new Panel(el("Panel")).actuators());
    }

    @Test
    public void refreshIsANoOpAndToStringIsTheLabel() throws Exception {
        Panel c = new Panel(el("Panel"));
        c.refresh(el("Panel"));
        assertEquals("Panel", c.toString());
    }
}
