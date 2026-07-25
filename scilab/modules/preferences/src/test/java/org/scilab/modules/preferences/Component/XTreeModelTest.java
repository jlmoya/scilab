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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Hermetic unit tests for the package-private {@code XTreeModel} (declared in
 * {@code Tree.java}), a {@link javax.swing.tree.TreeModel} backed by
 * {@code XAdapterNode}. Every query delegates to the adapter, so this exercises
 * the delegation, the root-identity de-duplication in {@code setRoot}, and the
 * no-op listener plumbing. Pure DOM logic; no display required.
 */
public class XTreeModelTest {

    private static Document doc() throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
    }

    private static Element tree(Document d) {
        Element root = d.createElement("tree");
        root.setAttribute("name", "root");
        Element a = d.createElement("branch");
        a.setAttribute("name", "A");
        root.appendChild(a);
        Element b = d.createElement("branch");
        b.setAttribute("name", "B");
        root.appendChild(b);
        return root;
    }

    @Test
    public void rootWrapsThePeer() throws Exception {
        XTreeModel m = new XTreeModel(tree(doc()));
        Object root = m.getRoot();
        assertTrue(root instanceof XAdapterNode);
        assertEquals("root", root.toString());
    }

    @Test
    public void childCountAndChildDelegateToTheAdapter() throws Exception {
        XTreeModel m = new XTreeModel(tree(doc()));
        Object root = m.getRoot();
        assertEquals(2, m.getChildCount(root));
        assertEquals("A", m.getChild(root, 0).toString());
        assertEquals("B", m.getChild(root, 1).toString());
    }

    @Test
    public void getIndexOfChildDelegates() throws Exception {
        XTreeModel m = new XTreeModel(tree(doc()));
        Object root = m.getRoot();
        Object first = m.getChild(root, 0);
        assertEquals(0, m.getIndexOfChild(root, first));
    }

    @Test
    public void rootWithChildrenIsNotALeafButItsChildrenAre() throws Exception {
        XTreeModel m = new XTreeModel(tree(doc()));
        Object root = m.getRoot();
        assertFalse(m.isLeaf(root), "the root has two children");
        assertTrue(m.isLeaf(m.getChild(root, 0)), "the leaf <branch> has no children");
    }

    @Test
    public void setRootWithTheSamePeerKeepsTheSameRootInstance() throws Exception {
        Document d = doc();
        Element peer = tree(d);
        XTreeModel m = new XTreeModel(peer);
        Object first = m.getRoot();
        m.setRoot(peer);
        assertSame(first, m.getRoot(), "re-setting the identical peer must not rebuild the adapter");
    }

    @Test
    public void setRootWithADifferentPeerRebuildsTheRoot() throws Exception {
        Document d = doc();
        XTreeModel m = new XTreeModel(tree(d));
        Object first = m.getRoot();
        m.setRoot(tree(d));
        assertNotSame(first, m.getRoot());
        assertEquals("root", m.getRoot().toString());
    }

    @Test
    public void listenerPlumbingAndValueForPathChangedAreNoOps() throws Exception {
        XTreeModel m = new XTreeModel(tree(doc()));
        // None of these are implemented; the contract is simply "does not throw".
        m.addTreeModelListener(null);
        m.removeTreeModelListener(null);
        m.valueForPathChanged(null, null);
    }
}
