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

package org.scilab.modules.preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Hermetic unit tests for {@link XSentinel}.
 *
 * <p>Only the pure, GUI-free surface is exercised: the static {@code signature}
 * reducer (the string whose equality implies "reachable through actuators") and,
 * via a sentinel built with a {@code null} Swing component (which forces an empty
 * actuator set), the {@code checks} / {@code setPeer} / {@code reduced} caching
 * behaviour. The Swing/AWT event callbacks are out of scope for a hermetic test.
 *
 * <p>DOM nodes are built with the JDK's JAXP {@link DocumentBuilderFactory}, so no
 * Scilab runtime is required.
 */
public class XSentinelTest {

    private static Document newDoc() throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
    }

    /** Build a detached element {@code <name a1='v1' a2='v2' .../>}. */
    private static Element el(Document doc, String name, String... kv) {
        Element e = doc.createElement(name);
        for (int i = 0; i + 1 < kv.length; i += 2) {
            e.setAttribute(kv[i], kv[i + 1]);
        }
        return e;
    }

    private static final String[] NONE = new String[0];

    // ----- signature() -------------------------------------------------------

    @Test
    public void signatureOfNodeWithoutAttributesIsJustTheNodeName() throws Exception {
        Document doc = newDoc();
        assertEquals("Foo", XSentinel.signature(el(doc, "Foo"), NONE));
    }

    @Test
    public void signatureIncludesNonActuatorAttributes() throws Exception {
        Document doc = newDoc();
        Element e = el(doc, "Foo", "color", "ff0000");
        assertEquals("Foo color='ff0000'", XSentinel.signature(e, NONE));
    }

    @Test
    public void actuatorAttributesAreStrippedFromSignature() throws Exception {
        Document doc = newDoc();
        Element e = el(doc, "Foo", "color", "ff0000");
        // "color" is now an actuator => it is removed (it can change without
        // rebuilding the node), so the signature collapses back to the name.
        assertEquals("Foo", XSentinel.signature(e, new String[] {"color"}));
    }

    @Test
    public void layoutAttributesAreAlwaysStripped() throws Exception {
        Document doc = newDoc();
        // gridx / anchor / insets / border-side are in the implicit LAYOUT set.
        Element e = el(doc, "Foo", "gridx", "2", "anchor", "17", "insets", "1,1,1,1", "border-side", "North");
        assertEquals("Foo", XSentinel.signature(e, NONE));
    }

    @Test
    public void attributeValueWhitespaceIsCollapsed() throws Exception {
        Document doc = newDoc();
        Element e = el(doc, "Foo", "text", "a  b\t\nc");
        assertEquals("Foo text='a b c'", XSentinel.signature(e, NONE));
    }

    @Test
    public void signatureMixesKeptAttributesWhileDroppingActuatorsAndLayout() throws Exception {
        Document doc = newDoc();
        Element e = el(doc, "Bar", "a", "1", "gridy", "2", "b", "3");
        String sig = XSentinel.signature(e, new String[] {"b"});
        assertTrue(sig.startsWith("Bar"), sig);
        assertTrue(sig.contains("a='1'"), "non-actuator, non-layout attr kept: " + sig);
        assertFalse(sig.contains("b='3'"), "actuator 'b' dropped: " + sig);
        assertFalse(sig.contains("gridy"), "layout 'gridy' dropped: " + sig);
    }

    /**
     * Defect characterization: the implicit LAYOUT set contains the typo
     * {@code "ipday"} instead of {@code "ipady"}. As a consequence {@code ipadx}
     * is stripped (as intended) but a real {@code ipady} constraint leaks into
     * the signature, so a node differing only in {@code ipady} is wrongly judged
     * un-reachable-through-actuators. This test documents the current behaviour.
     */
    @Test
    public void layoutTypo_ipadyLeaksWhileIpadxIsStripped() throws Exception {
        Document doc = newDoc();
        assertEquals("Foo", XSentinel.signature(el(doc, "Foo", "ipadx", "5"), NONE));

        String sig = XSentinel.signature(el(doc, "Foo", "ipady", "5"), NONE);
        assertEquals("Foo ipady='5'", sig,
                     "the 'ipday' typo means 'ipady' is not recognised as a layout attribute");
    }

    // ----- checks() / setPeer() / reduced (null-component sentinel) ----------

    @Test
    public void reducedIsLazilyNullUntilFirstChecks() throws Exception {
        Document doc = newDoc();
        XSentinel s = new XSentinel((Component) null, el(doc, "Foo", "x", "1"));
        assertNull(s.reduced, "reduced signature is computed lazily on first checks()");
    }

    @Test
    public void checksIsTrueForEqualSignaturesAndCachesReduced() throws Exception {
        Document doc = newDoc();
        Element peer = el(doc, "Foo", "x", "1");
        XSentinel s = new XSentinel((Component) null, peer);

        assertTrue(s.checks(el(doc, "Foo", "x", "1")));
        assertEquals("Foo x='1'", s.reduced, "reduced now caches the peer signature");
    }

    @Test
    public void checksIsFalseWhenAKeptAttributeDiffers() throws Exception {
        Document doc = newDoc();
        XSentinel s = new XSentinel((Component) null, el(doc, "Foo", "x", "1"));
        assertFalse(s.checks(el(doc, "Foo", "x", "2")));
    }

    @Test
    public void checksIgnoresLayoutOnlyDifferences() throws Exception {
        Document doc = newDoc();
        // Both reduce to bare "Foo" because gridx is a layout attribute.
        XSentinel s = new XSentinel((Component) null, el(doc, "Foo", "gridx", "1"));
        assertTrue(s.checks(el(doc, "Foo", "gridx", "9")),
                   "a difference confined to layout constraints is reachable through actuators");
    }

    @Test
    public void checksIsFalseForADifferentNodeName() throws Exception {
        Document doc = newDoc();
        XSentinel s = new XSentinel((Component) null, el(doc, "Foo"));
        assertFalse(s.checks(el(doc, "Bar")));
    }

    /**
     * Defect/behaviour characterization: {@code reduced} is cached on the first
     * {@code checks()} call and {@code setPeer()} does <em>not</em> refresh it, so
     * subsequent comparisons keep using the original peer's signature.
     */
    @Test
    public void setPeerDoesNotRefreshTheCachedReducedSignature() throws Exception {
        Document doc = newDoc();
        Element first = el(doc, "Foo", "x", "1");
        XSentinel s = new XSentinel((Component) null, first);

        assertTrue(s.checks(first));            // caches reduced = "Foo x='1'"
        s.setPeer(el(doc, "Foo", "x", "2"));    // peer changes, reduced stays cached

        assertEquals("Foo x='1'", s.reduced);
        // Comparing against the *new* peer still fails: the stale cached signature wins.
        assertFalse(s.checks(el(doc, "Foo", "x", "2")));
    }
}
