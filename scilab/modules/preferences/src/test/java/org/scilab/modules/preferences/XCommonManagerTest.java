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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.awt.Color;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Hermetic unit tests for the pure static helpers declared on
 * {@link XCommonManager}: colour {@code <-> } string conversion, the
 * {@code NAV}-sentinel attribute accessors, the typed ({@code int}/{@code double}/
 * {@code boolean}) accessors, and {@code getNodePath}.
 *
 * <p>These methods are declared on {@code XCommonManager} itself, so referencing
 * them only triggers that class's initializer — which builds JAXP factories and
 * reads the {@code SCI} env var but touches no native code, no display and no
 * running Scilab. (The heavier {@code XConfigManager} subclass, whose initializer
 * calls {@code Messages.gettext} over JNI, is intentionally never referenced.)
 * Attribute values are kept plain so the {@code _(...)} localization branch of
 * {@code getAttribute} — another JNI call — is never taken. Nodes come from JAXP.
 */
public class XCommonManagerTest {

    private static Document newDoc() throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
    }

    private static Element el(Document doc, String name, String... kv) {
        Element e = doc.createElement(name);
        for (int i = 0; i + 1 < kv.length; i += 2) {
            e.setAttribute(kv[i], kv[i + 1]);
        }
        return e;
    }

    // ----- getColor(Color) ---------------------------------------------------

    @Test
    public void colorToHexStringForPrimaries() {
        assertEquals("#ff0000", XCommonManager.getColor(Color.RED));
        assertEquals("#00ff00", XCommonManager.getColor(Color.GREEN));
        assertEquals("#0000ff", XCommonManager.getColor(Color.BLUE));
        assertEquals("#000000", XCommonManager.getColor(Color.BLACK));
        assertEquals("#ffffff", XCommonManager.getColor(Color.WHITE));
    }

    @Test
    public void nullColorMapsToBlack() {
        assertEquals("#000000", XCommonManager.getColor((Color) null));
    }

    /**
     * Defect characterization: {@code getColor(Color)} does
     * {@code Integer.toHexString(rgb).substring(2)}, assuming the hex form is
     * always 8 chars (opaque colours have alpha {@code 0xFF}). A fully
     * transparent colour has {@code getRGB() == 0}, whose hex form is {@code "0"},
     * and {@code substring(2)} then overflows. This documents the current throw.
     */
    @Test
    public void fullyTransparentColorOverflowsSubstring() {
        assertThrows(IndexOutOfBoundsException.class,
                     () -> XCommonManager.getColor(new Color(0, 0, 0, 0)));
    }

    // ----- getColor(String) --------------------------------------------------

    @Test
    public void hexStringToColor() {
        assertEquals(new Color(255, 0, 0), XCommonManager.getColor("#ff0000"));
        assertEquals(Color.BLACK, XCommonManager.getColor("#000000"));
        assertEquals(new Color(0x12, 0x34, 0x56), XCommonManager.getColor("#123456"));
    }

    @Test
    public void colorConversionRoundTrips() {
        assertEquals("#123456", XCommonManager.getColor(XCommonManager.getColor("#123456")));
        assertEquals("#abcdef", XCommonManager.getColor(XCommonManager.getColor("#abcdef")));
    }

    @Test
    public void invalidColorStringThrows() {
        assertThrows(NumberFormatException.class, () -> XCommonManager.getColor("not-a-color"));
    }

    @Test
    public void nullColorStringThrows() {
        assertThrows(NullPointerException.class, () -> XCommonManager.getColor((String) null));
    }

    // ----- getAttribute ------------------------------------------------------

    @Test
    public void getAttributeReturnsTheStoredValue() throws Exception {
        Document doc = newDoc();
        Element e = el(doc, "Foo", "color", "ff0000");
        assertEquals("ff0000", XCommonManager.getAttribute(e, "color"));
    }

    @Test
    public void getAttributeReturnsTheNavSentinelWhenAbsent() throws Exception {
        Document doc = newDoc();
        Element e = el(doc, "Foo", "color", "ff0000");
        // Missing attribute => the shared NAV sentinel instance (compared by == in callers).
        assertSame(XCommonManager.NAV, XCommonManager.getAttribute(e, "missing"));
    }

    @Test
    public void getAttributeOnNodeWithoutAttributesReturnsNav() throws Exception {
        Document doc = newDoc();
        // A text node has no attribute map at all (getAttributes() == null).
        assertSame(XCommonManager.NAV, XCommonManager.getAttribute(doc.createTextNode("x"), "any"));
    }

    @Test
    public void getAttributeWithDefaultFallsBackWhenAbsent() throws Exception {
        Document doc = newDoc();
        Element e = el(doc, "Foo", "color", "ff0000");
        assertEquals("ff0000", XCommonManager.getAttribute(e, "color", "def"));
        assertEquals("def", XCommonManager.getAttribute(e, "missing", "def"));
    }

    // ----- getInt ------------------------------------------------------------

    @Test
    public void getIntParsesOrDefaults() throws Exception {
        Document doc = newDoc();
        Element e = el(doc, "Foo", "n", "42", "empty", "");
        assertEquals(42, XCommonManager.getInt(e, "n", 7));
        assertEquals(7, XCommonManager.getInt(e, "missing", 7), "absent => default");
        assertEquals(7, XCommonManager.getInt(e, "empty", 7), "empty string => default");
    }

    /**
     * Defect characterization: a present-but-unparseable value does <em>not</em>
     * fall back to the supplied default; the {@code NumberFormatException} handler
     * returns a hard-coded {@code 0} instead.
     */
    @Test
    public void getIntReturnsZeroNotDefaultOnGarbage() throws Exception {
        Document doc = newDoc();
        Element e = el(doc, "Foo", "n", "abc");
        assertEquals(0, XCommonManager.getInt(e, "n", 7));
    }

    // ----- getDouble ---------------------------------------------------------

    @Test
    public void getDoubleParsesOrDefaults() throws Exception {
        Document doc = newDoc();
        Element e = el(doc, "Foo", "d", "3.5", "empty", "");
        assertEquals(3.5, XCommonManager.getDouble(e, "d", 1.25), 0.0);
        assertEquals(1.25, XCommonManager.getDouble(e, "missing", 1.25), 0.0);
        assertEquals(1.25, XCommonManager.getDouble(e, "empty", 1.25), 0.0);
    }

    @Test
    public void getDoubleReturnsZeroNotDefaultOnGarbage() throws Exception {
        Document doc = newDoc();
        Element e = el(doc, "Foo", "d", "xyz");
        assertEquals(0.0, XCommonManager.getDouble(e, "d", 1.25), 0.0);
    }

    // ----- getBoolean --------------------------------------------------------

    @Test
    public void getBooleanIsCaseInsensitiveTrueElseFalse() throws Exception {
        Document doc = newDoc();
        Element e = el(doc, "Foo", "t", "true", "T", "TRUE", "f", "false", "other", "yes", "empty", "");
        assertEquals(true, XCommonManager.getBoolean(e, "t", false));
        assertEquals(true, XCommonManager.getBoolean(e, "T", false));
        assertEquals(false, XCommonManager.getBoolean(e, "f", true));
        assertEquals(false, XCommonManager.getBoolean(e, "other", true), "anything not \"true\" is false");
        assertEquals(true, XCommonManager.getBoolean(e, "missing", true), "absent => default");
        assertEquals(false, XCommonManager.getBoolean(e, "empty", false), "empty => default");
    }

    // ----- constants ---------------------------------------------------------

    @Test
    public void constantsHaveTheirDocumentedValues() {
        assertEquals("\"not an value'", XCommonManager.NAV);
        assertEquals("    ", XCommonManager.INCREMENT);
        assertEquals(4, XCommonManager.INCREMENT.length());
    }

    // ----- getNodePath -------------------------------------------------------

    @Test
    public void nodePathIsNullForNodesShallowerThanThreeLevels() throws Exception {
        Document doc = newDoc();
        Element root = doc.createElement("root");
        doc.appendChild(root);
        // root chain is [root, #document] (size 2) => null; the document itself
        // (size 1) is null too.
        assertNull(XCommonManager.getNodePath(root));
        assertNull(XCommonManager.getNodePath(doc));
    }

    @Test
    public void nodePathDropsTheTopTwoAncestors() throws Exception {
        Document doc = newDoc();
        Element root = doc.createElement("root");
        doc.appendChild(root);
        Element a = doc.createElement("a");
        root.appendChild(a);
        Element b = doc.createElement("b");
        a.appendChild(b);
        Element c = doc.createElement("c");
        b.appendChild(c);

        // The #document and the root element are popped off; the rest is joined.
        assertEquals("//a", XCommonManager.getNodePath(a));
        assertEquals("//a/b", XCommonManager.getNodePath(b));
        assertEquals("//a/b/c", XCommonManager.getNodePath(c));
    }

    @Test
    public void nodePathEmitsXconfUidPredicate() throws Exception {
        Document doc = newDoc();
        Element root = doc.createElement("root");
        doc.appendChild(root);
        Element a = doc.createElement("a");
        root.appendChild(a);
        Element b = doc.createElement("b");
        b.setAttribute("xconf-uid", "42");
        a.appendChild(b);

        assertEquals("//a/b[@xconf-uid=\"42\"]", XCommonManager.getNodePath(b));
    }
}
