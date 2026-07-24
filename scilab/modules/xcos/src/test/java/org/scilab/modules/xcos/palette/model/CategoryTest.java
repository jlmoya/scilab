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

package org.scilab.modules.xcos.palette.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Enumeration;
import java.util.List;

import javax.swing.tree.TreeNode;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link Category}.
 *
 * <p>
 * A {@code Category} is the branch node of the palette tree: it owns a live list
 * of child {@link PaletteNode}s and implements the {@link TreeNode} contract on
 * top of it. Every assertion here works on plain in-memory objects, so no native
 * runtime is required.
 *
 * <p>
 * The inherited {@link PaletteNode#toString()} is exercised only for the
 * {@code null} and empty name cases; a non-empty name would route through
 * {@code Messages.gettext}, which is a native (JNI) call and therefore
 * deliberately left untested here.
 *
 * <p>
 * The package-private marshalling hooks ({@code beforeMarshal},
 * {@code afterMarshal}, {@code afterUnmarshal}) are reachable because this test
 * lives in the same package; they receive {@code null} for the (unused)
 * marshaller/unmarshaller arguments.
 */
public class CategoryTest {

    @Test
    public void freshCategoryHasEmptyLiveNodeList() {
        Category c = new Category();
        List<PaletteNode> nodes = c.getNode();
        assertNotNull(nodes);
        assertTrue(nodes.isEmpty());
    }

    @Test
    public void getNodeReturnsSameLiveListAcrossCalls() {
        Category c = new Category();
        List<PaletteNode> first = c.getNode();
        first.add(new PreLoaded());
        List<PaletteNode> second = c.getNode();
        assertSame(first, second, "getNode() must expose the live list, not a snapshot");
        assertEquals(1, second.size());
    }

    @Test
    public void allowsChildrenAndIsNotLeaf() {
        Category c = new Category();
        assertTrue(c.getAllowsChildren());
        assertFalse(c.isLeaf());
    }

    @Test
    public void childCountTracksTheLiveList() {
        Category c = new Category();
        assertEquals(0, c.getChildCount());
        c.getNode().add(new PreLoaded());
        c.getNode().add(new Custom());
        assertEquals(2, c.getChildCount());
    }

    @Test
    public void getChildAtReturnsElementAtIndex() {
        Category c = new Category();
        PreLoaded a = new PreLoaded();
        Custom b = new Custom();
        c.getNode().add(a);
        c.getNode().add(b);
        assertSame(a, c.getChildAt(0));
        assertSame(b, c.getChildAt(1));
    }

    @Test
    public void getChildAtOutOfRangeThrows() {
        Category c = new Category();
        assertThrows(IndexOutOfBoundsException.class, () -> c.getChildAt(0));
        c.getNode().add(new PreLoaded());
        assertThrows(IndexOutOfBoundsException.class, () -> c.getChildAt(5));
        assertThrows(IndexOutOfBoundsException.class, () -> c.getChildAt(-1));
    }

    @Test
    public void getIndexReturnsPositionOrMinusOne() {
        Category c = new Category();
        PreLoaded present = new PreLoaded();
        PreLoaded absent = new PreLoaded();
        c.getNode().add(present);
        assertEquals(0, c.getIndex(present));
        assertEquals(-1, c.getIndex(absent));
    }

    @Test
    public void childrenEnumeratesTheNodeListInOrder() {
        Category c = new Category();
        PreLoaded a = new PreLoaded();
        Custom b = new Custom();
        c.getNode().add(a);
        c.getNode().add(b);

        Enumeration<PaletteNode> e = c.children();
        assertTrue(e.hasMoreElements());
        assertSame(a, e.nextElement());
        assertSame(b, e.nextElement());
        assertFalse(e.hasMoreElements());
    }

    @Test
    public void childrenOfEmptyCategoryHasNoElements() {
        Category c = new Category();
        Enumeration<PaletteNode> e = c.children();
        assertNotNull(e);
        assertFalse(e.hasMoreElements());
    }

    @Test
    public void isAPaletteNodeAndTreeNode() {
        Category c = new Category();
        assertTrue(c instanceof PaletteNode);
        assertTrue(c instanceof TreeNode);
    }

    // ---- inherited PaletteNode behaviour ----

    @Test
    public void nameEnableParentRoundTrip() {
        Category c = new Category();
        assertNull(c.getName());
        assertFalse(c.isEnable());
        assertNull(c.getParent());

        Category parent = new Category();
        c.setName("Continuous");
        c.setEnable(true);
        c.setParent(parent);

        assertEquals("Continuous", c.getName());
        assertTrue(c.isEnable());
        assertSame(parent, c.getParent());
    }

    @Test
    public void toStringOfNullNameIsNull() {
        // null name short-circuits before the native Messages.gettext call
        assertNull(new Category().toString());
    }

    @Test
    public void toStringOfEmptyNameIsEmpty() {
        // empty name also short-circuits before the native Messages.gettext call
        Category c = new Category();
        c.setName("");
        assertEquals("", c.toString());
    }

    // ---- package-private marshalling hooks ----

    @Test
    public void marshalCycleStripsDynamicThenRestores() {
        Category c = new Category();
        c.setName("CategoryTest.marshalCycle");
        PreLoaded normal = new PreLoaded();
        PreLoaded.Dynamic dynamic = new PreLoaded.Dynamic();
        c.getNode().add(normal);
        c.getNode().add(dynamic);
        assertEquals(2, c.getChildCount());

        // beforeMarshal drops only the Dynamic palettes from the live list
        c.beforeMarshal(null);
        assertEquals(1, c.getChildCount());
        assertSame(normal, c.getChildAt(0));
        assertFalse(c.getNode().contains(dynamic));

        // afterMarshal restores the pre-marshal snapshot (both nodes present again)
        c.afterMarshal(null);
        assertEquals(2, c.getChildCount());
        assertTrue(c.getNode().contains(normal));
        assertTrue(c.getNode().contains(dynamic));
    }

    @Test
    public void afterUnmarshalPrunesEmptyChildCategoriesOnly() {
        Category root = new Category();
        Category empty = new Category();
        Category full = new Category();
        full.getNode().add(new PreLoaded());
        PreLoaded palette = new PreLoaded();

        root.getNode().add(empty);
        root.getNode().add(full);
        root.getNode().add(palette);
        assertEquals(3, root.getChildCount());

        root.afterUnmarshal(null, null);

        assertEquals(2, root.getChildCount());
        assertFalse(root.getNode().contains(empty), "empty sub-category must be pruned");
        assertTrue(root.getNode().contains(full), "non-empty sub-category must survive");
        assertTrue(root.getNode().contains(palette), "a Palette leaf is not a Category and must survive");
    }

    @Test
    public void afterUnmarshalWithNullNodeListIsANoOp() {
        Category c = new Category(); // node field still null: getNode() never called
        assertDoesNotThrow(() -> c.afterUnmarshal(null, null));
    }

    /**
     * Behaviour characterization: {@link Category} overrides the unmarshalling
     * hook <em>without</em> chaining to {@link PaletteNode#afterUnmarshal}, so —
     * unlike a {@link Palette} leaf — a Category never records its own parent
     * back-pointer during unmarshalling.
     *
     * @see CustomTest#afterUnmarshalSetsParentFromBaseHook()
     */
    @Test
    public void afterUnmarshalDoesNotSetOwnParent_characterization() {
        Category parent = new Category();
        Category child = new Category();
        child.afterUnmarshal(null, parent);
        assertNull(child.getParent());
    }

    /**
     * Defect characterization: {@code beforeMarshal} dereferences the raw
     * {@code node} field with no lazy initialisation, so a Category whose node
     * list was never materialised (field still {@code null}) fails with a
     * {@link NullPointerException} instead of marshalling as empty.
     */
    @Test
    public void beforeMarshalOnUntouchedCategoryThrowsNpe_defectCharacterization() {
        Category c = new Category();
        c.setName("CategoryTest.beforeMarshalNpe");
        assertThrows(NullPointerException.class, () -> c.beforeMarshal(null));
    }
}
