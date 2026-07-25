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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link ScilabIntegerReference}, the {@code java.nio}
 * buffer-backed integer variant. Element access is column-major
 * ({@code index = i + nbRows*j}); the precision arms of {@code getRawData} /
 * {@code getCorrectData} are covered for all four widths. Equality against a
 * plain {@link ScilabInteger} is exercised via the shared column-major engine.
 */
public class ScilabIntegerReferenceTest {

    // Column-major buffer for the logical 2x2 matrix {{10,30},{20,40}}.
    private static ByteBuffer int8Buffer() {
        return ByteBuffer.wrap(new byte[] {10, 20, 30, 40});
    }

    @Test
    public void defaultConstructorIsEmpty() {
        ScilabIntegerReference r = new ScilabIntegerReference();
        assertTrue(r.isEmpty());
        assertEquals(0, r.getHeight());
        assertEquals(0, r.getWidth());
    }

    @Test
    public void byteReferenceDimensionsAndFlags() {
        ScilabIntegerReference r = new ScilabIntegerReference("v", int8Buffer(), 2, 2, false);
        assertFalse(r.isEmpty());
        assertEquals(2, r.getHeight());
        assertEquals(2, r.getWidth());
        assertEquals("v", r.getVarName());
        assertTrue(r.isReference());
        assertFalse(r.isSwaped());
        assertEquals(ScilabIntegerTypeEnum.sci_int8, r.getPrec());
        assertFalse(r.isUnsigned());
    }

    @Test
    public void byteElementAccessIsColumnMajor() {
        ScilabIntegerReference r = new ScilabIntegerReference("v", int8Buffer(), 2, 2, false);
        // index = i + nbRows*j
        assertEquals(10, r.getByteElement(0, 0));
        assertEquals(20, r.getByteElement(1, 0));
        assertEquals(30, r.getByteElement(0, 1));
        assertEquals(40, r.getByteElement(1, 1));
    }

    @Test
    public void setByteElementWritesThroughToBuffer() {
        ScilabIntegerReference r = new ScilabIntegerReference("v", int8Buffer(), 2, 2, false);
        r.setByteElement(0, 1, (byte) 99);
        assertEquals(99, r.getByteElement(0, 1));
    }

    @Test
    public void getDataAsByteReadsBufferSequentially() {
        // setBuffer fills each row from consecutive buffer slots, so [10,20,30,40]
        // reconstructs as {{10,20},{30,40}} (distinct from the column-major getByteElement view).
        ScilabIntegerReference r = new ScilabIntegerReference("v", int8Buffer(), 2, 2, false);
        byte[][] d = r.getDataAsByte();
        assertArrayEquals(new byte[] {10, 20}, d[0]);
        assertArrayEquals(new byte[] {30, 40}, d[1]);
    }

    @Test
    public void setDataWritesSequentiallyAndUpdatesPrecision() {
        ScilabIntegerReference r = new ScilabIntegerReference("v", int8Buffer(), 2, 2, false);
        r.setData(new byte[][] {{1, 2}, {3, 4}}, true);
        assertEquals(ScilabIntegerTypeEnum.sci_uint8, r.getPrec());
        assertTrue(r.isUnsigned());
        // setPart wrote [1,2,3,4]; column-major get reads them back in that order.
        assertEquals(1, r.getByteElement(0, 0));
        assertEquals(2, r.getByteElement(1, 0));
        assertEquals(3, r.getByteElement(0, 1));
        assertEquals(4, r.getByteElement(1, 1));
    }

    @Test
    public void rawDataIsTheBufferAndCorrectDataIsAMatrix() {
        ScilabIntegerReference r = new ScilabIntegerReference("v", int8Buffer(), 2, 2, false);
        assertInstanceOf(ByteBuffer.class, r.getRawData());
        assertInstanceOf(byte[][].class, r.getCorrectData());
    }

    @Test
    public void equalsPlainIntegerViaColumnMajorEngine() {
        // The column-major buffer [10,20,30,40] represents {{10,30},{20,40}}.
        ScilabIntegerReference ref = new ScilabIntegerReference("v", int8Buffer(), 2, 2, false);
        ScilabInteger plain = new ScilabInteger(new byte[][] {{10, 30}, {20, 40}}, false);
        assertEquals(ref, plain);
        ScilabInteger different = new ScilabInteger(new byte[][] {{10, 30}, {20, 99}}, false);
        assertNotEquals(ref, different);
    }

    @Test
    public void shortReferencePrecisionAndAccess() {
        ShortBuffer buf = ShortBuffer.wrap(new short[] {100, 200, 300, 400});
        ScilabIntegerReference r = new ScilabIntegerReference("v", buf, 2, 2, true);
        assertEquals(ScilabIntegerTypeEnum.sci_uint16, r.getPrec());
        assertTrue(r.isUnsigned());
        assertEquals(300, r.getShortElement(0, 1));
        assertInstanceOf(ShortBuffer.class, r.getRawData());
        assertInstanceOf(short[][].class, r.getCorrectData());
    }

    @Test
    public void intReferencePrecisionAndAccess() {
        IntBuffer buf = IntBuffer.wrap(new int[] {1000, 2000, 3000, 4000});
        ScilabIntegerReference r = new ScilabIntegerReference("v", buf, 2, 2, false);
        assertEquals(ScilabIntegerTypeEnum.sci_int32, r.getPrec());
        assertEquals(3000, r.getIntElement(0, 1));
        r.setIntElement(1, 1, 12345);
        assertEquals(12345, r.getIntElement(1, 1));
        assertInstanceOf(IntBuffer.class, r.getRawData());
    }

    @Test
    public void longReferencePrecisionAndAccess() {
        LongBuffer buf = LongBuffer.wrap(new long[] {1L, 2L, 3L, 4L});
        ScilabIntegerReference r = new ScilabIntegerReference("v", buf, 2, 2, true);
        assertEquals(ScilabIntegerTypeEnum.sci_uint64, r.getPrec());
        assertEquals(3L, r.getLongElement(0, 1));
        assertInstanceOf(LongBuffer.class, r.getRawData());
        assertInstanceOf(long[][].class, r.getCorrectData());
    }
}
