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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Hermetic unit tests for the package-private {@code XAdapterNode} (declared in
 * {@code Tree.java}). It wraps a DOM node and exposes a tree-ish view of it: the
 * child accessors deliberately SKIP {@code #text}/{@code #comment} nodes and any
 * {@code actionPerformed} element, so counting and indexing operate on the
 * "structural" children only. This is pure DOM traversal &mdash; no Swing, no
 * native code, no display.
 */
public class XAdapterNodeTest {

    private static Document doc() throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
    }

    /** A "tree" element with 2 structural children (nodeA, nodeB) plus noise
     *  (a text node, a comment, an actionPerformed element) that must be ignored. */
    private static Element treeWithNoise(Document d) {
        Element root = d.createElement("tree");
        root.setAttribute("name", "root");
        root.appendChild(d.createTextNode("  whitespace  "));
        Element a = d.createElement("branch");
        a.setAttribute("name", "A");
        root.appendChild(a);
        root.appendChild(d.createComment("a comment"));
        Element ap = d.createElement("actionPerformed");
        root.appendChild(ap);
        Element b = d.createElement("branch");
        b.setAttribute("name", "B");
        root.appendChild(b);
        return root;
    }

    @Test
    public void getChildCountSkipsTextCommentAndActionPerformed() throws Exception {
        XAdapterNode root = new XAdapterNode(treeWithNoise(doc()));
        assertEquals(2, root.getChildCount(), "only the two <branch> elements are structural children");
    }

    @Test
    public void getChildReturnsStructuralChildrenInOrder() throws Exception {
        XAdapterNode root = new XAdapterNode(treeWithNoise(doc()));
        assertEquals("A", root.getChild(0).toString());
        assertEquals("B", root.getChild(1).toString());
    }

    @Test
    public void getChildBeyondEndReturnsNull() throws Exception {
        XAdapterNode root = new XAdapterNode(treeWithNoise(doc()));
        assertNull(root.getChild(2), "there is no third structural child");
    }

    @Test
    public void getIndexOfChildMatchesOnPeerIdentity() throws Exception {
        XAdapterNode root = new XAdapterNode(treeWithNoise(doc()));
        assertEquals(0, root.getIndexOfChild(root.getChild(0)));
        assertEquals(1, root.getIndexOfChild(root.getChild(1)));
    }

    @Test
    public void getIndexOfChildReturnsMinusOneForAStranger() throws Exception {
        Document d = doc();
        XAdapterNode root = new XAdapterNode(treeWithNoise(d));
        XAdapterNode stranger = new XAdapterNode(d.createElement("branch"));
        assertEquals(-1, root.getIndexOfChild(stranger));
    }

    @Test
    public void getPeerReturnsTheWrappedNode() throws Exception {
        Document d = doc();
        Element root = treeWithNoise(d);
        assertSame(root, new XAdapterNode(root).getPeer());
    }

    @Test
    public void contentIsTheNodeName() throws Exception {
        XAdapterNode root = new XAdapterNode(treeWithNoise(doc()));
        assertEquals("tree", root.content());
    }

    @Test
    public void toStringIsTheNameAttribute() throws Exception {
        XAdapterNode root = new XAdapterNode(treeWithNoise(doc()));
        assertEquals("root", root.toString());
    }

    @Test
    public void toStringDefaultsToEmptyWhenNoNameAttribute() throws Exception {
        Document d = doc();
        XAdapterNode nameless = new XAdapterNode(d.createElement("branch"));
        assertEquals("", nameless.toString(), "a missing 'name' attribute yields the empty string, not NAV");
    }

    @Test
    public void aStructuralChildWithNoOwnChildrenHasZeroCount() throws Exception {
        XAdapterNode root = new XAdapterNode(treeWithNoise(doc()));
        XAdapterNode leaf = root.getChild(0);
        assertNotNull(leaf);
        assertEquals(0, leaf.getChildCount());
    }
}
