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

package org.scilab.modules.helptools.external;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Hermetic unit tests for {@link HTMLScilabHandler}'s identity contract and its
 * pure {@code startExternalXML} branching.
 *
 * <p>Unlike the SVG/MathML handlers, the Scilab handler registers under the very
 * same namespace it inherits as {@code getScilabURI()} — that coincidence is
 * pinned here so a future change to either side cannot silently diverge. Also
 * unlike them, its {@code <image>} root returns the buffer <em>without</em>
 * reconstructing a tag (the Scilab code is the raw text child), while non-root
 * elements are reconstructed into the buffer — both branches are covered with a
 * stub {@link Locator}. The image-generation path needs a live converter and is
 * out of scope.
 */
public class HTMLScilabHandlerTest {

    private static Attributes attr(String uri, String local, String qName, String value) {
        AttributesImpl a = new AttributesImpl();
        a.addAttribute(uri, local, qName, "CDATA", value);
        return a;
    }

    private static Locator locAt(final int line) {
        return new Locator() {
            public int getLineNumber() {
                return line;
            }
            public int getColumnNumber() {
                return -1;
            }
            public String getPublicId() {
                return null;
            }
            public String getSystemId() {
                return null;
            }
        };
    }

    @Test
    public void uriIsTheScilabNamespace() {
        assertEquals("http://www.scilab.org", new HTMLScilabHandler("out", "base").getURI());
    }

    @Test
    public void uriMatchesTheInheritedScilabNamespaceConstant() {
        HTMLScilabHandler h = new HTMLScilabHandler("out", "base");
        assertEquals(h.getScilabURI(), h.getURI());
    }

    @Test
    public void constructorDoesNotThrowForTypicalPaths() {
        assertDoesNotThrow(() -> new HTMLScilabHandler("/tmp/out", "en_US"));
    }

    // ---- startExternalXML ----------------------------------------------

    @Test
    public void startOnImageRootReturnsAnEmptyBufferWithoutRebuildingTheTag() {
        HTMLScilabHandler h = new HTMLScilabHandler("out", "en_US");
        StringBuilder sb = h.startExternalXML("image", attr("", "localized", "localized", "true"), locAt(5));
        assertNotNull(sb, "the <image> root must return the accumulation buffer");
        // The image handler does NOT recreate the <image> tag: the buffer stays empty,
        // ready to receive the raw Scilab source as character data.
        assertEquals("", sb.toString());
    }

    @Test
    public void startOnNonRootRebuildsTheTagIntoTheBufferAndReturnsNull() {
        HTMLScilabHandler h = new HTMLScilabHandler("out", "en_US");
        assertNull(h.startExternalXML("body", attr("", "id", "id", "x"), locAt(1)));
        // The reconstructed tag is retained and revealed when the <image> root is seen.
        StringBuilder sb = h.startExternalXML("image", new AttributesImpl(), locAt(2));
        assertNotNull(sb);
        assertTrue(sb.toString().contains("<body id='x'>"), "nested tag must be buffered: " + sb);
    }
}
