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

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Hermetic unit tests for {@link ExportBitmap}. Both public methods drive the
 * pure-JDK {@link javax.imageio.ImageIO} pipeline, which works headless, so no
 * running Scilab / rendering stack is required — a plain in-memory
 * {@link BufferedImage} and a temp file are enough.
 *
 * {@code Export.*} status codes are compile-time constants (inlined), so this
 * test does not load the heavyweight {@code Export} class.
 */
public class ExportBitmapTest {

    @TempDir
    File tempDir;

    private static BufferedImage sampleImage(int type) {
        BufferedImage img = new BufferedImage(4, 4, type);
        img.getGraphics().setColor(Color.RED);
        img.getGraphics().fillRect(0, 0, 4, 4);
        return img;
    }

    @Test
    public void writeFileWritesAReadablePng() throws Exception {
        File out = new File(tempDir, "ok.png");
        assertTrue(out.createNewFile()); // writeFile requires an existing, writable file

        int ret = ExportBitmap.writeFile(sampleImage(BufferedImage.TYPE_INT_RGB), "png", out);

        assertEquals(Export.SUCCESS, ret);
        assertTrue(out.length() > 0, "png file should be non-empty");
        BufferedImage reread = ImageIO.read(out);
        assertNotNull(reread);
        assertEquals(4, reread.getWidth());
        assertEquals(4, reread.getHeight());
    }

    @Test
    public void writeFileReturnsIoExceptionErrorWhenTargetIsNotAFile() {
        // The file never gets created: file.isFile() is false, so the whole
        // write loop is skipped and the initial IOEXCEPTION_ERROR is returned.
        File missing = new File(tempDir, "does-not-exist.png");
        assertFalse(missing.exists());

        int ret = ExportBitmap.writeFile(sampleImage(BufferedImage.TYPE_INT_RGB), "png", missing);

        assertEquals(Export.IOEXCEPTION_ERROR, ret);
        assertFalse(missing.exists());
    }

    @Test
    public void writeFileReturnsNoWriterErrorForAnUnknownFormat() throws Exception {
        File out = new File(tempDir, "weird.dat");
        assertTrue(out.createNewFile());

        int ret = ExportBitmap.writeFile(sampleImage(BufferedImage.TYPE_INT_RGB), "no-such-format", out);

        assertEquals(Export.NOWRITER_ERROR, ret);
    }

    @Test
    public void writeJpegWritesAReadableJpeg() throws Exception {
        File out = new File(tempDir, "q.jpg");

        int ret = ExportBitmap.writeJPEG(sampleImage(BufferedImage.TYPE_INT_RGB), 0.9f, out);

        assertEquals(Export.SUCCESS, ret);
        assertTrue(out.length() > 0, "jpeg file should be non-empty");
        BufferedImage reread = ImageIO.read(out);
        assertNotNull(reread);
        assertEquals(4, reread.getWidth());
        assertEquals(4, reread.getHeight());
    }

    @Test
    public void writeJpegAcceptsBoundaryCompressionQualities() throws Exception {
        // The 0.0 (max compression) and 1.0 (max quality) boundaries must both
        // be accepted by the writer and produce valid, re-readable JPEGs.
        File lo = new File(tempDir, "lo.jpg");
        File hi = new File(tempDir, "hi.jpg");

        assertEquals(Export.SUCCESS, ExportBitmap.writeJPEG(sampleImage(BufferedImage.TYPE_INT_RGB), 0.0f, lo));
        assertEquals(Export.SUCCESS, ExportBitmap.writeJPEG(sampleImage(BufferedImage.TYPE_INT_RGB), 1.0f, hi));

        assertTrue(lo.length() > 0);
        assertTrue(hi.length() > 0);
        assertNotNull(ImageIO.read(lo));
        assertNotNull(ImageIO.read(hi));
    }
}
