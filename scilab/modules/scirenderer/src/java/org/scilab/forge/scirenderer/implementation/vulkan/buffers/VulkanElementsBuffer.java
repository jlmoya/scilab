/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Scilab / macOS 2027 fork
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 */

package org.scilab.forge.scirenderer.implementation.vulkan.buffers;

import java.nio.FloatBuffer;

import org.scilab.forge.scirenderer.buffers.ElementsBuffer;

/**
 * Vulkan backend element buffer: a plain CPU {@link FloatBuffer} holder of per-vertex data
 * (position / color / normal / texture-coordinate), read straight out by the {@code VulkanMotor}
 * when it packs the per-frame geometry arena. Backend-agnostic — no GPU state lives here; the GPU
 * upload happens in the motor.
 */
public class VulkanElementsBuffer implements ElementsBuffer {

    private FloatBuffer data;
    private int elementsSize = 4;

    @Override
    public void setData(float[] data, int elementSize) {
        setData(FloatBuffer.wrap(data), elementSize);
    }

    @Override
    public void setData(Float[] data, int elementSize) {
        float[] raw = new float[data.length];
        for (int i = 0; i < data.length; i++) {
            raw[i] = data[i];
        }
        setData(FloatBuffer.wrap(raw), elementSize);
    }

    @Override
    public void setData(FloatBuffer data, int elementsSize) {
        this.data = data;
        this.elementsSize = elementsSize;
    }

    @Override
    public FloatBuffer getData() {
        return data;
    }

    @Override
    public int getElementsSize() {
        return elementsSize;
    }

    @Override
    public int getSize() {
        return (data == null || elementsSize == 0) ? 0 : data.capacity() / elementsSize;
    }

    @Override
    public void clear() {
        data = null;
    }
}
