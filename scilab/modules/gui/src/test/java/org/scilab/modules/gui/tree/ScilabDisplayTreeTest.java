/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Jose Moya
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 *
 */

package org.scilab.modules.gui.tree;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link ScilabDisplayTree}.
 *
 * <p>Only the two pure, side-effect-free static helpers are exercised:
 * {@code treeShaping(String[])} (computes per-node depth + parent id from a
 * flat "position/label/icon/callback" quadruple stream) and
 * {@code getTreeDepth(String[][])} (the maximum depth over the shaped tree).
 *
 * <p>The remaining methods — {@code scilabDisplayTree},
 * {@code uicontrolScilabDisplayTree}, {@code createGraphicTree} and
 * {@code createTree} — construct live {@code ScilabTree} Swing widgets
 * (AWT {@code Toolkit}, icon loading, callback creation, {@code ScilabBridge})
 * and therefore need the GUI/native runtime; they are intentionally not
 * covered here.
 */
class ScilabDisplayTreeTest {

    // ------------------------------------------------------------------
    // treeShaping — depth + parent computation
    // ------------------------------------------------------------------

    /**
     * The data layout is groups of four cells:
     * [position id, label, icon path, callback]. Only the position id (every
     * 4th cell, starting at 0) gets a computed depth (column 1) and parent
     * (column 2). A dot-free root id has depth 0 and the literal parent
     * "root".
     */
    @Test
    void singleRootNodeShaping() {
        String[] data = {"1", "label", "icon", "cb"};

        String[][] shape = ScilabDisplayTree.treeShaping(data);

        assertEquals(4, shape.length);
        assertEquals(3, shape[0].length);
        // position slot: depth 0, parent "root"
        assertArrayEquals(new String[] {"1", "0", "root"}, shape[0]);
        // the other three cells keep their raw value in column 0, null elsewhere
        assertArrayEquals(new String[] {"label", null, null}, shape[1]);
        assertArrayEquals(new String[] {"icon", null, null}, shape[2]);
        assertArrayEquals(new String[] {"cb", null, null}, shape[3]);
    }

    /**
     * Depth is (number of dot-separated tokens - 1); parent is the substring
     * before the last dot.
     */
    @Test
    void depthAndParentComputedFromDottedIds() {
        String[] data = {
            "1", "r", "", "",
            "1.1", "c", "", "",
            "1.1.2", "g", "", ""
        };

        String[][] shape = ScilabDisplayTree.treeShaping(data);

        assertArrayEquals(new String[] {"1", "0", "root"}, shape[0]);
        assertArrayEquals(new String[] {"1.1", "1", "1"}, shape[4]);
        assertArrayEquals(new String[] {"1.1.2", "2", "1.1"}, shape[8]);
    }

    /** Parent is everything before the LAST dot, not the first. */
    @Test
    void parentIsPrefixBeforeLastDot() {
        String[] data = {"a.b.c.d", "x", "y", "z"};

        String[][] shape = ScilabDisplayTree.treeShaping(data);

        // 4 tokens -> depth 3 ; prefix before last '.' -> "a.b.c"
        assertArrayEquals(new String[] {"a.b.c.d", "3", "a.b.c"}, shape[0]);
    }

    /** A multi-character dot-free id is still depth 0 / "root". */
    @Test
    void dotFreeIdIsRoot() {
        String[] data = {"topnode", "l", "i", "c"};

        String[][] shape = ScilabDisplayTree.treeShaping(data);

        assertEquals("0", shape[0][1]);
        assertEquals("root", shape[0][2]);
    }

    /** Column 0 always mirrors the raw input, for every cell (not just slots). */
    @Test
    void columnZeroPreservesEveryRawCell() {
        String[] data = {"1", "a", "b", "c", "1.1", "d", "e", "f"};

        String[][] shape = ScilabDisplayTree.treeShaping(data);

        assertEquals(data.length, shape.length);
        for (int i = 0; i < data.length; i++) {
            assertEquals(data[i], shape[i][0], "column 0 mismatch at " + i);
            assertEquals(3, shape[i].length, "row width at " + i);
        }
        // non-position cells carry no depth/parent
        assertNull(shape[1][1]);
        assertNull(shape[1][2]);
        assertNull(shape[5][1]);
        assertNull(shape[5][2]);
    }

    /** Empty input yields an empty (but well-formed) shape, not an error. */
    @Test
    void emptyInputYieldsEmptyShape() {
        String[][] shape = ScilabDisplayTree.treeShaping(new String[0]);

        assertEquals(0, shape.length);
    }

    /**
     * Defect characterization: an empty position id makes the token count 0,
     * so depth becomes -1 and the code takes the "has parent" branch, where
     * {@code "".substring(0, -1)} blows up. Documents that empty node ids are
     * not tolerated.
     */
    @Test
    void emptyPositionIdThrowsIndexOutOfBounds() {
        String[] data = {""};

        assertThrows(IndexOutOfBoundsException.class,
                     () -> ScilabDisplayTree.treeShaping(data));
    }

    /**
     * Defect characterization: a lone "." tokenizes to zero tokens (depth -1),
     * and {@code ".".substring(0, 0)} is the empty string — so this survives
     * where "" throws, producing depth "-1" and an empty parent id.
     */
    @Test
    void loneDotIdYieldsNegativeDepthAndEmptyParent() {
        String[] data = {".", "l", "i", "c"};

        String[][] shape = ScilabDisplayTree.treeShaping(data);

        assertEquals("-1", shape[0][1]);
        assertEquals("", shape[0][2]);
    }

    // ------------------------------------------------------------------
    // getTreeDepth — maximum depth over the shaped tree
    // ------------------------------------------------------------------

    /** No rows -> depth 0. */
    @Test
    void getTreeDepthOfEmptyArrayIsZero() {
        assertEquals(0, ScilabDisplayTree.getTreeDepth(new String[0][3]));
    }

    /**
     * Defect characterization: {@code getTreeDepth} only inspects positions at
     * indices that are multiples of four AND strictly greater than zero — so a
     * single-node (length-4) shaped tree always reports depth 0, even if the
     * root row itself carries a larger depth string.
     */
    @Test
    void getTreeDepthIgnoresIndexZeroRow() {
        String[][] shaped = new String[4][3];
        shaped[0][1] = "9"; // deliberately large; index 0 is skipped (i > 0)

        assertEquals(0, ScilabDisplayTree.getTreeDepth(shaped));
    }

    /** Reads the depth cells at indices 4, 8, ... and returns the maximum. */
    @Test
    void getTreeDepthReturnsMaximumOverPositionSlots() {
        String[][] shaped = new String[12][3];
        shaped[4][1] = "1";
        shaped[8][1] = "2";

        assertEquals(2, ScilabDisplayTree.getTreeDepth(shaped));
    }

    /** The maximum is order-independent (not simply the last slot's value). */
    @Test
    void getTreeDepthTakesMaxNotLast() {
        String[][] shaped = new String[12][3];
        shaped[4][1] = "3";
        shaped[8][1] = "1";

        assertEquals(3, ScilabDisplayTree.getTreeDepth(shaped));
    }

    /**
     * Only the multiple-of-four depth cells are parsed; garbage in the other
     * cells is never read, so it neither contributes nor throws.
     */
    @Test
    void getTreeDepthIgnoresNonSlotCells() {
        String[][] shaped = new String[8][3];
        shaped[1][1] = "zzz";
        shaped[2][1] = "zzz";
        shaped[3][1] = "zzz";
        shaped[5][1] = "zzz";
        shaped[6][1] = "zzz";
        shaped[7][1] = "zzz";
        shaped[4][1] = "1";

        assertEquals(1, ScilabDisplayTree.getTreeDepth(shaped));
    }

    /**
     * Defect characterization: a non-numeric depth cell at a real position
     * slot propagates a {@link NumberFormatException} out of {@code parseInt}.
     */
    @Test
    void getTreeDepthThrowsOnNonNumericSlot() {
        String[][] shaped = new String[8][3];
        shaped[4][1] = "notANumber";

        assertThrows(NumberFormatException.class,
                     () -> ScilabDisplayTree.getTreeDepth(shaped));
    }

    // ------------------------------------------------------------------
    // treeShaping + getTreeDepth end-to-end
    // ------------------------------------------------------------------

    /** Shaping a genuine 3-level tree then measuring it reports depth 2. */
    @Test
    void shapeThenDepthEndToEnd() {
        String[] data = {
            "1", "root", "", "",
            "1.1", "child", "", "",
            "1.1.1", "grandchild", "", ""
        };

        String[][] shape = ScilabDisplayTree.treeShaping(data);

        assertEquals(2, ScilabDisplayTree.getTreeDepth(shape));
    }
}
