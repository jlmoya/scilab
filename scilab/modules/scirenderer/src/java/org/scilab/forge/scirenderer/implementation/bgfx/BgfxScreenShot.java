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

package org.scilab.forge.scirenderer.implementation.bgfx;

import org.lwjgl.bgfx.BGFXCallbackInterface;
import org.lwjgl.bgfx.BGFXCallbackVtbl;
import org.lwjgl.system.MemoryUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;

/**
 * A bgfx callback interface whose only non-trivial member is {@code screen_shot}: when bgfx delivers
 * a requested framebuffer capture, it is written to a PNG. This is the non-intrusive way to capture
 * the figure (only the bgfx framebuffer, never the desktop), and is the basis for a real
 * figure-to-image export.
 *
 * <p>The {@link BGFXCallbackInterface}/{@link BGFXCallbackVtbl} and the native closures are held for
 * the lifetime of bgfx (referenced from {@link BgfxCanvas}); they are intentionally not freed (the
 * capture path is opt-in QA, and bgfx may reference them until shutdown).
 */
final class BgfxScreenShot {

    private final BGFXCallbackVtbl vtbl;
    private final BGFXCallbackInterface iface;

    BgfxScreenShot() {
        vtbl = BGFXCallbackVtbl.calloc();
        vtbl.fatal((thisPtr, filePath, line, code, str) ->
                   System.err.println("[bgfx fatal] code=" + code + " " + MemoryUtil.memUTF8Safe(str)));
        vtbl.trace_vargs((thisPtr, filePath, line, format, argList) -> { });
        vtbl.profiler_begin((thisPtr, name, abgr, filePath, line) -> { });
        vtbl.profiler_begin_literal((thisPtr, name, abgr, filePath, line) -> { });
        vtbl.profiler_end(thisPtr -> { });
        vtbl.cache_read_size((thisPtr, id) -> 0);
        vtbl.cache_read((thisPtr, id, data, size) -> false);
        vtbl.cache_write((thisPtr, id, data, size) -> { });
        vtbl.screen_shot((thisPtr, filePath, width, height, pitch, data, size, yflip) ->
                         writePng(MemoryUtil.memUTF8(filePath), width, height, pitch, data, size, yflip));
        vtbl.capture_begin((thisPtr, width, height, pitch, format, yflip) -> { });
        vtbl.capture_end(thisPtr -> { });
        vtbl.capture_frame((thisPtr, data, size) -> { });

        iface = BGFXCallbackInterface.calloc();
        iface.vtbl(vtbl);
    }

    BGFXCallbackInterface iface() {
        return iface;
    }

    private static void writePng(String path, int width, int height, int pitch, long data, int size, boolean yflip) {
        try {
            final ByteBuffer buf = MemoryUtil.memByteBuffer(data, size);
            final BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < height; y++) {
                final int srcRow = yflip ? (height - 1 - y) : y;
                final int base = srcRow * pitch;
                for (int x = 0; x < width; x++) {
                    final int p = base + x * 4;          // BGRA8 on Metal
                    final int b = buf.get(p) & 0xff;
                    final int g = buf.get(p + 1) & 0xff;
                    final int r = buf.get(p + 2) & 0xff;
                    img.setRGB(x, y, (r << 16) | (g << 8) | b);
                }
            }
            ImageIO.write(img, "png", new File(path));
            System.out.println("[scirenderer.bgfx] wrote screenshot " + width + "x" + height + " -> " + path);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
