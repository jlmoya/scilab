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

package org.scilab.forge.scirenderer.implementation.g2d.motor;

import org.junit.jupiter.api.Test;
import org.scilab.forge.scirenderer.shapes.appearance.Appearance;

import java.awt.BasicStroke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hermetic unit tests for {@link G2DStroke}, the {@link BasicStroke} subclass used by the
 * Graphics2D backend. {@code BasicStroke} is a pure geometry description (no display), so its
 * construction and the {@code getStroke(...)} factory can be tested directly.
 */
public class G2DStrokeTest {

    @Test
    public void isABasicStroke() {
        assertTrue(new G2DStroke(1, null, 0) instanceof BasicStroke);
    }

    @Test
    public void constructorFixesButtCapAndMiterJoin() {
        G2DStroke stroke = new G2DStroke(2.5f, new float[] {1, 2}, 0.5f);
        assertEquals(2.5f, stroke.getLineWidth());
        assertEquals(0.5f, stroke.getDashPhase());
        assertArrayEquals(new float[] {1, 2}, stroke.getDashArray());
        assertEquals(BasicStroke.CAP_BUTT, stroke.getEndCap());
        assertEquals(BasicStroke.JOIN_MITER, stroke.getLineJoin());
        assertEquals(10.0f, stroke.getMiterLimit());
    }

    @Test
    public void nullAppearanceYieldsTheSharedBasicStroke() {
        // A null appearance falls back to the default (width 1, solid pattern), which returns
        // the cached BASIC instance regardless of the dash phase argument.
        G2DStroke a = G2DStroke.getStroke(null, 0);
        G2DStroke b = G2DStroke.getStroke(null, 5);
        assertSame(a, b);
        assertEquals(1.0f, a.getLineWidth());
        assertNull(a.getDashArray());
        assertEquals(BasicStroke.CAP_BUTT, a.getEndCap());
        assertEquals(BasicStroke.JOIN_MITER, a.getLineJoin());
    }

    @Test
    public void defaultAppearanceAlsoYieldsTheSharedBasicStroke() {
        G2DStroke basic = G2DStroke.getStroke(null, 0);
        assertSame(basic, G2DStroke.getStroke(new Appearance(), 0));
    }

    @Test
    public void zeroWidthProducesADistinctSolidZeroWidthStroke() {
        Appearance appearance = new Appearance();
        appearance.setLineWidth(0);
        G2DStroke stroke = G2DStroke.getStroke(appearance, 0);

        assertEquals(0.0f, stroke.getLineWidth());
        assertNull(stroke.getDashArray());
        assertNotSame(G2DStroke.getStroke(null, 0), stroke);
    }

    @Test
    public void solidPatternWithNonUnitWidthProducesADashlessStroke() {
        Appearance appearance = new Appearance();
        appearance.setLineWidth(3);
        // Default pattern is 0xFFFF (== -1 as a short) => "solid".
        G2DStroke stroke = G2DStroke.getStroke(appearance, 0);

        assertEquals(3.0f, stroke.getLineWidth());
        assertNull(stroke.getDashArray());
    }

    @Test
    public void nonSolidPatternProducesADashArrayScaledByTheWidth() {
        Appearance appearance = new Appearance();
        appearance.setLineWidth(2);
        appearance.setLinePattern((short) 0xF0F0);
        G2DStroke stroke = G2DStroke.getStroke(appearance, 0);

        assertEquals(2.0f, stroke.getLineWidth());
        float[] dash = stroke.getDashArray();
        assertNotNull(dash, "a non-solid line pattern must decode into a dash array");
        // 0xF0F0 decodes to equal on/off runs, each scaled by the width factor (2).
        assertArrayEquals(new float[] {8.0f, 8.0f}, dash);
    }
}
