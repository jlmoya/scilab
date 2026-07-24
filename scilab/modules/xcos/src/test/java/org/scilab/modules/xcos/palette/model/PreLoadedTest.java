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

import java.lang.reflect.Modifier;
import java.util.List;

import javax.swing.tree.TreeNode;
import javax.xml.bind.annotation.XmlTransient;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link PreLoaded} and its nested
 * {@link PreLoaded.Dynamic} subtype.
 *
 * <p>
 * A {@code PreLoaded} is a {@link Palette} leaf that owns a live list of
 * {@link PaletteBlock}s. Crucially the blocks are <em>not</em> exposed as
 * {@link TreeNode} children — the palette stays a tree leaf — so the block list
 * and the tree accessors are checked independently. Everything runs on plain
 * in-memory objects; no native runtime is required.
 *
 * <p>
 * The inherited {@link PaletteNode#toString()} is exercised only for the
 * {@code null} and empty name cases; a non-empty name routes through the native
 * {@code Messages.gettext} and is deliberately left untested.
 */
public class PreLoadedTest {

    @Test
    public void freshPreLoadedHasEmptyLiveBlockList() {
        PreLoaded p = new PreLoaded();
        List<PaletteBlock> blocks = p.getBlock();
        assertNotNull(blocks);
        assertTrue(blocks.isEmpty());
    }

    @Test
    public void getBlockReturnsSameLiveListAcrossCalls() {
        PreLoaded p = new PreLoaded();
        List<PaletteBlock> first = p.getBlock();
        first.add(new PaletteBlock());
        List<PaletteBlock> second = p.getBlock();
        assertSame(first, second, "getBlock() must expose the live list, not a snapshot");
        assertEquals(1, second.size());
    }

    @Test
    public void isALeafPaletteInTheTree() {
        PreLoaded p = new PreLoaded();
        assertTrue(p.isLeaf());
        assertFalse(p.getAllowsChildren());
        assertEquals(0, p.getChildCount());
        assertNull(p.children());
        assertNull(p.getChildAt(0));
        assertEquals(0, p.getIndex(new PreLoaded()));
    }

    /**
     * The palette's {@link PaletteBlock}s are model data, not tree children: a
     * populated block list leaves the {@link TreeNode} view of the palette a
     * childless leaf.
     */
    @Test
    public void blocksAreNotExposedAsTreeChildren() {
        PreLoaded p = new PreLoaded();
        p.getBlock().add(new PaletteBlock());
        p.getBlock().add(new PaletteBlock());

        assertEquals(2, p.getBlock().size());
        assertEquals(0, p.getChildCount());
        assertTrue(p.isLeaf());
    }

    @Test
    public void isAPalettePaletteNodeAndTreeNode() {
        PreLoaded p = new PreLoaded();
        assertTrue(p instanceof Palette);
        assertTrue(p instanceof PaletteNode);
        assertTrue(p instanceof TreeNode);
    }

    @Test
    public void inheritedNameEnableParentRoundTrip() {
        PreLoaded p = new PreLoaded();
        assertNull(p.getName());
        assertFalse(p.isEnable());
        assertNull(p.getParent());

        Category parent = new Category();
        p.setName("Sources");
        p.setEnable(true);
        p.setParent(parent);

        assertEquals("Sources", p.getName());
        assertTrue(p.isEnable());
        assertSame(parent, p.getParent());
    }

    @Test
    public void toStringOfNullNameIsNull() {
        assertNull(new PreLoaded().toString());
    }

    @Test
    public void toStringOfEmptyNameIsEmpty() {
        PreLoaded p = new PreLoaded();
        p.setName("");
        assertEquals("", p.toString());
    }

    // ---- the nested Dynamic subtype ----

    @Test
    public void dynamicIsAPreLoadedPalette() {
        PreLoaded.Dynamic d = new PreLoaded.Dynamic();
        assertTrue(d instanceof PreLoaded);
        assertTrue(d instanceof Palette);
    }

    @Test
    public void dynamicHasItsOwnLiveBlockList() {
        PreLoaded.Dynamic d = new PreLoaded.Dynamic();
        assertNotNull(d.getBlock());
        assertTrue(d.getBlock().isEmpty());
        d.getBlock().add(new PaletteBlock());
        assertEquals(1, d.getBlock().size());
    }

    /**
     * A plain {@link PreLoaded} is not a {@link PreLoaded.Dynamic}. The
     * distinction is load-bearing: {@link Category#beforeMarshal} strips only
     * {@code Dynamic} palettes, and {@code PaletteNode.checkRemoving} forbids
     * removing a non-dynamic {@code PreLoaded}.
     */
    @Test
    public void plainPreLoadedIsNotDynamic() {
        assertFalse(new PreLoaded() instanceof PreLoaded.Dynamic);
    }

    /**
     * The nested {@link PreLoaded.Dynamic} palette is deliberately excluded from
     * marshalling; its {@link XmlTransient} marker is part of the public
     * contract.
     */
    @Test
    public void dynamicIsAnnotatedXmlTransient() {
        assertTrue(PreLoaded.Dynamic.class.isAnnotationPresent(XmlTransient.class));
    }

    @Test
    public void dynamicIsAStaticNestedClass() {
        assertTrue(Modifier.isStatic(PreLoaded.Dynamic.class.getModifiers()));
    }
}
