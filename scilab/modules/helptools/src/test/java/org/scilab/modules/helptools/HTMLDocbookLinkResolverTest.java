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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
}
