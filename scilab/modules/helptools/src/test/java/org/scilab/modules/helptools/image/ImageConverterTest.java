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

import java.awt.Component;
import java.awt.Graphics;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import javax.swing.Icon;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Hermetic unit tests for {@link ImageConverter}'s pure static helpers:
 * {@code imageExists} (missing-image detection) and {@code convertIconToPNG}
 * (headless icon rasterisation).
 *
 * <p>Only the static surface is exercised: instantiating {@code ImageConverter}
 * initialises a {@code jakarta.activation.MimetypesFileTypeMap}, which resolves a
 * runtime SPI provider that is not on the unit-test classpath (the activation
 * <em>impl</em> jar is intentionally not a build dependency of this module) — so
 * the registry and md5-cache instance methods cannot be covered here without a
 * live runtime. Everything below runs with just temp files, no Scilab, no display.
 */
public class ImageConverterTest {

    /** A minimal, headless-safe icon: it declares a size but paints nothing. */
    private static final class BlankIcon implements Icon {
        public int getIconWidth() {
            return 3;
        }
        public int getIconHeight() {
            return 4;
        }
        public void paintIcon(Component c, Graphics g, int x, int y) {
            // intentionally empty
        }
    }

    // ---- imageExists (static) ------------------------------------------

    @Test
    public void imageExistsReturnsNullWhenRelativeImageIsPresent(@TempDir Path dir) throws IOException {
        File pic = new File(dir.toFile(), "pic.png");
        assertTrue(pic.createNewFile());
        assertNull(ImageConverter.imageExists(dir.toString(), "pic.png"),
                   "an existing image must report null (i.e. nothing missing)");
    }

    @Test
    public void imageExistsReturnsTheExpectedPathWhenMissing(@TempDir Path dir) {
        File missing = ImageConverter.imageExists(dir.toString(), "missing.png");
        assertNotNull(missing);
        assertEquals("missing.png", missing.getName());
    }

    @Test
    public void imageExistsHonoursAbsolutePaths(@TempDir Path dir) throws IOException {
        File pic = new File(dir.toFile(), "abs.png");
        assertTrue(pic.createNewFile());
        // Absolute path that exists => the "path" argument is ignored and null is returned.
        assertNull(ImageConverter.imageExists("/no/such/base", pic.getAbsolutePath()));
    }

    // ---- convertIconToPNG (static, headless) ---------------------------

    @Test
    public void convertIconToPNGWritesANonEmptyPngFile(@TempDir Path dir) {
        File out = new File(dir.toFile(), "icon.png");
        assertTrue(ImageConverter.convertIconToPNG(new BlankIcon(), out));
        assertTrue(out.isFile());
        assertTrue(out.length() > 0, "a PNG should have been written");
    }
}
