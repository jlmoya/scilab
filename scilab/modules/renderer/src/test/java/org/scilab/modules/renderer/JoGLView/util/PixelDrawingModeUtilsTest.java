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
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.scilab.forge.scirenderer.implementation.jogl.JoGLPixelDrawingMode;
import org.scilab.modules.graphic_objects.figure.Figure.PixelDrawingMode;

/**
 * Hermetic unit tests for {@link PixelDrawingModeUtils#figureToJoGLmode},
 * a pure enum-to-enum translation between the graphic-object model's
 * {@link PixelDrawingMode} and scirenderer's {@link JoGLPixelDrawingMode}.
 * Both enums are simple constant lists, so nothing here needs a GL context.
 */
class PixelDrawingModeUtilsTest {

    @Test
    void everyFigureModeMapsToASameOrdinalJoglMode() {
        // The two enums are declared in identical order, so an exhaustive
        // ordinal check pins the whole translation table without listing
        // all sixteen cases by hand.
        for (PixelDrawingMode mode : PixelDrawingMode.values()) {
            JoGLPixelDrawingMode mapped = PixelDrawingModeUtils.figureToJoGLmode(mode);
            assertNotNull(mapped, "no JoGL mode for " + mode);
            assertEquals(mode.ordinal(), mapped.ordinal(),
                         "ordinal mismatch translating " + mode);
        }
    }

    @Test
    void spotChecksNamedMappings() {
        assertEquals(JoGLPixelDrawingMode.CLEAR, PixelDrawingModeUtils.figureToJoGLmode(PixelDrawingMode.CLEAR));
        assertEquals(JoGLPixelDrawingMode.AND_REVERSE, PixelDrawingModeUtils.figureToJoGLmode(PixelDrawingMode.ANDREVERSE));
        assertEquals(JoGLPixelDrawingMode.AND_INVERTED, PixelDrawingModeUtils.figureToJoGLmode(PixelDrawingMode.ANDINVERTED));
        assertEquals(JoGLPixelDrawingMode.OR_REVERSE, PixelDrawingModeUtils.figureToJoGLmode(PixelDrawingMode.ORREVERSE));
        assertEquals(JoGLPixelDrawingMode.COPY_INVERTED, PixelDrawingModeUtils.figureToJoGLmode(PixelDrawingMode.COPYINVERTED));
        assertEquals(JoGLPixelDrawingMode.OR_INVERTED, PixelDrawingModeUtils.figureToJoGLmode(PixelDrawingMode.ORINVERTED));
        assertEquals(JoGLPixelDrawingMode.NAND, PixelDrawingModeUtils.figureToJoGLmode(PixelDrawingMode.NAND));
        assertEquals(JoGLPixelDrawingMode.SET, PixelDrawingModeUtils.figureToJoGLmode(PixelDrawingMode.SET));
    }

    @Test
    void translationIsATotalCoverageOfTheSixteenModes() {
        assertEquals(16, PixelDrawingMode.values().length);
        for (PixelDrawingMode mode : PixelDrawingMode.values()) {
            assertNotNull(PixelDrawingModeUtils.figureToJoGLmode(mode));
        }
    }
}
