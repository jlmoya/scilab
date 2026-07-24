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

import javax.swing.tree.TreeNode;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link Custom}.
 *
 * <p>
 * {@code Custom} is a {@link Palette} leaf that adds a single {@link VariablePath}
 * {@code path} property; it inherits its neutral {@link TreeNode} behaviour from
 * {@code Palette}. All assertions operate on plain in-memory objects, so no
 * native runtime is required.
 *
 * <p>
 * The inherited {@link PaletteNode#toString()} is exercised only for the
 * {@code null} and empty name cases; a non-empty name routes through the native
 * {@code Messages.gettext} and is deliberately left untested.
 */
public class CustomTest {

    @Test
    public void freshCustomHasNullPath() {
        assertNull(new Custom().getPath());
    }

    @Test
    public void pathRoundTrips() {
        Custom c = new Custom();
        VariablePath p = new VariablePath();
        p.setPath("/palettes/custom.xml");

        c.setPath(p);
        assertSame(p, c.getPath());

        c.setPath(null);
        assertNull(c.getPath());
    }

    @Test
    public void isALeafPaletteInTheTree() {
        Custom c = new Custom();
        assertTrue(c.isLeaf());
        assertFalse(c.getAllowsChildren());
        assertEquals(0, c.getChildCount());
    }

    @Test
    public void treeAccessorsReturnNeutralValues() {
        Custom c = new Custom();
        // Palette returns null children and a null child regardless of index
        assertNull(c.children());
        assertNull(c.getChildAt(0));
        assertNull(c.getChildAt(-1));
        assertNull(c.getChildAt(999));
        // getIndex ignores its argument and always answers 0 for a leaf
        assertEquals(0, c.getIndex(new Custom()));
        assertEquals(0, c.getIndex(null));
    }

    @Test
    public void isAPalettePaletteNodeAndTreeNode() {
        Custom c = new Custom();
        assertTrue(c instanceof Palette);
        assertTrue(c instanceof PaletteNode);
        assertTrue(c instanceof TreeNode);
    }

    @Test
    public void inheritedNameEnableParentRoundTrip() {
        Custom c = new Custom();
        assertNull(c.getName());
        assertFalse(c.isEnable());
        assertNull(c.getParent());

        Category parent = new Category();
        c.setName("MyPalette");
        c.setEnable(true);
        c.setParent(parent);

        assertEquals("MyPalette", c.getName());
        assertTrue(c.isEnable());
        assertSame(parent, c.getParent());
    }

    @Test
    public void toStringOfNullNameIsNull() {
        assertNull(new Custom().toString());
    }

    @Test
    public void toStringOfEmptyNameIsEmpty() {
        Custom c = new Custom();
        c.setName("");
        assertEquals("", c.toString());
    }

    /**
     * Behaviour characterization: a {@link Palette} leaf inherits the base
     * {@link PaletteNode#afterUnmarshal} hook, which records the JAXB parent as
     * the tree parent — the counterpart to
     * {@link CategoryTest#afterUnmarshalDoesNotSetOwnParent_characterization()},
     * where {@link Category} overrides the hook and skips this step.
     */
    @Test
    public void afterUnmarshalSetsParentFromBaseHook() {
        Category parent = new Category();
        Custom child = new Custom();
        child.afterUnmarshal(null, parent);
        assertSame(parent, child.getParent());
    }

    @Test
    public void afterUnmarshalWithNullParentLeavesParentNull() {
        Custom child = new Custom();
        child.afterUnmarshal(null, null);
        assertNull(child.getParent());
    }
}
