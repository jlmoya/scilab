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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.DoubleBuffer;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link ScilabDoubleReference}, the {@code DoubleBuffer}
 * backed double variant. Element access is column-major
 * ({@code index = i + nbRows*j}); a null / zero-capacity imaginary buffer means
 * real. Includes a defect-characterization test for {@code setElement}, which
 * writes the real argument into the imaginary buffer.
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
    public void getRealPartReadsBufferSequentially() {
        ScilabDoubleReference r = new ScilabDoubleReference("v", buf(1, 2, 3, 4), null, 2, 2);
        double[][] d = r.getRealPart();
        assertArrayEquals(new double[] {1.0, 2.0}, d[0], EPS);
        assertArrayEquals(new double[] {3.0, 4.0}, d[1], EPS);
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
        assertArrayEquals(new double[] {5.0, 6.0}, im[0], EPS);
        assertArrayEquals(new double[] {7.0, 8.0}, im[1], EPS);
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
    public void setElementWritesRealArgumentIntoImaginaryBuffer_defect() {
        // Documented defect: setElement(i, j, x, y) stores x into BOTH buffers,
        // so the imaginary component ends up as x (the real argument), never y.
        ScilabDoubleReference r = new ScilabDoubleReference("v", buf(1, 2, 3, 4), buf(5, 6, 7, 8), 2, 2);
        r.setElement(0, 0, 3.0, 9.0);
        assertEquals(3.0, r.getRealElement(0, 0), EPS);
        assertEquals(3.0, r.getImaginaryElement(0, 0), EPS); // y (9.0) is ignored
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
