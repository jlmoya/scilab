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

import org.junit.jupiter.api.Test;
import org.scilab.forge.scirenderer.shapes.appearance.Color;

import java.awt.Font;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hermetic unit tests for the value-holder portions of {@link TextEntity}.
 * The font-metric methods (getLayout / getSize) are intentionally out of scope
 * for a hermetic test.
 */
public class TextEntityTest {

    @Test
    public void constructorStoresTextAndUsesADefaultFont() {
        TextEntity e = new TextEntity("hello");
        assertEquals("hello", e.getText());
        assertNotNull(e.getFont(), "a non-null default font is assigned");
    }

    @Test
    public void defaultFlagsAndColor() {
        TextEntity e = new TextEntity("x");
        assertTrue(e.isTextAntiAliased());
        assertTrue(e.isTextUseFractionalMetrics());
        assertEquals(TextEntity.DEFAULT_TEXT_COLOR, e.getTextColor());
        assertTrue(TextEntity.DEFAULT_TEXT_ANTI_ALIASED);
        assertTrue(TextEntity.DEFAULT_TEXT_USE_FRACTIONAL_METRICS);
    }

    @Test
    public void settersRoundTrip() {
        TextEntity e = new TextEntity("x");

        e.setText("world");
        assertEquals("world", e.getText());

        Font font = new Font("Dialog", Font.BOLD, 14);
        e.setFont(font);
        assertSame(font, e.getFont());

        Color color = new Color(0.1f, 0.2f, 0.3f);
        e.setTextColor(color);
        assertSame(color, e.getTextColor());

        e.setTextAntiAliased(false);
        assertFalse(e.isTextAntiAliased());

        e.setTextUseFractionalMetrics(false);
        assertFalse(e.isTextUseFractionalMetrics());
    }

    @Test
    public void isValidRequiresNonEmptyText() {
        assertTrue(new TextEntity("non-empty").isValid());
        assertFalse(new TextEntity("").isValid());
        assertFalse(new TextEntity(null).isValid());
    }

    @Test
    public void isValidRequiresANonNullFont() {
        TextEntity e = new TextEntity("text");
        assertTrue(e.isValid());
        e.setFont(null);
        assertFalse(e.isValid());
    }
}
