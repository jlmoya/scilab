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
        // setPart writes rows sequentially: [1.5, 2.5, 3.5, 4.5].
        assertArrayEquals(new double[] {1.5, 2.5, 3.5, 4.5}, buffer.array(), 0.0);

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
        assertArrayEquals(new int[] {10, 20, 30, 40}, buffer.array());

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
}
