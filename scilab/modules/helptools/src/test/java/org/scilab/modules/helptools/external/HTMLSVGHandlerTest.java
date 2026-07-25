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
 * Hermetic unit tests for {@link HTMLSVGHandler}'s identity contract and its
 * {@code startExternalXML} tag-accumulation.
 *
 * <p>{@link ExternalXMLHandler#getURI()} is the key a {@code DocbookTagConverter}
 * registers each external handler under, so it must be exactly the SVG namespace.
 * The constructor only concatenates path strings (no I/O), so it is safe to build
 * here. {@code startExternalXML} is also pure — it only touches an internal buffer,
 * the inherited {@code getLocalized} decoder and the {@link Locator} — so it is
 * driven here with a stub locator; the {@code endExternalXML} image-generation path
 * needs a live converter and is out of scope.
 */
public class HTMLSVGHandlerTest {

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
    public void uriIsTheSvgNamespace() {
        assertEquals("http://www.w3.org/2000/svg", new HTMLSVGHandler("out", "base").getURI());
    }

    @Test
    public void inheritsTheScilabNamespaceConstant() {
        assertEquals("http://www.scilab.org", new HTMLSVGHandler("out", "base").getScilabURI());
    }

    @Test
    public void constructorDoesNotThrowForTypicalPaths() {
        assertDoesNotThrow(() -> new HTMLSVGHandler("/tmp/out", "fr_FR"));
    }

    // ---- startExternalXML ----------------------------------------------

    @Test
    public void startOnSvgRootReturnsTheBufferWithTheOpeningTag() {
        HTMLSVGHandler h = new HTMLSVGHandler("out", "fr_FR");
        StringBuilder sb = h.startExternalXML("svg", attr("", "width", "width", "100"), locAt(7));
        assertNotNull(sb, "the <svg> root must return the accumulation buffer");
        String s = sb.toString();
        assertTrue(s.contains("<svg"), "buffer should hold the opening tag: " + s);
        assertTrue(s.contains("width='100'"), "attributes should be reconstructed: " + s);
    }

    @Test
    public void startOnNonRootReturnsNullButBuffersTheTag() {
        HTMLSVGHandler h = new HTMLSVGHandler("out", "fr_FR");
        // A nested element returns null (not a self-contained fragment)...
        assertNull(h.startExternalXML("g", attr("", "id", "id", "a"), locAt(1)));
        // ...yet its opening tag is retained, and surfaces when the root is seen.
        StringBuilder sb = h.startExternalXML("svg", new AttributesImpl(), locAt(2));
        assertNotNull(sb);
        String s = sb.toString();
        assertTrue(s.contains("<g id='a'>"), "nested tag must be buffered: " + s);
        assertTrue(s.contains("<svg>"), "root tag must follow: " + s);
    }

    @Test
    public void startOnSvgRootWithLocalizedAttributeIsAccepted() {
        // Exercises the getLocalized(scilabURI, attrs) branch on the root element.
        HTMLSVGHandler h = new HTMLSVGHandler("out", "fr_FR");
        StringBuilder sb = h.startExternalXML(
            "svg", attr("http://www.scilab.org", "localized", "scilab:localized", "true"), locAt(3));
        assertNotNull(sb);
        assertTrue(sb.toString().contains("<svg"));
    }
}
