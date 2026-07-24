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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.event.MouseEvent;
import java.util.concurrent.atomic.AtomicInteger;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Hermetic unit tests for the {@link File} preference component (a
 * {@link javax.swing.JTextField} + {@code MouseListener}). The href/desc/mask
 * accessors, the single-listener registration and the enabled-guarded mouse-click
 * dispatch are all pure Swing/event state; {@code choose()} (which opens the native
 * file dialog) is deliberately not exercised. No native code or display is required.
 */
public class FileTest {

    private static Element el(String name, String... kv) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element e = doc.createElement(name);
        for (int i = 0; i + 1 < kv.length; i += 2) {
            e.setAttribute(kv[i], kv[i + 1]);
        }
        return e;
    }

    private static MouseEvent click(File f) {
        return new MouseEvent(f, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 1, 1, 1, false);
    }

    @Test
    public void constructorReadsHrefDescAndMask() throws Exception {
        File c = new File(el("File", "href", "/x/y.sce", "desc", "Scilab", "mask", "*.sce"));
        assertEquals("/x/y.sce", c.href());
        assertEquals("Scilab", c.desc());
        assertEquals("*.sce", c.mask());
    }

    @Test
    public void hrefActuatorAndSensorRoundTrip() throws Exception {
        File c = new File(el("File"));
        c.href("/a/b");
        assertEquals("/a/b", c.href());
        assertEquals("/a/b", c.getText());
    }

    @Test
    public void descAndMaskActuatorsRoundTrip() throws Exception {
        File c = new File(el("File"));
        c.desc("Documents");
        c.mask("*.txt");
        assertEquals("Documents", c.desc());
        assertEquals("*.txt", c.mask());
    }

    @Test
    public void enableAttributeControlsEnabledState() throws Exception {
        assertFalse(new File(el("File", "enable", "false")).isEnabled());
        assertTrue(new File(el("File")).isEnabled());
    }

    @Test
    public void mouseClickDispatchesToTheRegisteredListenerWhenEnabled() throws Exception {
        File c = new File(el("File", "href", "/x"));
        AtomicInteger fired = new AtomicInteger();
        c.addActionListener(e -> fired.incrementAndGet());
        c.mouseClicked(click(c));
        assertEquals(1, fired.get());
    }

    @Test
    public void mouseClickIsSuppressedWhenDisabled() throws Exception {
        File c = new File(el("File", "href", "/x", "enable", "false"));
        AtomicInteger fired = new AtomicInteger();
        c.addActionListener(e -> fired.incrementAndGet());
        c.mouseClicked(click(c));
        assertEquals(0, fired.get(), "a disabled File swallows the click");
    }

    @Test
    public void unusedMouseCallbacksAreNoOps() throws Exception {
        File c = new File(el("File"));
        // These must not throw and must not require a listener.
        c.mouseEntered(click(c));
        c.mouseExited(click(c));
        c.mousePressed(click(c));
        c.mouseReleased(click(c));
    }

    /**
     * Defect characterization: {@code toString()} labels the {@code mask} clause
     * with {@code href='...'} (copy/paste slip) instead of {@code mask='...'}.
     */
    @Test
    public void toStringMislabelsTheMaskClauseAsHref() throws Exception {
        File c = new File(el("File", "href", "/x/y.sce", "desc", "Scilab", "mask", "*.sce"));
        assertEquals("File href='/x/y.sce' desc='Scilab' href='*.sce'", c.toString());
    }

    @Test
    public void actuatorsAreTheDocumentedSet() throws Exception {
        assertArrayEquals(new String[] {"enable", "href", "desc", "mask"}, new File(el("File")).actuators());
    }
}
