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

package org.scilab.forge.scirenderer.implementation.vulkan.texture;

import org.scilab.forge.scirenderer.texture.AbstractTexture;

/**
 * Vulkan texture: all wrap/filter/data-provider plumbing comes from {@link AbstractTexture}; this
 * only adds the GPU-side cache handle. The motor lazily uploads {@code getDataProvider().getData()}
 * to a Vulkan image on first use and marks it up-to-date; {@code dataUpdated()} (inherited) flips
 * {@code upToDate} back to false on a colormap / glyph change so the motor re-uploads.
 */
public class VulkanTexture extends AbstractTexture {

    /** Opaque handle into the GPU texture table owned by the scene renderer (0 = not uploaded). */
    private long gpuHandle;

    public long getGpuHandle() {
        return gpuHandle;
    }

    public void setGpuHandle(long handle) {
        this.gpuHandle = handle;
        this.upToDate = true;
    }

    /** Forget the GPU handle (after disposal) so a future use re-uploads. */
    public void clearGpuHandle() {
        this.gpuHandle = 0;
        this.upToDate = false;
    }

    public boolean isUpToDate() {
        return upToDate;
    }
}
