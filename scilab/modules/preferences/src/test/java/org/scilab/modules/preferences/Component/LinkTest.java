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

import java.awt.Color;

import javax.swing.JPanel;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Hermetic unit tests for the {@link Link} preference component, a {@link Label}
 * subclass that renders its text as an underlined hyperlink. The text-wrapping and
 * enabled/disabled colouring are pure Swing state; no native code or display is
 * required. The disabled branch reads {@code getParent().getBackground()}, so that
 * one case supplies a real parent with a known background.
 */
public class LinkTest {

    private static Element el(String name, String... kv) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element e = doc.createElement(name);
        for (int i = 0; i + 1 < kv.length; i += 2) {
            e.setAttribute(kv[i], kv[i + 1]);
        }
        return e;
    }

    @Test
    public void textIsWrappedInUnderlinedHtml() throws Exception {
        Link c = new Link(el("Link", "text", "click"));
        assertEquals("<HTML><U>click</U></HTML>", c.text());
    }

    @Test
    public void enabledLinkIsBlue() throws Exception {
        Link c = new Link(el("Link", "text", "click"));
        assertEquals(Color.BLUE, c.getForeground());
    }

    @Test
    public void disabledLinkTakesTheDarkenedParentBackground() throws Exception {
        Link c = new Link(el("Link", "text", "click", "enable", "false"));
        JPanel parent = new JPanel();
        parent.setBackground(new Color(100, 100, 100));
        parent.add(c);
        // Re-run refresh now that the link has a parent with a known background.
        c.refresh(el("Link", "text", "click", "enable", "false"));
        assertEquals(new Color(100, 100, 100).darker(), c.getForeground());
    }

    @Test
    public void inheritsLabelActuators() throws Exception {
        // Link does not override actuators(), so it exposes Label's full set.
        assertEquals("[text, foreground, background, tooltip, font-family, font-face, font-size, enable]",
                     java.util.Arrays.toString(new Link(el("Link", "text", "x")).actuators()));
    }
}
