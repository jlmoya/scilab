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

package org.scilab.modules.graphic_export.convertToPPM;

import java.awt.image.ImageProducer;
import java.awt.image.MemoryImageSource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Hermetic unit tests for {@link PPMEncoder} — the ACME PPM (P6) writer.
 *
 * All tests are pure JDK: encoders write into a {@link ByteArrayOutputStream}
 * and, for the end-to-end case, a synchronous {@link MemoryImageSource} drives
 * the {@link ImageEncoder} pipeline on the calling thread (no display, no
 * Scilab).
 */
public class PPMEncoderTest {

    /** A tiny, valid producer used only to satisfy the encoder constructor. */
    private static ImageProducer dummyProducer() {
        return new MemoryImageSource(1, 1, new int[] {0}, 0, 1);
    }

    private static byte[] bytes(int... values) {
        byte[] b = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            b[i] = (byte) values[i];
        }
        return b;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] r = new byte[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

    @Test
    public void encodeStartWritesTheP6HeaderWithoutComments() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PPMEncoder enc = new PPMEncoder(dummyProducer(), baos);

        enc.encodeStart(2, 3);

        assertEquals("P6\n2 3\n255\n", baos.toString("UTF-8"));
    }

    @Test
    public void encodeStartEmitsCommentsBetweenMagicAndDimensions() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PPMEncoder enc = new PPMEncoder(dummyProducer(), baos);
        enc.addComment("hello");
        enc.addComment("world");

        enc.encodeStart(3, 4);

        assertEquals("P6\n# hello\n# world\n3 4\n255\n", baos.toString("UTF-8"));
    }

    @Test
    public void writeStringEmitsRawUtf8Bytes() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PPMEncoder enc = new PPMEncoder(dummyProducer(), baos);

        enc.writeString("abc\n");

        assertArrayEquals("abc\n".getBytes(StandardCharsets.UTF_8), baos.toByteArray());
    }

    @Test
    public void encodePixelsExtractsRgbBytesAndDropsAlpha() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PPMEncoder enc = new PPMEncoder(dummyProducer(), baos);

        // Two pixels on one row: opaque red then opaque green. Alpha (0xFF) is
        // ignored; only R,G,B are written, in that order, one byte each.
        int[] rgb = { 0xFFFF0000, 0xFF00FF00 };
        enc.encodePixels(0, 0, 2, 1, rgb, 0, 2);

        assertArrayEquals(bytes(0xFF, 0x00, 0x00, 0x00, 0xFF, 0x00), baos.toByteArray());
    }

    @Test
    public void encodePixelsHonoursOffsetAndScansize() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PPMEncoder enc = new PPMEncoder(dummyProducer(), baos);

        // A 3-wide backing buffer with a 1-column left margin (off=1) and a
        // trailing pixel per row that must be skipped: only the middle column of
        // each of the two rows (w=1) is encoded.
        int[] rgb = {
            0x11223344, 0xFF0A0B0C, 0x77777777,   // row 0: skip, take 0x0A0B0C, skip
            0x55667788, 0xFF010203, 0x99999999    // row 1: skip, take 0x010203, skip
        };
        enc.encodePixels(0, 0, 1, 2, rgb, 1, 3);

        assertArrayEquals(bytes(0x0A, 0x0B, 0x0C, 0x01, 0x02, 0x03), baos.toByteArray());
    }

    @Test
    public void fullEncodeProducesHeaderPlusRowMajorRgbBytes() {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            // A 2x2 image, opaque, distinct per-channel values so the byte layout
            // is unambiguous. MemoryImageSource delivers synchronously in the
            // default RGB colour model with the TOPDOWNLEFTRIGHT hint set.
            int[] pixels = { 0xFF010203, 0xFF040506, 0xFF070809, 0xFF0A0B0C };
            MemoryImageSource src = new MemoryImageSource(2, 2, pixels, 0, 2);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PPMEncoder enc = new PPMEncoder(src, baos);
            enc.encode();

            byte[] header = "P6\n2 2\n255\n".getBytes(StandardCharsets.UTF_8);
            byte[] body = bytes(0x01, 0x02, 0x03, 0x04, 0x05, 0x06,
                                 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C);
            assertArrayEquals(concat(header, body), baos.toByteArray());
        });
    }

    @Test
    public void encodeDoneIsANoOpAndWritesNothing() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PPMEncoder enc = new PPMEncoder(dummyProducer(), baos);

        enc.encodeDone();

        assertEquals(0, baos.size());
    }
}
