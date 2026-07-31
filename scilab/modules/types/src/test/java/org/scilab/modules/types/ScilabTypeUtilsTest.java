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

package org.scilab.modules.types;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for the package-private {@link ScilabTypeUtils} equality
 * engine and its buffer &lt;-&gt; matrix copy helpers. These are the routines
 * behind {@code ScilabDouble/Boolean/Integer.equals}; here they are exercised
 * directly, including the column-major buffer-vs-matrix comparison.
 */
public class ScilabTypeUtilsTest {

    @Test
    public void equalsDoubleMatrixToMatrix() {
        double[][] a = {{1.0, 2.0}, {3.0, 4.0}};
        double[][] same = {{1.0, 2.0}, {3.0, 4.0}};
        double[][] different = {{1.0, 2.0}, {3.0, 9.0}};
        assertTrue(ScilabTypeUtils.equalsDouble(a, false, same, false));
        assertFalse(ScilabTypeUtils.equalsDouble(a, false, different, false));
    }

    @Test
    public void equalsDoubleBufferToMatrixIsColumnMajor() {
        // Matrix is 2 rows x 3 cols; the buffer stores it column-major:
        // index = row + rows*col, i.e. [ (0,0),(1,0),(0,1),(1,1),(0,2),(1,2) ].
        double[][] matrix = {{1.0, 2.0, 3.0}, {4.0, 5.0, 6.0}};
        DoubleBuffer columnMajor = DoubleBuffer.wrap(new double[] {1.0, 4.0, 2.0, 5.0, 3.0, 6.0});
        assertTrue(ScilabTypeUtils.equalsDouble(columnMajor, false, matrix, false));

        DoubleBuffer wrongOrder = DoubleBuffer.wrap(new double[] {1.0, 2.0, 3.0, 4.0, 5.0, 6.0});
        assertFalse(ScilabTypeUtils.equalsDouble(wrongOrder, false, matrix, false));
    }

    @Test
    public void equalsDoubleBufferToBuffer() {
        DoubleBuffer a = DoubleBuffer.wrap(new double[] {1.0, 2.0, 3.0});
        DoubleBuffer sameContent = DoubleBuffer.wrap(new double[] {1.0, 2.0, 3.0});
        DoubleBuffer other = DoubleBuffer.wrap(new double[] {1.0, 2.0, 4.0});
        assertTrue(ScilabTypeUtils.equalsDouble(a, false, sameContent, false));
        assertFalse(ScilabTypeUtils.equalsDouble(a, false, other, false));
    }

    @Test
    public void equalsIntegerByteMatrices() {
        byte[][] a = {{1, 2}, {3, 4}};
        byte[][] same = {{1, 2}, {3, 4}};
        byte[][] different = {{1, 2}, {3, 5}};
        assertTrue(ScilabTypeUtils.equalsInteger(a, false, same, false));
        assertFalse(ScilabTypeUtils.equalsInteger(a, false, different, false));
    }

    @Test
    public void equalsIntegerIntBufferToMatrixIsColumnMajor() {
        int[][] matrix = {{10, 20}, {30, 40}};
        // column-major: [ (0,0),(1,0),(0,1),(1,1) ] = [10, 30, 20, 40].
        IntBuffer columnMajor = IntBuffer.wrap(new int[] {10, 30, 20, 40});
        assertTrue(ScilabTypeUtils.equalsInteger(columnMajor, false, matrix, false));
        IntBuffer wrong = IntBuffer.wrap(new int[] {10, 20, 30, 40});
        assertFalse(ScilabTypeUtils.equalsInteger(wrong, false, matrix, false));
    }

    @Test
    public void equalsBooleanMatrices() {
        boolean[][] a = {{true, false}, {false, true}};
        boolean[][] same = {{true, false}, {false, true}};
        boolean[][] different = {{true, true}, {false, true}};
        assertTrue(ScilabTypeUtils.equalsBoolean(a, false, same, false));
        assertFalse(ScilabTypeUtils.equalsBoolean(a, false, different, false));
    }

    @Test
    public void setPartThenSetBufferRoundTripsDoubles() {
        double[][] part = {{1.5, 2.5}, {3.5, 4.5}};
        DoubleBuffer buffer = DoubleBuffer.allocate(4);
        ScilabTypeUtils.setPart(buffer, part);
        // setPart writes COLUMN-MAJOR (index i + rows*j), Scilab's own storage
        // order and the layout every per-element reference accessor uses:
        // {{1.5, 2.5}, {3.5, 4.5}} flattens to [1.5, 3.5, 2.5, 4.5]. An earlier
        // revision pinned the row-major bulk write here — that was register
        // B23(b), the defect that made whole-matrix accessors return the
        // transpose, not a contract.
        assertArrayEquals(new double[] {1.5, 3.5, 2.5, 4.5}, buffer.array(), 0.0);

        double[][] recovered = new double[2][2];
        ScilabTypeUtils.setBuffer(recovered, buffer);
        assertArrayEquals(part[0], recovered[0], 0.0);
        assertArrayEquals(part[1], recovered[1], 0.0);
    }

    @Test
    public void setPartThenSetBufferRoundTripsInts() {
        int[][] part = {{10, 20}, {30, 40}};
        IntBuffer buffer = IntBuffer.allocate(4);
        ScilabTypeUtils.setPart(buffer, part);
        // Column-major, as for the double overload above.
        assertArrayEquals(new int[] {10, 30, 20, 40}, buffer.array());

        int[][] recovered = new int[2][2];
        ScilabTypeUtils.setBuffer(recovered, buffer);
        assertArrayEquals(part[0], recovered[0]);
        assertArrayEquals(part[1], recovered[1]);
    }

    @Test
    public void setPartIsNoOpWhenBufferTooSmall() {
        // Guard: r*c (4) must fit the capacity (2); otherwise nothing is written.
        byte[][] part = {{7, 8}, {9, 10}};
        ByteBuffer tooSmall = ByteBuffer.allocate(2);
        ScilabTypeUtils.setPart(tooSmall, part);
        assertArrayEquals(new byte[] {0, 0}, tooSmall.array());
    }

    // ----- swaped (row-major) equality paths -----

    @Test
    public void equalsDoubleSwapedBufferIsRowMajor() {
        // When the data is flagged swaped, the buffer is compared row-by-row:
        // buffer must be data flattened row-major.
        double[][] data = {{1.0, 2.0, 3.0}, {4.0, 5.0, 6.0}};
        DoubleBuffer rowMajor = DoubleBuffer.wrap(new double[] {1.0, 2.0, 3.0, 4.0, 5.0, 6.0});
        assertTrue(ScilabTypeUtils.equalsDouble(rowMajor, false, data, true));

        DoubleBuffer wrong = DoubleBuffer.wrap(new double[] {1.0, 2.0, 3.0, 4.0, 5.0, 7.0});
        assertFalse(ScilabTypeUtils.equalsDouble(wrong, false, data, true));
    }

    @Test
    public void equalsDoubleMatrixFirstDispatchDelegatesToBuffer() {
        // buffer arg is the matrix, data arg is the buffer: the matrix-first overloads
        // just swap and reuse the buffer engine.
        double[][] matrix = {{1.0, 2.0}, {3.0, 4.0}};
        DoubleBuffer columnMajor = DoubleBuffer.wrap(new double[] {1.0, 3.0, 2.0, 4.0});
        assertTrue(ScilabTypeUtils.equalsDouble(matrix, false, columnMajor, false));
    }

    @Test
    public void equalsIntegerByteSwapedBufferIsRowMajor() {
        byte[][] data = {{1, 2}, {3, 4}};
        ByteBuffer rowMajor = ByteBuffer.wrap(new byte[] {1, 2, 3, 4});
        assertTrue(ScilabTypeUtils.equalsInteger(rowMajor, false, data, true));
        ByteBuffer wrong = ByteBuffer.wrap(new byte[] {1, 2, 3, 9});
        assertFalse(ScilabTypeUtils.equalsInteger(wrong, false, data, true));
    }

    @Test
    public void equalsIntegerShortColumnMajorAndSwaped() {
        short[][] data = {{10, 20}, {30, 40}};
        // column-major
        ShortBuffer columnMajor = ShortBuffer.wrap(new short[] {10, 30, 20, 40});
        assertTrue(ScilabTypeUtils.equalsInteger(columnMajor, false, data, false));
        // swaped -> row-major
        ShortBuffer rowMajor = ShortBuffer.wrap(new short[] {10, 20, 30, 40});
        assertTrue(ScilabTypeUtils.equalsInteger(rowMajor, false, data, true));
    }

    @Test
    public void equalsIntegerIntSwapedBufferIsRowMajor() {
        int[][] data = {{10, 20}, {30, 40}};
        IntBuffer rowMajor = IntBuffer.wrap(new int[] {10, 20, 30, 40});
        assertTrue(ScilabTypeUtils.equalsInteger(rowMajor, false, data, true));
    }

    @Test
    public void equalsIntegerLongColumnMajorAndSwaped() {
        long[][] data = {{10L, 20L}, {30L, 40L}};
        LongBuffer columnMajor = LongBuffer.wrap(new long[] {10L, 30L, 20L, 40L});
        assertTrue(ScilabTypeUtils.equalsInteger(columnMajor, false, data, false));
        LongBuffer rowMajor = LongBuffer.wrap(new long[] {10L, 20L, 30L, 40L});
        assertTrue(ScilabTypeUtils.equalsInteger(rowMajor, false, data, true));
        LongBuffer wrong = LongBuffer.wrap(new long[] {10L, 20L, 30L, 99L});
        assertFalse(ScilabTypeUtils.equalsInteger(wrong, false, data, true));
    }

    @Test
    public void equalsIntegerMatrixFirstDispatchForShortAndLong() {
        short[][] shortData = {{1, 2}, {3, 4}};
        ShortBuffer shortBuf = ShortBuffer.wrap(new short[] {1, 3, 2, 4});
        assertTrue(ScilabTypeUtils.equalsInteger(shortData, false, shortBuf, false));

        long[][] longData = {{1L, 2L}, {3L, 4L}};
        LongBuffer longBuf = LongBuffer.wrap(new long[] {1L, 3L, 2L, 4L});
        assertTrue(ScilabTypeUtils.equalsInteger(longData, false, longBuf, false));
    }

    @Test
    public void equalsIntegerBufferToBuffer() {
        ByteBuffer a = ByteBuffer.wrap(new byte[] {1, 2, 3});
        ByteBuffer same = ByteBuffer.wrap(new byte[] {1, 2, 3});
        ByteBuffer other = ByteBuffer.wrap(new byte[] {1, 2, 4});
        assertTrue(ScilabTypeUtils.equalsInteger(a, false, same, false));
        assertFalse(ScilabTypeUtils.equalsInteger(a, false, other, false));
    }

    // ----- boolean equality across representations -----

    @Test
    public void equalsBooleanBufferToMatrixColumnMajorAndSwaped() {
        boolean[][] data = {{true, false}, {false, true}};
        // column-major: index i + rows*j -> [T,F,F,T] = [1,0,0,1]
        IntBuffer columnMajor = IntBuffer.wrap(new int[] {1, 0, 0, 1});
        assertTrue(ScilabTypeUtils.equalsBoolean(columnMajor, false, data, false));
        // swaped -> row-major: index j + cols*i -> [T,F,F,T] = [1,0,0,1] here too
        assertTrue(ScilabTypeUtils.equalsBoolean(IntBuffer.wrap(new int[] {1, 0, 0, 1}), false, data, true));
        // a genuine mismatch
        assertFalse(ScilabTypeUtils.equalsBoolean(IntBuffer.wrap(new int[] {1, 1, 0, 1}), false, data, false));
    }

    @Test
    public void equalsBooleanMatrixFirstAndBufferToBuffer() {
        boolean[][] data = {{true, false}, {false, true}};
        IntBuffer columnMajor = IntBuffer.wrap(new int[] {1, 0, 0, 1});
        // matrix-first dispatch
        assertTrue(ScilabTypeUtils.equalsBoolean(data, false, columnMajor, false));
        // buffer-to-buffer
        assertTrue(ScilabTypeUtils.equalsBoolean(IntBuffer.wrap(new int[] {1, 0}), false, IntBuffer.wrap(new int[] {1, 0}), false));
    }

    // ----- setBuffer / setPart round trips for the remaining primitive widths -----

    @Test
    public void setPartThenSetBufferRoundTripsBytes() {
        byte[][] part = {{1, 2}, {3, 4}};
        ByteBuffer buffer = ByteBuffer.allocate(4);
        ScilabTypeUtils.setPart(buffer, part);
        // Column-major (see the double overload's comment above).
        assertArrayEquals(new byte[] {1, 3, 2, 4}, buffer.array());

        byte[][] recovered = new byte[2][2];
        ScilabTypeUtils.setBuffer(recovered, buffer);
        assertArrayEquals(part[0], recovered[0]);
        assertArrayEquals(part[1], recovered[1]);
    }

    @Test
    public void setPartThenSetBufferRoundTripsShorts() {
        short[][] part = {{10, 20}, {30, 40}};
        ShortBuffer buffer = ShortBuffer.allocate(4);
        ScilabTypeUtils.setPart(buffer, part);
        // Column-major (see the double overload's comment above).
        assertArrayEquals(new short[] {10, 30, 20, 40}, buffer.array());

        short[][] recovered = new short[2][2];
        ScilabTypeUtils.setBuffer(recovered, buffer);
        assertArrayEquals(part[0], recovered[0]);
        assertArrayEquals(part[1], recovered[1]);
    }

    @Test
    public void setPartThenSetBufferRoundTripsLongs() {
        long[][] part = {{100L, 200L}, {300L, 400L}};
        LongBuffer buffer = LongBuffer.allocate(4);
        ScilabTypeUtils.setPart(buffer, part);
        // Column-major (see the double overload's comment above).
        assertArrayEquals(new long[] {100L, 300L, 200L, 400L}, buffer.array());

        long[][] recovered = new long[2][2];
        ScilabTypeUtils.setBuffer(recovered, buffer);
        assertArrayEquals(part[0], recovered[0]);
        assertArrayEquals(part[1], recovered[1]);
    }

    @Test
    public void setPartThenSetBufferRoundTripsBooleansColumnMajor() {
        // The boolean setPart/setBuffer pair stores column-major (index i + cols*j).
        boolean[][] part = {{true, false}, {false, true}};
        IntBuffer buffer = IntBuffer.allocate(4);
        ScilabTypeUtils.setPart(buffer, part);

        boolean[][] recovered = new boolean[2][2];
        ScilabTypeUtils.setBuffer(recovered, buffer);
        assertArrayEquals(part[0], recovered[0]);
        assertArrayEquals(part[1], recovered[1]);
    }

    @Test
    public void setBufferIsNoOpWhenBufferTooSmall() {
        // The double variant guards on r*c <= capacity, leaving the target untouched.
        double[][] target = {{9.0, 9.0}, {9.0, 9.0}};
        DoubleBuffer tooSmall = DoubleBuffer.allocate(2);
        ScilabTypeUtils.setBuffer(target, tooSmall);
        assertArrayEquals(new double[] {9.0, 9.0}, target[0], 0.0);
        assertArrayEquals(new double[] {9.0, 9.0}, target[1], 0.0);
    }
}
