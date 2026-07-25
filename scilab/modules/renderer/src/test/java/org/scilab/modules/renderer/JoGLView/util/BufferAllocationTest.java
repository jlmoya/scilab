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

package org.scilab.modules.renderer.JoGLView.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link BufferAllocation}, the direct-NIO buffer
 * factory. It delegates sizing to JOGL's {@code GLBuffers}, which allocate
 * plain direct buffers with no GL context, so the happy-path allocation and
 * the free-memory guard both run without a display.
 *
 * <p>The {@code OutOfMemoryException} branch is not force-triggered here: it
 * fires only when the JVM heap is genuinely near exhaustion, which cannot be
 * arranged deterministically without destabilising the test JVM. Everything
 * else - element sizing, directness, independence of successive buffers - is
 * exercised.
 */
class BufferAllocationTest {

    @Test
    void newByteBufferHasTheRequestedCapacityAndIsDirect() throws OutOfMemoryException {
        ByteBuffer b = BufferAllocation.newByteBuffer(64);
        assertEquals(64, b.capacity(), "one byte per requested unit");
        assertEquals(64, b.limit());
        assertEquals(0, b.position());
        assertTrue(b.isDirect(), "GLBuffers must hand back a direct buffer");
    }

    @Test
    void newIntBufferCapacityIsCountedInIntsNotBytes() throws OutOfMemoryException {
        // The length argument is an element count: 10 ints, not 10 bytes.
        IntBuffer b = BufferAllocation.newIntBuffer(10);
        assertEquals(10, b.capacity());
        assertEquals(10, b.limit());
        assertTrue(b.isDirect());
    }

    @Test
    void newFloatBufferCapacityIsCountedInFloats() throws OutOfMemoryException {
        FloatBuffer b = BufferAllocation.newFloatBuffer(7);
        assertEquals(7, b.capacity());
        assertEquals(7, b.limit());
        assertTrue(b.isDirect());
    }

    @Test
    void zeroLengthAllocationsAreEmptyButValid() throws OutOfMemoryException {
        assertEquals(0, BufferAllocation.newByteBuffer(0).capacity());
        assertEquals(0, BufferAllocation.newIntBuffer(0).capacity());
        assertEquals(0, BufferAllocation.newFloatBuffer(0).capacity());
    }

    @Test
    void successiveAllocationsAreIndependentInstances() throws OutOfMemoryException {
        ByteBuffer a = BufferAllocation.newByteBuffer(32);
        ByteBuffer b = BufferAllocation.newByteBuffer(32);
        assertNotSame(a, b, "each call must allocate a fresh buffer");
        a.put(0, (byte) 0x7F);
        assertEquals((byte) 0x00, b.get(0), "writing one buffer must not touch the other");
    }

    @Test
    void writtenContentReadsBackFromAFloatBuffer() throws OutOfMemoryException {
        FloatBuffer b = BufferAllocation.newFloatBuffer(3);
        b.put(new float[] {1.5f, -2.5f, 3.0f});
        b.rewind();
        assertEquals(1.5f, b.get(), 0.0f);
        assertEquals(-2.5f, b.get(), 0.0f);
        assertEquals(3.0f, b.get(), 0.0f);
    }

    @Test
    void classIsAFinalUtilityWithAPrivateConstructor() throws Exception {
        assertTrue(Modifier.isFinal(BufferAllocation.class.getModifiers()),
                   "utility class should be final");
        Constructor<BufferAllocation> ctor = BufferAllocation.class.getDeclaredConstructor();
        assertTrue(Modifier.isPrivate(ctor.getModifiers()),
                   "the utility class must hide its constructor");
    }
}
