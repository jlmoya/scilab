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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.event.TreeSelectionEvent;
import javax.swing.tree.TreePath;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Hermetic unit tests for the {@link Tree} preference component. {@code Tree}
 * extends {@link Panel} and wraps a {@code JTree} whose model adapts the DOM
 * children of its node (each element child is a row; {@code #...} and
 * {@code actionPerformed} nodes are skipped). Constructed headless.
 *
 * <p>Exercised: the {@code path} sensor/actuator round-trip (a one-based
 * "index/" chain of the current selection), the {@code choose} value, and the
 * {@code valueChanged} to {@code ActionListener} bridge. The renderer and
 * expansion painting are display-bound and out of scope.
 */
public class TreeTest {

    private static Document newDoc() throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
    }

    /** {@code <Tree><branch name="a">...2 leaves...</branch><branch name="b"/></Tree>}. */
    private static Element treeNode(Document doc) {
        Element tree = doc.createElement("Tree");
        Element a = doc.createElement("branch");
        a.setAttribute("name", "a");
        Element a1 = doc.createElement("leaf");
        a1.setAttribute("name", "a1");
        Element a2 = doc.createElement("leaf");
        a2.setAttribute("name", "a2");
        a.appendChild(a1);
        a.appendChild(a2);
        Element b = doc.createElement("branch");
        b.setAttribute("name", "b");
        tree.appendChild(a);
        tree.appendChild(b);
        return tree;
    }

    // ----- trivial surface --------------------------------------------------

    @Test
    public void constructsHeadlessFromADomTree() throws Exception {
        Tree t = new Tree(treeNode(newDoc()));
        assertNotNull(t);
        assertArrayEquals(new String[] {"item"}, t.actuators());
        assertEquals("Tree ...", t.toString());
    }

    @Test
    public void pathIsMinusOneWithNoSelection() throws Exception {
        Tree t = new Tree(treeNode(newDoc()));
        assertEquals("-1", t.path(), "no selection => sentinel \"-1\"");
        assertEquals("-1", t.choose(), "choose() delegates to path()");
    }

    // ----- path sensor / actuator -------------------------------------------

    @Test
    public void pathActuatorSelectsTheOneBasedTopLevelRow() throws Exception {
        Tree t = new Tree(treeNode(newDoc()));
        t.path("1/");
        assertEquals("1/", t.path(), "first top-level branch reads back as \"1/\"");
        assertEquals("1/", t.choose());
    }

    @Test
    public void pathActuatorSelectsTheSecondBranch() throws Exception {
        Tree t = new Tree(treeNode(newDoc()));
        t.path("2/");
        assertEquals("2/", t.path());
    }

    @Test
    public void nonNumericPathFallsBackToTheFirstRow() throws Exception {
        // NumberFormatException handler selects row 0 => path() reports "1/".
        Tree t = new Tree(treeNode(newDoc()));
        t.path("bogus/");
        assertEquals("1/", t.path());
    }

    // ----- valueChanged / listener ------------------------------------------

    @Test
    public void valueChangedForwardsToTheRegisteredActionListener() throws Exception {
        Tree t = new Tree(treeNode(newDoc()));
        AtomicReference<String> got = new AtomicReference<>();
        t.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                got.set(e.getActionCommand());
            }
        });

        t.valueChanged(new TreeSelectionEvent(t, new TreePath(new Object[] {"r"}), true, null, null));
        assertEquals("actionPerformed", got.get());
    }

    @Test
    public void valueChangedIsSilentWithoutAListener() throws Exception {
        Tree t = new Tree(treeNode(newDoc()));
        // No listener registered: must not throw.
        t.valueChanged(new TreeSelectionEvent(t, new TreePath(new Object[] {"r"}), true, null, null));
    }
}
