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

package org.scilab.modules.helptools;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.SAXException;

import org.scilab.modules.helptools.HTMLDocbookLinkResolver.TreeId;

/**
 * Hermetic unit tests for {@link HTMLDocbookLinkResolver}, the SAX pass that indexes
 * a DocBook document's {@code xml:id}s into id→file, id→title and a navigable tree.
 *
 * <p>The resolver only needs a local file (no Scilab, no network), so these tests
 * parse a tiny two-node DocBook document from a temp directory and assert the maps
 * it produces plus the {@link TreeId} sibling/parent navigation. A single, unique
 * id is used so {@code makeFileName} takes its plain {@code id + ".html"} branch on
 * every platform (the MD5-disambiguation branch only fires for duplicate ids on a
 * case-insensitive OS).
 */
public class HTMLDocbookLinkResolverTest {

    private static final String DOCBOOK =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
        + "<book xmlns=\"http://docbook.org/ns/docbook\">\n"
        + "  <chapter xml:id=\"c1\">\n"
        + "    <title>Hello</title>\n"
        + "  </chapter>\n"
        + "</book>\n";

    private static HTMLDocbookLinkResolver resolve(Path dir) throws Exception {
        File doc = new File(dir.toFile(), "doc.xml");
        Files.writeString(doc.toPath(), DOCBOOK, StandardCharsets.UTF_8);
        return new HTMLDocbookLinkResolver(doc.getAbsolutePath());
    }

    @Test
    public void indexesTheChapterIdToItsHtmlFile(@TempDir Path dir) throws Exception {
        HTMLDocbookLinkResolver r = resolve(dir);
        assertEquals(1, r.getMapId().size());
        assertEquals("c1.html", r.getMapId().get("c1"));
    }

    @Test
    public void collectsTitleIntoTocAndRefname(@TempDir Path dir) throws Exception {
        HTMLDocbookLinkResolver r = resolve(dir);
        assertEquals("Hello", r.getToc().get("c1"));
        assertEquals("Hello", r.getMapIdRefname().get("c1"));
        // No <refpurpose> in the document => the purpose map stays empty.
        assertTrue(r.getMapIdPurpose().isEmpty());
    }

    @Test
    public void buildsARootedTreeWithTheChapterAsAChild(@TempDir Path dir) throws Exception {
        HTMLDocbookLinkResolver r = resolve(dir);
        TreeId root = r.getTree();
        assertTrue(root.isRoot());
        assertEquals(1, root.children.size());
        assertEquals("c1", root.children.get(0).id);
    }

    @Test
    public void treeNavigationFromTheChapterNode(@TempDir Path dir) throws Exception {
        HTMLDocbookLinkResolver r = resolve(dir);
        TreeId root = r.getTree();
        TreeId c1 = r.getMapTreeId().get("c1");

        assertNotNull(c1);
        assertFalse(c1.isRoot());
        // First (and only) child: previous falls back to the parent, next is null.
        assertSame(root, c1.getPrevious());
        assertNull(c1.getNext());
    }

    @Test
    public void makeFileNameUsesPlainHtmlNameForAFreshId(@TempDir Path dir) throws Exception {
        HTMLDocbookLinkResolver r = resolve(dir);
        assertEquals("aFreshUniqueId.html", r.makeFileName("aFreshUniqueId"));
    }

    // ==================================================================
    // A richer, multi-level document exercising part/chapter/section and
    // a refentry (refname/refpurpose), plus tree navigation and escaping.
    // ==================================================================

    /**
     * <pre>
     *   book
     *   └─ part p1  "Part &amp; One"
     *      ├─ chapter c1  "Chapter One"
     *      │  ├─ section s1  "Sec1"
     *      │  └─ section s2  "Sec2"
     *      └─ refentry r1   refname "func", refpurpose "does &lt;stuff&gt;"
     * </pre>
     */
    private static final String NESTED =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
        + "<book xmlns=\"http://docbook.org/ns/docbook\">\n"
        + "  <part xml:id=\"p1\">\n"
        + "    <title>Part &amp; One</title>\n"
        + "    <chapter xml:id=\"c1\">\n"
        + "      <title>Chapter One</title>\n"
        + "      <section xml:id=\"s1\"><title>Sec1</title></section>\n"
        + "      <section xml:id=\"s2\"><title>Sec2</title></section>\n"
        + "    </chapter>\n"
        + "    <refentry xml:id=\"r1\">\n"
        + "      <refnamediv>\n"
        + "        <refname>func</refname>\n"
        + "        <refpurpose>does &lt;stuff&gt;</refpurpose>\n"
        + "      </refnamediv>\n"
        + "    </refentry>\n"
        + "  </part>\n"
        + "</book>\n";

    private static HTMLDocbookLinkResolver resolveXml(Path dir, String xml, String name) throws Exception {
        File doc = new File(dir.toFile(), name);
        Files.writeString(doc.toPath(), xml, StandardCharsets.UTF_8);
        return new HTMLDocbookLinkResolver(doc.getAbsolutePath());
    }

    @Test
    public void nestedDocumentIndexesEveryStructuralId(@TempDir Path dir) throws Exception {
        HTMLDocbookLinkResolver r = resolveXml(dir, NESTED, "nested.xml");
        Map<String, String> ids = r.getMapId();
        assertEquals(5, ids.size());
        assertEquals("p1.html", ids.get("p1"));
        assertEquals("c1.html", ids.get("c1"));
        assertEquals("s1.html", ids.get("s1"));
        assertEquals("s2.html", ids.get("s2"));
        assertEquals("r1.html", ids.get("r1"));
    }

    @Test
    public void titlesAndRefnameGoIntoTocXmlEscaped(@TempDir Path dir) throws Exception {
        HTMLDocbookLinkResolver r = resolveXml(dir, NESTED, "nested.xml");
        // '&' inside a title is re-escaped by the resolver's own characters() handler.
        assertEquals("Part &amp; One", r.getToc().get("p1"));
        assertEquals("Chapter One", r.getToc().get("c1"));
        assertEquals("Sec1", r.getToc().get("s1"));
        // A <refname> populates the same toc/refname maps as a <title>.
        assertEquals("func", r.getToc().get("r1"));
        assertEquals("func", r.getMapIdRefname().get("r1"));
    }

    @Test
    public void refpurposeIsCollectedForTheRefentry(@TempDir Path dir) throws Exception {
        HTMLDocbookLinkResolver r = resolveXml(dir, NESTED, "nested.xml");
        Map<String, String> purpose = r.getMapIdPurpose();
        assertEquals(1, purpose.size());
        assertEquals("does &lt;stuff&gt;", purpose.get("r1"));
    }

    @Test
    public void treeMirrorsTheDocumentNesting(@TempDir Path dir) throws Exception {
        HTMLDocbookLinkResolver r = resolveXml(dir, NESTED, "nested.xml");
        TreeId root = r.getTree();
        assertTrue(root.isRoot());
        assertEquals(1, root.children.size());

        TreeId p1 = root.children.get(0);
        assertEquals("p1", p1.id);
        assertSame(root, p1.parent);
        // A <part> has the chapter and the refentry as its two children.
        assertEquals(2, p1.children.size());
        assertEquals("c1", p1.children.get(0).id);
        assertEquals("r1", p1.children.get(1).id);

        TreeId c1 = r.getMapTreeId().get("c1");
        assertEquals(2, c1.children.size());
        assertEquals("s1", c1.children.get(0).id);
        assertEquals("s2", c1.children.get(1).id);
    }

    @Test
    public void siblingAndCrossLevelNavigation(@TempDir Path dir) throws Exception {
        HTMLDocbookLinkResolver r = resolveXml(dir, NESTED, "nested.xml");
        Map<String, TreeId> t = r.getMapTreeId();
        TreeId root = r.getTree();
        TreeId p1 = t.get("p1");
        TreeId c1 = t.get("c1");
        TreeId r1 = t.get("r1");
        TreeId s1 = t.get("s1");
        TreeId s2 = t.get("s2");

        // First child's previous falls back to its parent.
        assertSame(c1, s1.getPrevious());
        // Second child's previous is the first child.
        assertSame(s1, s2.getPrevious());
        // Forward within the same parent.
        assertSame(s2, s1.getNext());
        // Last child of a chapter climbs and lands on the chapter's next sibling.
        assertSame(r1, s2.getNext());
        // Within the part: chapter's previous is the part, refentry's previous is the chapter.
        assertSame(p1, c1.getPrevious());
        assertSame(c1, r1.getPrevious());
        // The very last leaf and the only top-level node have no successor.
        assertNull(r1.getNext());
        assertNull(p1.getNext());
        // The part's previous is the root (it is the root's first child).
        assertSame(root, p1.getPrevious());
    }

    @Test
    public void characterEscapingCoversAllFiveEntities(@TempDir Path dir) throws Exception {
        String xml =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<book xmlns=\"http://docbook.org/ns/docbook\">\n"
            + "  <chapter xml:id=\"e1\"><title>a'b\"c&lt;d&gt;e&amp;f</title></chapter>\n"
            + "</book>\n";
        HTMLDocbookLinkResolver r = resolveXml(dir, xml, "escape.xml");
        // ' -> &#0039;, " -> &quot;, < -> &lt;, > -> &gt;, & -> &amp;
        assertEquals("a&#0039;b&quot;c&lt;d&gt;e&amp;f", r.getToc().get("e1"));
    }

    @Test
    public void makeFileNameDisambiguatesAlreadySeenIdOnCaseInsensitiveOs(@TempDir Path dir) throws Exception {
        HTMLDocbookLinkResolver r = resolveXml(dir, NESTED, "nested.xml");
        String fileName = r.makeFileName("c1"); // "c1" was indexed during the parse
        boolean caseInsensitive = System.getProperty("os.name").toLowerCase().contains("windows")
                                  || System.getProperty("os.name").toLowerCase().contains("mac");
        if (caseInsensitive) {
            // id + '-' + 32-hex-MD5 + ".html"
            assertTrue(fileName.matches("c1-[0-9a-f]{32}\\.html"),
                       "expected an MD5-disambiguated name, was: " + fileName);
        } else {
            assertEquals("c1.html", fileName);
        }
    }

    // ---- error paths ---------------------------------------------------

    @Test
    public void structuralElementWithoutIdAbortsParsing(@TempDir Path dir) {
        String xml =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<book xmlns=\"http://docbook.org/ns/docbook\">\n"
            + "  <section><title>no id here</title></section>\n"
            + "</book>\n";
        assertThrows(SAXException.class, () -> resolveXml(dir, xml, "noid.xml"));
    }

    @Test
    public void duplicateIdAbortsParsing(@TempDir Path dir) {
        String xml =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<book xmlns=\"http://docbook.org/ns/docbook\">\n"
            + "  <chapter xml:id=\"dup\"><title>A</title></chapter>\n"
            + "  <chapter xml:id=\"dup\"><title>B</title></chapter>\n"
            + "</book>\n";
        assertThrows(SAXException.class, () -> resolveXml(dir, xml, "dup.xml"));
    }
}
