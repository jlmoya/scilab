/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Scilab Enterprises
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 *
 */

package org.scilab.modules.graphic_export;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Hermetic unit tests for {@link ExportParams}, the immutable-ish value holder
 * carrying compression quality, page orientation and the antialiasing flag.
 *
 * These exercise the orientation constants, both constructors and the
 * {@code setParamsOnGraphics} side-effect on a headless {@link Graphics2D}
 * obtained from an in-memory {@link BufferedImage} (no display required).
 */
public class ExportParamsTest {

    /** A throwaway headless Graphics2D backed by a 1x1 image. */
    private static Graphics2D newHeadlessGraphics() {
        return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
    }

    @Test
    public void orientationConstantsHaveExpectedValues() {
        assertEquals(0, ExportParams.PORTRAIT);
        assertEquals(1, ExportParams.LANDSCAPE);
        assertNotEquals(ExportParams.PORTRAIT, ExportParams.LANDSCAPE);
    }

    @Test
    public void defaultConstructorUsesDocumentedDefaults() {
        ExportParams p = new ExportParams();
        assertEquals(-1f, p.compressionQuality, 0f);
        assertEquals(ExportParams.PORTRAIT, p.orientation);
        assertFalse(p.antialiasing);
    }

    @Test
    public void fullConstructorStoresEveryField() {
        ExportParams p = new ExportParams(0.75f, ExportParams.LANDSCAPE, true);
        assertEquals(0.75f, p.compressionQuality, 0f);
        assertEquals(ExportParams.LANDSCAPE, p.orientation);
        assertTrue(p.antialiasing);
    }

    @Test
    public void fullConstructorPreservesArbitraryValues() {
        // The constructor is a plain field-copy: it neither clamps nor validates.
        ExportParams p = new ExportParams(2.0f, 99, false);
        assertEquals(2.0f, p.compressionQuality, 0f);
        assertEquals(99, p.orientation);
        assertFalse(p.antialiasing);
    }

    @Test
    public void publicFieldsAreMutable() {
        ExportParams p = new ExportParams();
        p.compressionQuality = 0.5f;
        p.orientation = ExportParams.LANDSCAPE;
        p.antialiasing = true;
        assertEquals(0.5f, p.compressionQuality, 0f);
        assertEquals(ExportParams.LANDSCAPE, p.orientation);
        assertTrue(p.antialiasing);
    }

    @Test
    public void setParamsOnGraphicsTurnsAntialiasingOnWhenEnabled() {
        ExportParams p = new ExportParams(-1f, ExportParams.PORTRAIT, true);
        Graphics2D g = newHeadlessGraphics();
        try {
            p.setParamsOnGraphics(g);
            assertEquals(RenderingHints.VALUE_ANTIALIAS_ON,
                         g.getRenderingHint(RenderingHints.KEY_ANTIALIASING));
        } finally {
            g.dispose();
        }
    }

    @Test
    public void setParamsOnGraphicsTurnsAntialiasingOffWhenDisabled() {
        ExportParams p = new ExportParams(-1f, ExportParams.PORTRAIT, false);
        Graphics2D g = newHeadlessGraphics();
        try {
            // Pre-set the hint ON to prove setParamsOnGraphics actively writes OFF.
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            p.setParamsOnGraphics(g);
            assertEquals(RenderingHints.VALUE_ANTIALIAS_OFF,
                         g.getRenderingHint(RenderingHints.KEY_ANTIALIASING));
        } finally {
            g.dispose();
        }
    }

    @Test
    public void setParamsOnGraphicsIsIdempotentForTheAntialiasingHint() {
        ExportParams p = new ExportParams(-1f, ExportParams.PORTRAIT, true);
        Graphics2D g = newHeadlessGraphics();
        try {
            p.setParamsOnGraphics(g);
            p.setParamsOnGraphics(g);
            assertEquals(RenderingHints.VALUE_ANTIALIAS_ON,
                         g.getRenderingHint(RenderingHints.KEY_ANTIALIASING));
        } finally {
            g.dispose();
        }
    }
}
