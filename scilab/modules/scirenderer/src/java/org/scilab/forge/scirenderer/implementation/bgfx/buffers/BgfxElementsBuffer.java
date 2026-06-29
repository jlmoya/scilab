/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Scilab macOS/2027 modernization
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 */

package org.scilab.forge.scirenderer.implementation.bgfx.buffers;

import org.scilab.forge.scirenderer.buffers.DataBuffer;
import org.scilab.forge.scirenderer.buffers.ElementsBuffer;

import java.nio.FloatBuffer;

/**
 * bgfx elements buffer: a client-side {@link FloatBuffer} of homogeneous vec4 elements.
 *
 * <p>Identical storage to the g2d backend's buffer (CPU-side); the bgfx backend uploads it to
 * transient GPU buffers at draw time. Elements are always padded to {@link #ELEMENT_SIZE} (x,y,z,w
 * / r,g,b,a).
 */
public class BgfxElementsBuffer implements DataBuffer, ElementsBuffer {

    /** The size of one element (homogeneous vec4). */
    public static final int ELEMENT_SIZE = 4;

    private static final float[] DEFAULT_VERTEX = new float[] {0, 0, 0, 1};

    private FloatBuffer data;
    private final Object mutex;

    /** Package-private: only {@link BgfxBuffersManager} instantiates this. */
    BgfxElementsBuffer() {
        mutex = new Object();
        data = null;
    }

    @Override
    public void setData(float[] newData, int elementSize) {
        if ((elementSize < 1) || (elementSize > ELEMENT_SIZE)) {
            throw new BadElementSizeException(elementSize, 1, ELEMENT_SIZE);
        }
        int verticesNumber = newData.length / elementSize;
        FloatBuffer buffer = FloatBuffer.allocate(ELEMENT_SIZE * verticesNumber);
        buffer.rewind();
        int k = 0;
        for (int i = 0; i < verticesNumber; i++) {
            for (int j = 0; j < ELEMENT_SIZE; j++) {
                if (j < elementSize) {
                    buffer.put(newData[k++]);
                } else {
                    buffer.put(DEFAULT_VERTEX[j]);
                }
            }
        }
        buffer.rewind();
        setData(buffer);
    }

    @Override
    public void setData(Float[] newData, int elementSize) {
        if ((elementSize < 1) || (elementSize > ELEMENT_SIZE)) {
            throw new BadElementSizeException(elementSize, 1, ELEMENT_SIZE);
        }
        int verticesNumber = newData.length / elementSize;
        FloatBuffer buffer = FloatBuffer.allocate(ELEMENT_SIZE * verticesNumber);
        buffer.rewind();
        int k = 0;
        for (int i = 0; i < verticesNumber; i++) {
            for (int j = 0; j < ELEMENT_SIZE; j++) {
                if (j < elementSize) {
                    buffer.put(newData[k++]);
                } else {
                    buffer.put(DEFAULT_VERTEX[j]);
                }
            }
        }
        buffer.rewind();
        setData(buffer);
    }

    @Override
    public void setData(FloatBuffer newData, int elementsSize) {
        if ((elementsSize < 1) || (elementsSize > ELEMENT_SIZE)) {
            throw new BadElementSizeException(elementsSize, 1, ELEMENT_SIZE);
        }
        if (elementsSize == ELEMENT_SIZE) {
            if (newData != null) {
                newData.rewind();
            }
            setData(newData);
            return;
        }
        int verticesNumber = newData.limit() / elementsSize;
        FloatBuffer buffer = FloatBuffer.allocate(ELEMENT_SIZE * verticesNumber);
        buffer.rewind();
        newData.rewind();
        for (int i = 0; i < verticesNumber; i++) {
            for (int j = 0; j < ELEMENT_SIZE; j++) {
                if (j < elementsSize) {
                    buffer.put(newData.get());
                } else {
                    buffer.put(DEFAULT_VERTEX[j]);
                }
            }
        }
        buffer.rewind();
        setData(buffer);
    }

    @Override
    public int getSize() {
        synchronized (mutex) {
            return data == null ? 0 : data.limit() / ELEMENT_SIZE;
        }
    }

    @Override
    public int getElementsSize() {
        return ELEMENT_SIZE;
    }

    @Override
    public FloatBuffer getData() {
        synchronized (mutex) {
            return data;
        }
    }

    private void setData(FloatBuffer data) {
        synchronized (mutex) {
            this.data = data;
        }
    }

    @Override
    public void clear() {
        if (data != null) {
            data.clear();
        }
        data = null;
    }

    private static class BadElementSizeException extends RuntimeException {
        BadElementSizeException(int size, int min, int max) {
            super("Bad vertex elements size : " + size + ". Should be in [" + min + ", " + (max - 1) + "]");
        }
    }
}
