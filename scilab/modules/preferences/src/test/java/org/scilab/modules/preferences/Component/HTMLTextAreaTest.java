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
import static org.junit.jupiter.api.Assertions.assertThrows;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Hermetic unit tests for the {@link HTMLTextArea} preference component (a
 * {@link javax.swing.JTextPane}).
 *
 * <p>Only the paths that stay off native code are exercised. When the peer has an
 * actual {@code <html>} child, {@code dumpNode} calls
 * {@code ScilabXMLUtilities.removeEmptyLines}, whose class initializer reaches
 * {@code Messages.gettext} over JNI — that path is out of scope. The empty-peer
 * path (no children) returns the text content directly and is fully hermetic, and
 * the malformed-child path exposes a real defect.
 */
public class HTMLTextAreaTest {

    private static Document doc() throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
    }

    @Test
    public void emptyPeerConstructsWithoutTouchingNativeCode() throws Exception {
        HTMLTextArea c = new HTMLTextArea(doc().createElement("HTMLTextArea"));
        assertEquals("TextArea", c.toString());
        assertArrayEquals(new String[] {}, c.actuators());
    }

    @Test
    public void refreshOnAnEmptyPeerDoesNotThrow() throws Exception {
        Document d = doc();
        HTMLTextArea c = new HTMLTextArea(d.createElement("HTMLTextArea"));
        c.refresh(d.createElement("HTMLTextArea"));
        assertEquals("TextArea", c.toString());
    }

    /**
     * Defect characterization: {@code dumpNode} calls
     * {@code child.getLocalName().equalsIgnoreCase("html")} on every child, but
     * nodes created with the DOM Level 1 {@code createElement} (and text nodes)
     * have a {@code null} local name, so a non-namespaced child makes the
     * constructor throw a {@link NullPointerException}.
     */
    @Test
    public void nonNamespacedChildTriggersNullLocalNameNpe() throws Exception {
        Document d = doc();
        Element peer = d.createElement("HTMLTextArea");
        peer.appendChild(d.createElement("child")); // getLocalName() == null
        assertThrows(NullPointerException.class, () -> new HTMLTextArea(peer));
    }
}
