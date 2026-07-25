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

package org.scilab.modules.commons.xml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Hermetic unit tests for {@link ScilabXMLUtilities}.
 *
 * <p>Every method is exercised against DOM trees built in-memory with the same JDK-internal
 * JAXP implementation the class itself selects (via {@link ScilabDocumentBuilderFactory}), so
 * no live Scilab, no network and no fixed on-disk resource is involved. File round-trips use a
 * JUnit {@code @TempDir}. The class references {@code Messages.gettext} in two static
 * initializers; that call resolves to the untranslated key through the localization native lib
 * present on the test {@code java.library.path}, so class initialization is side-effect free.
 *
 * <p>Several assertions are deliberately <em>defect-characterization</em> tests (clearly named):
 * {@code removeEmptyLines} strips <em>all</em> text nodes, not merely whitespace, and
 * {@code writeDocument} inherits that behaviour, dropping element text content on save.
 */
public class ScilabXMLUtilitiesTest {

    private DocumentBuilder builder;

    @BeforeEach
    public void setUp() throws Exception {
        builder = ScilabDocumentBuilderFactory.newInstance().newDocumentBuilder();
    }

    private Document newDoc(String rootTag) {
        Document doc = builder.newDocument();
        doc.appendChild(doc.createElement(rootTag));
        return doc;
    }

    // ----------------------------------------------------------------- getXMLString

    @Test
    public void getXMLStringEscapesEachReservedCharacter() {
        assertEquals("a&amp;b", ScilabXMLUtilities.getXMLString("a&b"));
        assertEquals("&lt;tag&gt;", ScilabXMLUtilities.getXMLString("<tag>"));
        assertEquals("&apos;", ScilabXMLUtilities.getXMLString("'"));
        assertEquals("&quot;", ScilabXMLUtilities.getXMLString("\""));
    }

    @Test
    public void getXMLStringEscapesAMixtureAndPreservesSurroundingText() {
        assertEquals("x&amp;y&lt;z", ScilabXMLUtilities.getXMLString("x&y<z"));
        // Leading special: the "last" cursor starts at 0 and the head branch is taken.
        assertEquals("&amp;tail", ScilabXMLUtilities.getXMLString("&tail"));
        // Trailing special: the final "append the remainder" branch (last < length) runs.
        assertEquals("head&amp;", ScilabXMLUtilities.getXMLString("head&"));
        assertEquals("&lt;a&gt;&amp;&apos;&quot;", ScilabXMLUtilities.getXMLString("<a>&'\""));
    }

    @Test
    public void getXMLStringReturnsInputUnchangedWhenNothingToEscape() {
        String plain = "plain-text_123";
        // No reserved char => the method returns the very same instance (last == 0 fast path).
        assertSame(plain, ScilabXMLUtilities.getXMLString(plain));
    }

    @Test
    public void getXMLStringHandlesNullAndEmpty() {
        assertNull(ScilabXMLUtilities.getXMLString(null));
        assertEquals("", ScilabXMLUtilities.getXMLString(""));
    }

    // ----------------------------------------------------------------- createNode

    @Test
    public void createNodeWithArrayAppliesAttributesAndAppends() {
        Document doc = newDoc("root");
        Element root = doc.getDocumentElement();

        Element node = ScilabXMLUtilities.createNode(doc, root, "child", new Object[] {"a", 1, "b", true});

        assertEquals("child", node.getTagName());
        assertEquals("1", node.getAttribute("a"));
        assertEquals("true", node.getAttribute("b"));
        assertSame(root, node.getParentNode());
    }

    @Test
    public void createNodeAlwaysCreatesANewElementEvenWhenOneExists() {
        Document doc = newDoc("root");
        Element root = doc.getDocumentElement();

        Element first = ScilabXMLUtilities.createNode(doc, root, "dup", new Object[] {"a", "1"});
        Element second = ScilabXMLUtilities.createNode(doc, root, "dup", new Object[] {"a", "2"});

        assertNotSame(first, second);
        assertEquals(2, root.getElementsByTagName("dup").getLength());
        assertEquals("2", second.getAttribute("a"));
    }

    @Test
    public void createNodeWithMapAppliesAttributes() {
        Document doc = newDoc("root");
        Element root = doc.getDocumentElement();
        Map<String, Object> attrs = new LinkedHashMap<String, Object>();
        attrs.put("width", 42);
        attrs.put("name", "box");

        Element node = ScilabXMLUtilities.createNode(doc, root, "item", attrs);

        assertEquals("42", node.getAttribute("width"));
        assertEquals("box", node.getAttribute("name"));
        assertSame(root, node.getParentNode());
    }

    // ----------------------------------------------------------------- replaceNamedNode (Element parent)

    @Test
    public void replaceNamedNodeCreatesWhenAbsentThenReusesTheSameElement() {
        Document doc = newDoc("root");
        Element root = doc.getDocumentElement();

        Element created = ScilabXMLUtilities.replaceNamedNode(doc, root, "item", new Object[] {"a", "1"});
        assertNotNull(created);
        assertEquals("1", created.getAttribute("a"));

        // A second call must UPDATE the existing element, not append a sibling.
        Element again = ScilabXMLUtilities.replaceNamedNode(doc, root, "item", new Object[] {"a", "2", "b", "x"});
        assertSame(created, again);
        assertEquals("2", again.getAttribute("a"));
        assertEquals("x", again.getAttribute("b"));
        assertEquals(1, root.getElementsByTagName("item").getLength());
    }

    @Test
    public void replaceNamedNodeWithMapUpdatesInPlace() {
        Document doc = newDoc("root");
        Element root = doc.getDocumentElement();
        Map<String, Object> first = new HashMap<String, Object>();
        first.put("v", "1");
        Element e1 = ScilabXMLUtilities.replaceNamedNode(doc, root, "opt", first);

        Map<String, Object> second = new HashMap<String, Object>();
        second.put("v", "2");
        Element e2 = ScilabXMLUtilities.replaceNamedNode(doc, root, "opt", second);

        assertSame(e1, e2);
        assertEquals("2", e2.getAttribute("v"));
        assertEquals(1, root.getElementsByTagName("opt").getLength());
    }

    // ----------------------------------------------------------------- replaceNamedNode (parent by name)

    @Test
    public void replaceNamedNodeByParentNameLocatesTheParentUnderTheRoot() {
        Document doc = newDoc("root");
        Element root = doc.getDocumentElement();
        Element section = doc.createElement("section");
        root.appendChild(section);

        Element item = ScilabXMLUtilities.replaceNamedNode(doc, "section", "item", new Object[] {"k", "v"});
        assertNotNull(item);
        assertEquals("v", item.getAttribute("k"));
        assertSame(section, item.getParentNode());
    }

    @Test
    public void replaceNamedNodeByParentNameReturnsNullWhenParentMissing() {
        Document doc = newDoc("root");
        assertNull(ScilabXMLUtilities.replaceNamedNode(doc, "nosuchparent", "item", new Object[] {}));
    }

    @Test
    public void replaceNamedNodeByParentNameWithMapReturnsNullWhenParentMissing() {
        Document doc = newDoc("root");
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("k", "v");
        assertNull(ScilabXMLUtilities.replaceNamedNode(doc, "ghost", "item", map));
    }

    @Test
    public void replaceNamedNodeByParentNameWithMapUpdatesUnderNamedParent() {
        Document doc = newDoc("root");
        Element root = doc.getDocumentElement();
        Element body = doc.createElement("body");
        root.appendChild(body);
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("color", "red");
        map.put("size", 3);

        Element made = ScilabXMLUtilities.replaceNamedNode(doc, "body", "pen", map);
        assertNotNull(made);
        assertEquals("red", made.getAttribute("color"));
        assertEquals("3", made.getAttribute("size"));
        assertSame(body, made.getParentNode());
    }

    // ----------------------------------------------------------------- readNodeAttributes + convert

    @Test
    public void readNodeAttributesConvertsEveryPrimitiveTypeViaMap() {
        Document doc = newDoc("root");
        Element e = doc.createElement("values");
        e.setAttribute("i", "42");
        e.setAttribute("l", "9000000000");
        e.setAttribute("s", "7");
        e.setAttribute("by", "5");
        e.setAttribute("f", "1.5");
        e.setAttribute("d", "3.25");
        e.setAttribute("bo", "true");
        e.setAttribute("c", "Z");
        e.setAttribute("txt", "hello");

        Map<String, Object> map = new HashMap<String, Object>();
        map.put("i", int.class);
        map.put("l", long.class);
        map.put("s", short.class);
        map.put("by", byte.class);
        map.put("f", float.class);
        map.put("d", double.class);
        map.put("bo", boolean.class);
        map.put("c", char.class);
        map.put("txt", String.class);

        ScilabXMLUtilities.readNodeAttributes(e, map);

        assertEquals(Integer.valueOf(42), map.get("i"));
        assertEquals(Long.valueOf(9000000000L), map.get("l"));
        assertEquals(Short.valueOf((short) 7), map.get("s"));
        assertEquals(Byte.valueOf((byte) 5), map.get("by"));
        assertEquals(Float.valueOf(1.5f), map.get("f"));
        assertEquals(Double.valueOf(3.25), map.get("d"));
        assertEquals(Boolean.TRUE, map.get("bo"));
        assertEquals(Character.valueOf('Z'), map.get("c"));
        // Non-primitive class => the raw string is returned unchanged.
        assertEquals("hello", map.get("txt"));
    }

    @Test
    public void readNodeAttributesConvertsEmptyCharToNulCharacterAndSplitsStringArrays() {
        Document doc = newDoc("root");
        Element e = doc.createElement("v");
        e.setAttribute("empty", "");
        e.setAttribute("list", "a;b;c");

        Map<String, Object> map = new HashMap<String, Object>();
        map.put("empty", char.class);
        map.put("list", String[].class);

        ScilabXMLUtilities.readNodeAttributes(e, map);

        assertEquals(Character.valueOf('\0'), map.get("empty"));
        assertNotNull(map.get("list"));
        assertTrue(map.get("list") instanceof String[]);
        String[] arr = (String[]) map.get("list");
        assertEquals(3, arr.length);
        assertEquals("a", arr[0]);
        assertEquals("b", arr[1]);
        assertEquals("c", arr[2]);
    }

    @Test
    public void readNodeAttributesLeavesUnlistedAttributesUntouched() {
        Document doc = newDoc("root");
        Element e = doc.createElement("v");
        e.setAttribute("wanted", "10");
        e.setAttribute("ignored", "999");

        Map<String, Object> map = new HashMap<String, Object>();
        map.put("wanted", int.class);
        // "ignored" is present on the element but NOT requested, so it must not appear.

        ScilabXMLUtilities.readNodeAttributes(e, map);

        assertEquals(Integer.valueOf(10), map.get("wanted"));
        assertFalse(map.containsKey("ignored"));
        assertEquals(1, map.size());
    }

    @Test
    public void readNodeAttributesArrayFormConvertsInPlace() {
        Document doc = newDoc("root");
        Element e = doc.createElement("v");
        e.setAttribute("i", "7");
        e.setAttribute("bo", "false");

        Object[] pairs = new Object[] {"i", int.class, "bo", boolean.class};
        ScilabXMLUtilities.readNodeAttributes(e, pairs);

        assertEquals(Integer.valueOf(7), pairs[1]);
        assertEquals(Boolean.FALSE, pairs[3]);
    }

    @Test
    public void readNodeAttributesByTagNameFindsFirstAndConverts() {
        Document doc = newDoc("root");
        Element root = doc.getDocumentElement();
        Element node = doc.createElement("node");
        node.setAttribute("x", "5");
        root.appendChild(node);

        Map<String, Object> map = new HashMap<String, Object>();
        map.put("x", int.class);

        Element found = ScilabXMLUtilities.readNodeAttributes(doc, "node", map);
        assertSame(node, found);
        assertEquals(Integer.valueOf(5), map.get("x"));
    }

    @Test
    public void readNodeAttributesByTagNameReturnsNullWhenAbsent() {
        Document doc = newDoc("root");
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("x", int.class);
        assertNull(ScilabXMLUtilities.readNodeAttributes(doc, "absent", map));
    }

    @Test
    public void readNodeAttributesByTagNameArrayFormReturnsNullWhenAbsent() {
        Document doc = newDoc("root");
        Object[] pairs = new Object[] {"x", int.class};
        assertNull(ScilabXMLUtilities.readNodeAttributes(doc, "absent", pairs));
    }

    @Test
    public void readNodeAttributesByTagNameArrayFormFindsAndConverts() {
        Document doc = newDoc("root");
        Element root = doc.getDocumentElement();
        Element node = doc.createElement("cfg");
        node.setAttribute("on", "true");
        root.appendChild(node);

        Object[] pairs = new Object[] {"on", boolean.class};
        Element found = ScilabXMLUtilities.readNodeAttributes(doc, "cfg", pairs);
        assertSame(node, found);
        assertEquals(Boolean.TRUE, pairs[1]);
    }

    // ----------------------------------------------------------------- getElementsWithAttributeEquals

    @Test
    public void getElementsWithAttributeEqualsWalksTheWholeSubtreeIncludingTheRoot() {
        Document doc = newDoc("root");
        Element root = doc.getDocumentElement();
        root.setAttribute("type", "x");

        Element c1 = doc.createElement("c");
        c1.setAttribute("type", "x");
        root.appendChild(c1);

        Element c2 = doc.createElement("c");
        c2.setAttribute("type", "y");
        root.appendChild(c2);

        Element grandChild = doc.createElement("d");
        grandChild.setAttribute("type", "x");
        c2.appendChild(grandChild);

        List<Element> matches = ScilabXMLUtilities.getElementsWithAttributeEquals(root, "type", "x");

        assertEquals(3, matches.size());
        assertTrue(matches.contains(root));
        assertTrue(matches.contains(c1));
        assertTrue(matches.contains(grandChild));
        assertFalse(matches.contains(c2));
    }

    @Test
    public void getElementsWithAttributeEqualsReturnsEmptyWhenNoMatch() {
        Document doc = newDoc("root");
        Element root = doc.getDocumentElement();
        root.setAttribute("type", "a");
        List<Element> matches = ScilabXMLUtilities.getElementsWithAttributeEquals(root, "type", "zzz");
        assertNotNull(matches);
        assertTrue(matches.isEmpty());
    }

    // ----------------------------------------------------------------- removeEmptyLines

    @Test
    public void removeEmptyLinesStripsAllTextNodesInTheSubtree() {
        Document doc = newDoc("r");
        Element r = doc.getDocumentElement();
        Element child = doc.createElement("child");
        r.appendChild(doc.createTextNode("\n   "));
        r.appendChild(child);
        // Defect characterization: even NON-whitespace text is removed.
        child.appendChild(doc.createTextNode("some real text"));
        r.appendChild(doc.createTextNode("\n"));

        ScilabXMLUtilities.removeEmptyLines(r);

        assertEquals(1, r.getChildNodes().getLength());
        assertSame(child, r.getChildNodes().item(0));
        assertEquals(0, child.getChildNodes().getLength());
    }

    // ----------------------------------------------------------------- write / read round-trip

    @Test
    public void writeThenReadRoundTripsElementStructureAndAttributes(@TempDir Path tmp) {
        Document doc = newDoc("config");
        Element root = doc.getDocumentElement();
        Element entry = doc.createElement("entry");
        entry.setAttribute("key", "k1");
        entry.setAttribute("val", "v1");
        root.appendChild(entry);

        String path = tmp.resolve("out.xml").toString();
        ScilabXMLUtilities.writeDocument(doc, path);
        assertTrue(new File(path).exists());

        Document read = ScilabXMLUtilities.readDocument(path);
        assertNotNull(read);
        assertEquals("config", read.getDocumentElement().getTagName());

        NodeList entries = read.getDocumentElement().getElementsByTagName("entry");
        assertEquals(1, entries.getLength());
        Element re = (Element) entries.item(0);
        assertEquals("k1", re.getAttribute("key"));
        assertEquals("v1", re.getAttribute("val"));
    }

    @Test
    public void writeDocumentIsANoOpForNullDocumentOrNullFileName(@TempDir Path tmp) {
        String path = tmp.resolve("never.xml").toString();
        ScilabXMLUtilities.writeDocument(null, path);
        assertFalse(new File(path).exists(), "no file must be written for a null document");

        Document doc = newDoc("root");
        // A null filename must be silently ignored rather than throwing.
        ScilabXMLUtilities.writeDocument(doc, null);
    }

    @Test
    public void readDocumentReturnsNullForAMissingFile(@TempDir Path tmp) {
        assertNull(ScilabXMLUtilities.readDocument(tmp.resolve("does-not-exist.xml").toString()));
    }

    @Test
    public void readDocumentReturnsNullForMalformedXml(@TempDir Path tmp) throws Exception {
        Path bad = tmp.resolve("bad.xml");
        Files.write(bad, "<not><closed>".getBytes(StandardCharsets.UTF_8));
        assertNull(ScilabXMLUtilities.readDocument(bad.toString()));
    }
}
