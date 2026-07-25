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

package org.scilab.forge.scirenderer.texture;

import java.awt.Dimension;

/**
 * A hermetic {@link TextureDrawer} test helper. It has a fixed size, a
 * configurable origin position, and a no-op {@link #draw} so it can be rendered
 * headlessly into a {@link TextureBufferedImage} without touching any GPU or
 * display resources.
 */
final class NoOpTextureDrawer implements TextureDrawer {

    private final Dimension size;
    private final OriginPosition origin;

    NoOpTextureDrawer(Dimension size) {
        this(size, OriginPosition.UPPER_LEFT);
    }

    NoOpTextureDrawer(Dimension size, OriginPosition origin) {
        this.size = size;
        this.origin = origin;
    }

    @Override
    public void draw(TextureDrawingTools textureDrawingTools) {
        // Intentionally empty: exercises the accept()/reDraw() plumbing without
        // asserting on any drawn pixels.
    }

    @Override
    public Dimension getTextureSize() {
        return size;
    }

    @Override
    public OriginPosition getOriginPosition() {
        return origin;
    }
}
