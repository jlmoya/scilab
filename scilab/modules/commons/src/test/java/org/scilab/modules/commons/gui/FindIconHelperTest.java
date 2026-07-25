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

package org.scilab.modules.commons.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;
import javax.swing.Icon;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Hermetic unit tests for {@link FindIconHelper}.
 *
 * <p>The class walks the on-disk icon theme tree rooted at {@code $SCI} (surefire points
 * {@code SCI} at the reactor root). These tests never assert against those shipped icons —
 * that would be brittle — but instead pin the <em>deterministic</em> contract:
 * <ul>
 *   <li>null / empty / {@code <html>} names never resolve;</li>
 *   <li>an unknown name yields {@code null} when {@code defaultValue == false} and the fixed
 *       {@code .../gui/images/icons/16x16/status/error.png} sentinel when {@code true};</li>
 *   <li>a caller-registered theme directory (via {@link FindIconHelper#addThemePath}) makes a
 *       freshly-created icon discoverable through {@code findIcon} / {@code findImage} /
 *       {@code loadIcon}.</li>
 * </ul>
 * Everything runs headless: only {@link BufferedImage}/{@code ImageIO} (no live display) and
 * plain files under a JUnit {@code @TempDir} are used. {@code FindIconHelper} does not touch the
 * crashing {@code ScilabCommons} native layer, so class initialization is safe.
 */
public class FindIconHelperTest {

    private static final String ERROR_ICON_SUFFIX = "/modules/gui/images/icons/16x16/status/error.png";

    // ----------------------------------------------------------------- non-resolving inputs

    @Test
    public void nullNameNeverResolves() {
        assertNull(FindIconHelper.findIcon(null));
    }

    @Test
    public void emptyNameNeverResolves() {
        assertNull(FindIconHelper.findIcon(""));
        assertNull(FindIconHelper.findIcon("", false));
        assertNull(FindIconHelper.findIcon("", "24x24", false));
    }

    @Test
    public void htmlMarkupNamesAreRejectedByTheLookup() {
        // The <html> guard in lookupIcon short-circuits; with no fallback and defaultValue=false
        // the result is null.
        assertNull(FindIconHelper.findIcon("<html>bold</html>", false));
    }

    // ----------------------------------------------------------------- unknown-icon contract

    @Test
    public void unknownIconWithoutDefaultReturnsNull() {
        assertNull(FindIconHelper.findIcon("no_such_icon_zzz_123", false));
        assertNull(FindIconHelper.findIcon("no_such_icon_zzz_123", "32x32", false));
    }

    @Test
    public void unknownIconWithDefaultReturnsTheErrorSentinel() {
        // Every default-valued overload funnels to the same fixed 16x16 error icon.
        assertTrue(FindIconHelper.findIcon("no_such_icon_zzz_123").endsWith(ERROR_ICON_SUFFIX));
        assertTrue(FindIconHelper.findIcon("no_such_icon_zzz_123", true).endsWith(ERROR_ICON_SUFFIX));
        assertTrue(FindIconHelper.findIcon("no_such_icon_zzz_123", "48x48").endsWith(ERROR_ICON_SUFFIX));
        assertTrue(FindIconHelper.findIcon("no_such_icon_zzz_123", "48x48", true).endsWith(ERROR_ICON_SUFFIX));
    }

    // ----------------------------------------------------------------- findImage

    @Test
    public void findImageReturnsTheAbsolutePathOfAnExistingFile(@TempDir Path tmp) throws IOException {
        File img = tmp.resolve("existing.png").toFile();
        Files.write(img.toPath(), new byte[] {1, 2, 3});

        String resolved = FindIconHelper.findImage(img.getAbsolutePath());
        assertEquals(img.getAbsolutePath(), resolved);
    }

    @Test
    public void findImageFallsBackToNullForAnUnknownImageWhenNoDefault() {
        assertNull(FindIconHelper.findImage("totally_absent_image_zzz.png", false));
    }

    @Test
    public void findImageResolvesAgainstARegisteredThemeDirectory(@TempDir Path tmp) throws IOException {
        File icon = tmp.resolve("registered.png").toFile();
        Files.write(icon.toPath(), new byte[] {0});
        FindIconHelper.addThemePath(tmp.toString());

        // findImage tries "<themeBase>/<image>" for every registered base.
        String resolved = FindIconHelper.findImage("registered.png", false);
        assertNotNull(resolved);
        assertTrue(resolved.endsWith("registered.png"));
        assertTrue(new File(resolved).exists());
    }

    // ----------------------------------------------------------------- addThemePath + findIcon

    @Test
    public void addThemePathMakesAFreshIconDiscoverableAndIsIdempotent(@TempDir Path tmp) throws IOException {
        File icon = tmp.resolve("customicon.png").toFile();
        Files.write(icon.toPath(), new byte[] {0});

        // Registering twice must exercise (and survive) the "already present" early return.
        FindIconHelper.addThemePath(tmp.toString());
        FindIconHelper.addThemePath(tmp.toString());

        String resolved = FindIconHelper.findIcon("customicon", false);
        assertNotNull(resolved, "an icon under a registered theme path must resolve");
        assertTrue(resolved.endsWith("customicon.png"));
        assertTrue(new File(resolved).exists());
    }

    // ----------------------------------------------------------------- loadIcon

    @Test
    public void loadIconDecodesARegisteredImageIntoAnIcon(@TempDir Path tmp) throws IOException {
        // A genuine (headless-safe) 1x1 PNG so ImageIO.read succeeds.
        BufferedImage bimg = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        bimg.setRGB(0, 0, 0xFF112233);
        File png = tmp.resolve("pixel.png").toFile();
        assertTrue(ImageIO.write(bimg, "png", png), "the test PNG must be writable");
        FindIconHelper.addThemePath(tmp.toString());

        Icon icon = FindIconHelper.loadIcon("pixel");
        assertNotNull(icon);
        assertEquals(1, icon.getIconWidth());
        assertEquals(1, icon.getIconHeight());
    }

    @Test
    public void loadIconThrowsIOExceptionForAnUnresolvableName() {
        // findIcon(...,false) -> null -> empty path -> ImageIO.read(new File("")) fails.
        assertThrows(IOException.class, () -> FindIconHelper.loadIcon("no_such_icon_zzz_123"));
    }
}
