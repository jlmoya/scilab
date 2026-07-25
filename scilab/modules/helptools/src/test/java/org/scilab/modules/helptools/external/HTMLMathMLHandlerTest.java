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
 * Hermetic unit tests for {@link HTMLMathMLHandler}'s identity contract — the
 * MathML namespace it registers under, and the inherited Scilab namespace — plus
 * its pure {@code startExternalXML} tag accumulation (driven with a stub
 * {@link Locator}). The rendering path (which needs a live converter) is out of
 * scope.
 */
public class HTMLMathMLHandlerTest {

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
    public void uriIsTheMathmlNamespace() {
        assertEquals("http://www.w3.org/1998/Math/MathML",
                     new HTMLMathMLHandler("out", "base").getURI());
    }

    @Test
    public void inheritsTheScilabNamespaceConstant() {
        assertEquals("http://www.scilab.org", new HTMLMathMLHandler("out", "base").getScilabURI());
    }

    @Test
    public void constructorDoesNotThrowForTypicalPaths() {
        assertDoesNotThrow(() -> new HTMLMathMLHandler("/tmp/out", "ja_JP"));
    }

    // ---- startExternalXML ----------------------------------------------

    @Test
    public void startOnMathRootReturnsTheBufferWithTheOpeningTag() {
        HTMLMathMLHandler h = new HTMLMathMLHandler("out", "ja_JP");
        StringBuilder sb = h.startExternalXML("math", attr("", "display", "display", "block"), locAt(4));
        assertNotNull(sb, "the <math> root must return the accumulation buffer");
        String s = sb.toString();
        assertTrue(s.contains("<math"), "buffer should hold the opening tag: " + s);
        assertTrue(s.contains("display='block'"), "attributes should be reconstructed: " + s);
    }

    @Test
    public void startOnNonRootReturnsNullButBuffersTheTag() {
        HTMLMathMLHandler h = new HTMLMathMLHandler("out", "ja_JP");
        assertNull(h.startExternalXML("mrow", new AttributesImpl(), locAt(1)));
        StringBuilder sb = h.startExternalXML("math", new AttributesImpl(), locAt(2));
        assertNotNull(sb);
        String s = sb.toString();
        assertTrue(s.contains("<mrow>"), "nested tag must be buffered: " + s);
        assertTrue(s.contains("<math>"), "root tag must follow: " + s);
    }
}
