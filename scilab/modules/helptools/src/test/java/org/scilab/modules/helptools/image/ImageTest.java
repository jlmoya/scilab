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

package org.scilab.modules.helptools.image;

import static org.junit.jupiter.api.Assertions.*;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link Image}, a plain public-field value holder that
 * carries an {@link Icon} plus its measured geometry (width/height/ascent/descent).
 *
 * <p>Constructing a bare {@link ImageIcon} (or passing {@code null}) touches no
 * display, so these stay hermetic and headless-safe.
 */
public class ImageTest {

    @Test
    public void storesAllConstructorValues() {
        Icon icon = new ImageIcon();
        Image img = new Image(icon, 640, 480, 12, 3);

        assertSame(icon, img.icon);
        assertEquals(640, img.width);
        assertEquals(480, img.height);
        assertEquals(12, img.ascent);
        assertEquals(3, img.descent);
    }

    @Test
    public void acceptsNullIcon() {
        Image img = new Image(null, 1, 2, 0, 0);
        assertNull(img.icon);
        assertEquals(1, img.width);
        assertEquals(2, img.height);
    }

    @Test
    public void performsNoValidationOnGeometry() {
        // The class is a dumb holder: negative / zero dimensions are stored verbatim.
        Image img = new Image(null, -5, 0, -1, -2);
        assertEquals(-5, img.width);
        assertEquals(0, img.height);
        assertEquals(-1, img.ascent);
        assertEquals(-2, img.descent);
    }

    @Test
    public void fieldsAreMutable() {
        Image img = new Image(null, 10, 10, 1, 1);
        img.width = 99;
        img.icon = new ImageIcon();
        assertEquals(99, img.width);
        assertNotNull(img.icon);
    }
}
