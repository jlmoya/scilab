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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.DoubleBuffer;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link ScilabDoubleReference}, the {@code DoubleBuffer}
 * backed double variant.
 *
 * The buffer holds the variable in Scilab's own storage order, column-major
 * ({@code index = i + nbRows*j}), and EVERY accessor must agree on that — the
 * per-element and whole-matrix families read the same memory. An earlier
 * revision of this file pinned the whole-matrix accessors' row-major bulk fill
 * as documented behaviour, adjacent to a test asserting column-major element
 * access on the same data: that contradiction was register B23(b), a square
 * matrix coming back exactly transposed. These tests now assert the one true
 * layout; a null / zero-capacity imaginary buffer means real, with the same
 * observable behaviour as a real-only by-value {@link ScilabDouble}.
 */
public class ScilabDoubleReferenceTest {

    private static final double EPS = 0.0;

    private static DoubleBuffer buf(double... values) {
        return DoubleBuffer.wrap(values);
    }

    @Test
    public void realReferenceIsRealWithNullImaginary() {
        ScilabDoubleReference r = new ScilabDoubleReference("v", buf(1, 2, 3, 4), null, 2, 2);
        assertTrue(r.isReal());
        assertFalse(r.isEmpty());
        assertEquals(2, r.getHeight());
        assertEquals(2, r.getWidth());
        assertEquals("v", r.getVarName());
        assertTrue(r.isReference());
    }

    @Test
    public void realElementAccessIsColumnMajor() {
        ScilabDoubleReference r = new ScilabDoubleReference("v", buf(1, 2, 3, 4), null, 2, 2);
        assertEquals(1.0, r.getRealElement(0, 0), EPS);
        assertEquals(2.0, r.getRealElement(1, 0), EPS);
        assertEquals(3.0, r.getRealElement(0, 1), EPS);
        assertEquals(4.0, r.getRealElement(1, 1), EPS);
    }

    @Test
    public void getRealPartAgreesWithElementAccess() {
        // Column-major [1,2,3,4] as 2x2 is {{1,3},{2,4}} — the same values the
        // per-element accessors return. The pre-B23 bulk fill produced the
        // transpose {{1,2},{3,4}}: right dimensions, all the right numbers, in
        // the wrong cells.
        ScilabDoubleReference r = new ScilabDoubleReference("v", buf(1, 2, 3, 4), null, 2, 2);
        double[][] d = r.getRealPart();
        assertArrayEquals(new double[] {1.0, 3.0}, d[0], EPS);
        assertArrayEquals(new double[] {2.0, 4.0}, d[1], EPS);
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                assertEquals(r.getRealElement(i, j), d[i][j], EPS);
            }
        }
    }

    @Test
    public void nonSquareGetRealPartAgreesWithElementAccess() {
        // 2x3: column-major [1..6] is {{1,3,5},{2,4,6}}. Non-square is the shape
        // where the old bulk fill was not even a transpose but a general
        // scramble, so it guards the indexing rather than just the orientation.
        ScilabDoubleReference r = new ScilabDoubleReference("v", buf(1, 2, 3, 4, 5, 6), null, 2, 3);
        double[][] d = r.getRealPart();
        assertArrayEquals(new double[] {1.0, 3.0, 5.0}, d[0], EPS);
        assertArrayEquals(new double[] {2.0, 4.0, 6.0}, d[1], EPS);
    }

    @Test
    public void setRealPartRoundTripsThroughElementAccess() {
        // The write side had the same row-major bulk defect as the read side; a
        // whole-matrix write must land where the per-element readers look.
        ScilabDoubleReference r = new ScilabDoubleReference("v", buf(0, 0, 0, 0, 0, 0), null, 2, 3);
        double[][] data = {{1.0, 3.0, 5.0}, {2.0, 4.0, 6.0}};
        r.setRealPart(data);
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 3; j++) {
                assertEquals(data[i][j], r.getRealElement(i, j), EPS);
            }
        }
    }

    @Test
    public void rawAndTypedBufferAccessorsExposeTheBackingBuffer() {
        DoubleBuffer real = buf(1, 2, 3, 4);
        DoubleBuffer imag = buf(5, 6, 7, 8);
        ScilabDoubleReference r = new ScilabDoubleReference("v", real, imag, 2, 2);
        assertSame(real, r.getRawRealPart());
        assertSame(imag, r.getRawImaginaryPart());
        assertSame(real, r.getRealBuffer());
        assertSame(imag, r.getImaginaryBuffer());
    }

    @Test
    public void complexReferenceImaginaryAccess() {
        ScilabDoubleReference r = new ScilabDoubleReference("v", buf(1, 2, 3, 4), buf(5, 6, 7, 8), 2, 2);
        assertFalse(r.isReal());
        assertEquals(7.0, r.getImaginaryElement(0, 1), EPS);
        double[][] im = r.getImaginaryPart();
        assertArrayEquals(new double[] {5.0, 7.0}, im[0], EPS);
        assertArrayEquals(new double[] {6.0, 8.0}, im[1], EPS);
    }

    @Test
    public void setRealAndImaginaryElementsWriteThrough() {
        ScilabDoubleReference r = new ScilabDoubleReference("v", buf(1, 2, 3, 4), buf(5, 6, 7, 8), 2, 2);
        r.setRealElement(1, 1, 40.0);
        assertEquals(40.0, r.getRealElement(1, 1), EPS);
        r.setImaginaryElement(0, 0, 50.0);
        assertEquals(50.0, r.getImaginaryElement(0, 0), EPS);
    }

    @Test
    public void setElementWritesRealAndImaginaryParts() {
        // setElement(i, j, x, y): x is the real component, y the imaginary —
        // the same contract as ScilabDouble.setElement. The pre-B23 body wrote
        // x into BOTH buffers, silently discarding y.
        ScilabDoubleReference r = new ScilabDoubleReference("v", buf(1, 2, 3, 4), buf(5, 6, 7, 8), 2, 2);
        r.setElement(0, 0, 3.0, 9.0);
        assertEquals(3.0, r.getRealElement(0, 0), EPS);
        assertEquals(9.0, r.getImaginaryElement(0, 0), EPS);
    }

    @Test
    public void realOnlyGetImaginaryPartIsEmptyLikeByValue() {
        // A real-only by-value ScilabDouble holds imaginaryPart = new double[0][]
        // and returns it. The reference must match — the pre-B23 body handed the
        // null buffer to a bulk fill, and under the engine's in-process SIGSEGV
        // handler that null dereference killed the whole JVM (register B23(a)).
        ScilabDoubleReference withNull = new ScilabDoubleReference("v", buf(1, 2, 3, 4), null, 2, 2);
        assertEquals(0, withNull.getImaginaryPart().length);
        ScilabDoubleReference withEmpty = new ScilabDoubleReference("v", buf(1, 2, 3, 4), DoubleBuffer.allocate(0), 2, 2);
        assertEquals(0, withEmpty.getImaginaryPart().length);
    }

    @Test
    public void realOnlyImaginaryElementAccessThrowsCatchably() {
        // By-value parity: ScilabDouble.getImaginaryElement on a real-only value
        // indexes new double[0][] and throws ArrayIndexOutOfBoundsException. The
        // reference must throw the SAME catchable class — never dereference the
        // null buffer, which is a process kill under the engine, not an NPE.
        ScilabDoubleReference r = new ScilabDoubleReference("v", buf(1, 2, 3, 4), null, 2, 2);
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> r.getImaginaryElement(0, 0));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> r.setImaginaryElement(0, 0, 1.0));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> r.setElement(0, 0, 1.0, 2.0));
    }

    @Test
    public void realOnlySetImaginaryPartThrowsIllegalState() {
        // A by-value ScilabDouble can become complex (the setter replaces the
        // field); a view cannot conjure an imaginary buffer in engine memory.
        // Loud beats a silent no-op — and beats the old null-dereference death.
        ScilabDoubleReference r = new ScilabDoubleReference("v", buf(1, 2, 3, 4), null, 2, 2);
        assertThrows(IllegalStateException.class, () -> r.setImaginaryPart(new double[][] {{1, 2}, {3, 4}}));
    }

    @Test
    public void zeroRowsIsEmpty() {
        ScilabDoubleReference r = new ScilabDoubleReference("v", DoubleBuffer.allocate(0), null, 0, 0);
        assertTrue(r.isEmpty());
        assertEquals(0, r.getHeight());
        assertEquals(0, r.getWidth());
    }

    @Test
    public void zeroCapacityImaginaryBufferCountsAsReal() {
        ScilabDoubleReference r = new ScilabDoubleReference("v", buf(1, 2, 3, 4), DoubleBuffer.allocate(0), 2, 2);
        assertTrue(r.isReal());
    }
}
