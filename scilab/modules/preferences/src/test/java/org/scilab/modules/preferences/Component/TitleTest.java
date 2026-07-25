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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.swing.border.TitledBorder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Hermetic unit tests for the {@link Title} preference component (a
 * {@link javax.swing.JPanel} carrying a {@link javax.swing.border.TitledBorder}).
 * The constructor sets the titled border, an optional opaque background, and an
 * optional fixed dimension. All pure Swing state; constructed headless.
 */
public class TitleTest {

    private static Element el(String name, String... kv) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element e = doc.createElement(name);
        for (int i = 0; i + 1 < kv.length; i += 2) {
            e.setAttribute(kv[i], kv[i + 1]);
        }
        return e;
    }

    @Test
    public void constructorInstallsATitledBorderWithTheText() throws Exception {
        Title c = new Title(el("Title", "text", "Section"));
        assertInstanceOf(TitledBorder.class, c.getBorder());
        assertEquals("Section", ((TitledBorder) c.getBorder()).getTitle());
    }

    @Test
    public void actuatorsAreEmpty() throws Exception {
        assertEquals(0, new Title(el("Title", "text", "x")).actuators().length);
    }

    @Test
    public void toStringIsStable() throws Exception {
        assertEquals("Title", new Title(el("Title", "text", "x")).toString());
    }

    @Test
    public void backgroundAttributeMakesThePanelOpaque() throws Exception {
        Title c = new Title(el("Title", "text", "x", "background", "#00ff00"));
        assertTrue(c.isOpaque(), "a background attribute forces opacity");
        assertEquals(new java.awt.Color(0, 255, 0), c.getBackground());
    }

    @Test
    public void widthAndHeightAttributesSetThePreferredSize() throws Exception {
        Title c = new Title(el("Title", "text", "x", "width", "120", "height", "40"));
        assertEquals(120, c.getPreferredSize().width);
        assertEquals(40, c.getPreferredSize().height);
    }

    @Test
    public void refreshIsANoOp() throws Exception {
        Title c = new Title(el("Title", "text", "Section"));
        c.refresh(el("Title", "text", "Ignored"));
        assertEquals("Section", ((TitledBorder) c.getBorder()).getTitle(),
                     "refresh does nothing; the original title stands");
    }
}
