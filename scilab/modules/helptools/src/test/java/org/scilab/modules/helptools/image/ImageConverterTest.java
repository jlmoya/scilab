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
import java.nio.file.Files;
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

    // ---- copyImageFile (static) ----------------------------------------

    @Test
    public void copyImageFileCreatesDestinationWithIdenticalContent(@TempDir Path dir) throws IOException {
        File src = new File(dir.toFile(), "src.png");
        byte[] payload = {1, 2, 3, 4, 5};
        Files.write(src.toPath(), payload);

        File destDir = new File(dir.toFile(), "dest");
        assertTrue(destDir.mkdir());

        ImageConverter.copyImageFile(src, destDir.getAbsolutePath());

        File copied = new File(destDir, "src.png");
        assertTrue(copied.isFile(), "the image must be copied into the destination directory");
        assertArrayEquals(payload, Files.readAllBytes(copied.toPath()));
    }

    @Test
    public void copyImageFileSkipsWhenDestinationIsNewer(@TempDir Path dir) throws IOException {
        File src = new File(dir.toFile(), "src.png");
        Files.write(src.toPath(), new byte[] {9, 9});

        File destDir = new File(dir.toFile(), "dest");
        assertTrue(destDir.mkdir());
        File dest = new File(destDir, "src.png");
        byte[] original = {7, 7, 7};
        Files.write(dest.toPath(), original);

        // Destination strictly newer than the source => the copy is a no-op.
        assertTrue(src.setLastModified(1_000L));
        assertTrue(dest.setLastModified(5_000_000L));

        ImageConverter.copyImageFile(src, destDir.getAbsolutePath());

        assertArrayEquals(original, Files.readAllBytes(dest.toPath()),
                          "an up-to-date destination must not be overwritten");
    }

    @Test
    public void copyImageFileOverwritesWhenSourceIsNewer(@TempDir Path dir) throws IOException {
        File src = new File(dir.toFile(), "src.png");
        byte[] fresh = {4, 2};
        Files.write(src.toPath(), fresh);

        File destDir = new File(dir.toFile(), "dest");
        assertTrue(destDir.mkdir());
        File dest = new File(destDir, "src.png");
        Files.write(dest.toPath(), new byte[] {0, 0, 0, 0});

        // Source strictly newer => the stale destination is refreshed.
        assertTrue(dest.setLastModified(1_000L));
        assertTrue(src.setLastModified(5_000_000L));

        ImageConverter.copyImageFile(src, destDir.getAbsolutePath());

        assertArrayEquals(fresh, Files.readAllBytes(dest.toPath()));
    }
}
