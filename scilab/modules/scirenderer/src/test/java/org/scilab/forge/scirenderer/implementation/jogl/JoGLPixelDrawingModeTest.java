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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Hermetic unit tests for the {@link JoGLPixelDrawingMode} enum and its int mapping.
 * (This enum has no OpenGL dependency of its own.)
 */
public class JoGLPixelDrawingModeTest {

    @Test
    public void hasTheSixteenRasterOps() {
        assertEquals(16, JoGLPixelDrawingMode.values().length);
    }

    @Test
    public void intToEnumFollowsDeclarationOrder() {
        JoGLPixelDrawingMode[] all = JoGLPixelDrawingMode.values();
        for (int i = 0; i < all.length; i++) {
            assertSame(all[i], JoGLPixelDrawingMode.intToEnum(i), "index " + i);
        }
    }

    @Test
    public void spotCheckNamedOpcodes() {
        assertSame(JoGLPixelDrawingMode.CLEAR, JoGLPixelDrawingMode.intToEnum(0));
        assertSame(JoGLPixelDrawingMode.XOR, JoGLPixelDrawingMode.intToEnum(6));
        assertSame(JoGLPixelDrawingMode.SET, JoGLPixelDrawingMode.intToEnum(15));
    }

    @Test
    public void outOfRangeValuesMapToNull() {
        assertNull(JoGLPixelDrawingMode.intToEnum(16));
        assertNull(JoGLPixelDrawingMode.intToEnum(-1));
        assertNull(JoGLPixelDrawingMode.intToEnum(100));
    }

    @Test
    public void nullIntThrows() {
        // Switch on an Integer unboxes null => NullPointerException (documents current behavior).
        assertThrows(NullPointerException.class, () -> JoGLPixelDrawingMode.intToEnum(null));
    }
}
