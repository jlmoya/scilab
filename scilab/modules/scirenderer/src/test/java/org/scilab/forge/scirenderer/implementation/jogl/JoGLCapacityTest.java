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

package org.scilab.forge.scirenderer.implementation.jogl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;

/**
 * Hermetic unit tests for the default state of {@link JoGLCapacity}. This exercises
 * only the package-private constructor and the getters; the GL-context-dependent
 * {@code glReload} path is deliberately never invoked.
 */
public class JoGLCapacityTest {

    @Test
    public void defaultCapacityValues() {
        JoGLCapacity cap = new JoGLCapacity();
        assertEquals(8, cap.getLightNumber());
        assertEquals(6, cap.getClippingPlaneNumber());
        assertEquals(64, cap.getMaximumTextureSize());
        assertFalse(cap.isAccumulationBufferPresent());
        assertFalse(cap.isABRExtensionPresent());
    }

    @Test
    public void aliasedPointSizeRangeIsTwoZerosByDefault() {
        JoGLCapacity cap = new JoGLCapacity();
        float[] range = cap.getAliasedPointSizeRange();
        assertEquals(2, range.length);
        assertEquals(0f, range[0], 0f);
        assertEquals(0f, range[1], 0f);
    }

    @Test
    public void aliasedPointSizeRangeGetterIsDefensivelyCopied() {
        JoGLCapacity cap = new JoGLCapacity();
        float[] range = cap.getAliasedPointSizeRange();
        range[0] = 99f;
        assertNotSame(range, cap.getAliasedPointSizeRange());
        assertEquals(0f, cap.getAliasedPointSizeRange()[0], 0f, "internal state must not be mutated via the getter");
    }
}
