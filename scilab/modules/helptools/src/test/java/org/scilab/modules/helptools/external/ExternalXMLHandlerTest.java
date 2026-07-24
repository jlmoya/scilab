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
 * Hermetic unit tests for the concrete, self-contained behaviour of the abstract
 * {@link ExternalXMLHandler}: the Scilab namespace constant, the {@code compt}
 * counter reset, the tag-reconstruction helper, and the {@code localized}
 * attribute decoder. A tiny {@code Probe} subclass supplies no-op implementations
 * of the three abstract hooks and bridges the {@code protected static}
 * {@code getLocalized}. Real {@link AttributesImpl} instances drive the parsing.
 */
public class ExternalXMLHandlerTest {

    private static final class Probe extends ExternalXMLHandler {
        public StringBuilder startExternalXML(String localName, Attributes attributes, Locator locator) {
            return null;
        }
        public String endExternalXML(String localName) {
            return null;
        }
        public String getURI() {
            return "urn:test";
        }
        // expose the protected counter / static decoder for assertions
        int compt() {
            return compt;
        }
        void bump() {
            compt++;
        }
        static Boolean decode(String uri, Attributes a) {
            return getLocalized(uri, a);
        }
    }

    private static Attributes attr(String uri, String localName, String qName, String value) {
        AttributesImpl a = new AttributesImpl();
        a.addAttribute(uri, localName, qName, "CDATA", value);
        return a;
    }

    // ---- constants + counter -------------------------------------------

    @Test
    public void scilabUriIsTheCanonicalNamespace() {
        assertEquals("http://www.scilab.org", new Probe().getScilabURI());
    }

    @Test
    public void resetComptRestoresTheCounterToOne() {
        Probe p = new Probe();
        assertEquals(1, p.compt(), "counter starts at 1");
        p.bump();
        p.bump();
        assertEquals(3, p.compt());
        p.resetCompt();
        assertEquals(1, p.compt());
    }

    @Test
    public void converterAccessorIsNullUntilSet() {
        Probe p = new Probe();
        assertNull(p.getConverter());
        p.setConverter(null);
        assertNull(p.getConverter());
    }

    // ---- recreateTag ----------------------------------------------------

    @Test
    public void recreateTagEmitsAnOpeningTagWithAttributes() {
        StringBuilder buf = new StringBuilder();
        new Probe().recreateTag(buf, "svg", attr("", "width", "width", "10"));
        assertEquals("<svg width='10'>", buf.toString());
    }

    @Test
    public void recreateTagWithNullAttributesEmitsAClosingTag() {
        StringBuilder buf = new StringBuilder();
        new Probe().recreateTag(buf, "svg", null);
        assertEquals("</svg>", buf.toString());
    }

    @Test
    public void recreateTagSkipsAttributesWithEmptyLocalName() {
        // Namespace declarations arrive with an empty localName and must be dropped.
        AttributesImpl a = new AttributesImpl();
        a.addAttribute("", "", "xmlns", "CDATA", "http://ns");
        a.addAttribute("", "id", "id", "CDATA", "x");
        StringBuilder buf = new StringBuilder();
        new Probe().recreateTag(buf, "g", a);
        assertEquals("<g id='x'>", buf.toString());
    }

    // ---- getLocalized ---------------------------------------------------

    @Test
    public void getLocalizedReturnsNullWhenTheAttributeIsAbsent() {
        assertNull(Probe.decode(null, new AttributesImpl()));
    }

    @Test
    public void getLocalizedDecodesTrueAndFalse() {
        assertEquals(Boolean.TRUE, Probe.decode(null, attr("", "localized", "localized", "true")));
        assertEquals(Boolean.FALSE, Probe.decode(null, attr("", "localized", "localized", "false")));
    }

    @Test
    public void getLocalizedTreatsAnyOtherValueAsFalse() {
        assertEquals(Boolean.FALSE, Probe.decode(null, attr("", "localized", "localized", "maybe")));
    }

    @Test
    public void getLocalizedReadsTheNamespacedAttributeWhenUriGiven() {
        String uri = "http://www.scilab.org";
        assertEquals(Boolean.TRUE,
                     Probe.decode(uri, attr(uri, "localized", "scilab:localized", "true")));
    }
}
