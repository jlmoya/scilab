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

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ImageConsumer;
import java.awt.image.ImageProducer;
import java.io.ByteArrayOutputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.Hashtable;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Hermetic unit tests for the abstract {@link ImageEncoder} — the ACME
 * ImageConsumer framework PPMEncoder builds on. A tiny in-package
 * {@link RecordingEncoder} subclass captures the {@code encodeStart} /
 * {@code encodePixels} / {@code encodeDone} callbacks so the base class's own
 * state machine (started flag, RGB-default fast path, the non-TOPDOWNLEFTRIGHT
 * accumulate-then-flush path, and the IMAGEABORTED error path) can be asserted
 * directly, driven by hand-fed {@link ImageConsumer} calls — no display, no
 * Scilab, no threads other than the caller.
 */
public class ImageEncoderTest {

    // ---- Test doubles ---------------------------------------------------

    /** Records every callback the base ImageEncoder makes into the subclass. */
    private static final class RecordingEncoder extends ImageEncoder {
        int startCalls = 0;
        int doneCalls = 0;
        int startW = -1;
        int startH = -1;
        int encodePixelsCalls = 0;
        /** Row-major flatten of the pixels delivered to the LAST encodePixels call. */
        int[] lastBlockPixels = null;

        RecordingEncoder(ImageProducer producer, DataOutput dos) throws IOException {
            super(producer, dos);
        }

        RecordingEncoder(Image img, DataOutput dos) throws IOException {
            super(img, dos);
        }

        @Override
        protected void encodeStart(int w, int h) {
            startCalls++;
            startW = w;
            startH = h;
        }

        @Override
        protected void encodePixels(int x, int y, int w, int h, int[] rgbPixels, int off, int scansize) {
            encodePixelsCalls++;
            int[] flat = new int[w * h];
            int k = 0;
            for (int row = 0; row < h; ++row) {
                int rowOff = off + row * scansize;
                for (int col = 0; col < w; ++col) {
                    flat[k++] = rgbPixels[rowOff + col];
                }
            }
            lastBlockPixels = flat;
        }

        @Override
        protected void encodeDone() {
            doneCalls++;
        }

        /** Exposes the protected {@code props} field for assertions. */
        Hashtable exposedProps() {
            return props;
        }
    }

    /** A do-nothing producer; enough to satisfy the ctor and imageComplete's removeConsumer. */
    private static class NoopProducer implements ImageProducer {
        public void addConsumer(ImageConsumer ic) { }
        public boolean isConsumer(ImageConsumer ic) {
            return false;
        }
        public void removeConsumer(ImageConsumer ic) { }
        public void startProduction(ImageConsumer ic) { }
        public void requestTopDownLeftRightResend(ImageConsumer ic) { }
    }

    /** A producer whose production immediately aborts the image. */
    private static final class AbortProducer extends NoopProducer {
        @Override
        public void startProduction(ImageConsumer ic) {
            ic.imageComplete(ImageConsumer.IMAGEABORTED);
        }
    }

    private static RecordingEncoder newEncoder(ImageProducer p) throws IOException {
        return new RecordingEncoder(p, new DataOutputStream(new ByteArrayOutputStream()));
    }

    // ---- Fast path: RGB-default model + TOPDOWNLEFTRIGHT ------------------

    @Test
    public void topDownLeftRightDeliversPixelsDirectlyInOneBlock() throws IOException {
        RecordingEncoder enc = newEncoder(new NoopProducer());
        enc.setDimensions(2, 2);
        enc.setHints(ImageConsumer.TOPDOWNLEFTRIGHT);
        enc.setColorModel(ColorModel.getRGBdefault());

        int[] px = { 10, 20, 30, 40 };
        enc.setPixels(0, 0, 2, 2, ColorModel.getRGBdefault(), px, 0, 2);
        enc.imageComplete(ImageConsumer.STATICIMAGEDONE);

        // encodeStart is called exactly once, with the dimensions from setDimensions.
        assertEquals(1, enc.startCalls);
        assertEquals(2, enc.startW);
        assertEquals(2, enc.startH);
        // No accumulation: the block is passed straight through.
        assertEquals(1, enc.encodePixelsCalls);
        assertArrayEquals(new int[] {10, 20, 30, 40}, enc.lastBlockPixels);
        assertEquals(1, enc.doneCalls);
    }

    // ---- Accumulate path: hints WITHOUT TOPDOWNLEFTRIGHT -----------------

    @Test
    public void withoutTopDownHintPixelsAreAccumulatedThenFlushedOnceInImageOrder() throws IOException {
        RecordingEncoder enc = newEncoder(new NoopProducer());
        enc.setDimensions(2, 2);
        enc.setHints(0); // no TOPDOWNLEFTRIGHT -> accumulate

        // Deliver the BOTTOM row (y=1) first, then the TOP row (y=0): out of order.
        enc.setPixels(0, 1, 2, 1, ColorModel.getRGBdefault(), new int[] {30, 40}, 0, 2);
        enc.setPixels(0, 0, 2, 1, ColorModel.getRGBdefault(), new int[] {10, 20}, 0, 2);
        enc.imageComplete(ImageConsumer.STATICIMAGEDONE);

        // encodeStart still exactly once (on the first setPixels).
        assertEquals(1, enc.startCalls);
        // In accumulate mode, encodePixels fires ONCE, from imageComplete's flush,
        // covering the whole image in correct (top-to-bottom) order despite the
        // out-of-order delivery.
        assertEquals(1, enc.encodePixelsCalls);
        assertArrayEquals(new int[] {10, 20, 30, 40}, enc.lastBlockPixels);
        assertEquals(1, enc.doneCalls);
    }

    // ---- Non-RGB-default model conversion path ---------------------------

    @Test
    public void intPixelsInANonDefaultModelAreConvertedThroughGetRgb() throws IOException {
        RecordingEncoder enc = newEncoder(new NoopProducer());
        enc.setDimensions(2, 1);
        enc.setHints(ImageConsumer.TOPDOWNLEFTRIGHT);

        // A model that is NOT ColorModel.getRGBdefault() forces the per-pixel
        // getRGB() conversion branch. Its getRGB maps index -> a known ARGB.
        ColorModel model = new StubColorModel();
        enc.setPixels(0, 0, 2, 1, model, new int[] {1, 2}, 0, 2);
        enc.imageComplete(ImageConsumer.STATICIMAGEDONE);

        assertEquals(1, enc.startCalls);
        assertEquals(1, enc.encodePixelsCalls);
        // StubColorModel.getRGB(p) == 0xFF0000 * p, so 1 -> 0xFF0000, 2 -> 0x1FE0000.
        assertArrayEquals(new int[] {0xFF0000, 0xFF0000 * 2}, enc.lastBlockPixels);
        assertEquals(1, enc.doneCalls);
    }

    @Test
    public void bytePixelsAreMaskedTo8BitsAndConvertedThroughGetRgb() throws IOException {
        RecordingEncoder enc = newEncoder(new NoopProducer());
        enc.setDimensions(2, 1);
        enc.setHints(ImageConsumer.TOPDOWNLEFTRIGHT);

        ColorModel model = new StubColorModel();
        // 0x81 as a signed byte is negative; the base class masks with 0xff before
        // calling getRGB, so the index seen must be 129, not -127.
        enc.setPixels(0, 0, 2, 1, model, new byte[] {(byte) 0x81, (byte) 0x02}, 0, 2);
        enc.imageComplete(ImageConsumer.STATICIMAGEDONE);

        assertEquals(1, enc.encodePixelsCalls);
        assertArrayEquals(new int[] {0xFF0000 * 0x81, 0xFF0000 * 0x02}, enc.lastBlockPixels);
    }

    // ---- Error path ------------------------------------------------------

    @Test
    public void encodeThrowsWhenProductionAbortsAndDoesNotFinish() throws IOException {
        RecordingEncoder enc = newEncoder(new AbortProducer());

        IOException ex = assertThrows(IOException.class, enc::encode);
        assertEquals("image aborted", ex.getMessage());
        // On abort, the finishing callbacks must NOT run.
        assertEquals(0, enc.doneCalls);
        assertEquals(0, enc.encodePixelsCalls);
    }

    // ---- Simple setters --------------------------------------------------

    @Test
    public void setPropertiesStoresTheGivenTable() throws IOException {
        RecordingEncoder enc = newEncoder(new NoopProducer());
        Hashtable<String, Object> props = new Hashtable<>();
        props.put("k", "v");

        enc.setProperties(props);

        assertSame(props, enc.exposedProps());
    }

    @Test
    public void setColorModelIsAHarmlessNoOp() throws IOException {
        RecordingEncoder enc = newEncoder(new NoopProducer());
        assertDoesNotThrow(() -> enc.setColorModel(null));
        assertDoesNotThrow(() -> enc.setColorModel(ColorModel.getRGBdefault()));
    }

    // ---- Image-based constructor -----------------------------------------

    @Test
    public void imageBasedConstructorDrivesEncodingFromTheImageSource() {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            // The ImageEncoder(Image, DataOutput) ctor delegates to
            // this(img.getSource(), dos); a BufferedImage exposes a synchronous
            // ImageProducer that drives one full pass on the calling thread.
            BufferedImage img = new BufferedImage(2, 1, BufferedImage.TYPE_INT_RGB);
            img.setRGB(0, 0, 0x0000FF);
            img.setRGB(1, 0, 0x00FF00);

            RecordingEncoder enc =
                new RecordingEncoder((Image) img, new DataOutputStream(new ByteArrayOutputStream()));
            enc.encode();

            // Exactly one start (dimensions from the image) and one done.
            assertEquals(1, enc.startCalls);
            assertEquals(2, enc.startW);
            assertEquals(1, enc.startH);
            assertEquals(1, enc.doneCalls);
            // At least one block of pixels was delivered to the subclass.
            assertTrue(enc.encodePixelsCalls >= 1);
        });
    }

    // ---- Accumulate path via the BYTE overload ---------------------------

    @Test
    public void bytePixelsAreAccumulatedThenFlushedOnceWhenNotTopDown() throws IOException {
        RecordingEncoder enc = newEncoder(new NoopProducer());
        enc.setDimensions(2, 2);
        enc.setHints(0); // no TOPDOWNLEFTRIGHT -> accumulate
        ColorModel model = new StubColorModel();

        // Deliver the BOTTOM row (y=1) first, then the TOP row (y=0), via the
        // byte overload — exercises byte->getRGB conversion AND accumulation.
        enc.setPixels(0, 1, 2, 1, model, new byte[] {(byte) 3, (byte) 4}, 0, 2);
        enc.setPixels(0, 0, 2, 1, model, new byte[] {(byte) 1, (byte) 2}, 0, 2);
        enc.imageComplete(ImageConsumer.STATICIMAGEDONE);

        assertEquals(1, enc.startCalls);
        // Accumulate mode: a single flush from imageComplete, in image order.
        assertEquals(1, enc.encodePixelsCalls);
        assertArrayEquals(
            new int[] {0xFF0000 * 1, 0xFF0000 * 2, 0xFF0000 * 3, 0xFF0000 * 4},
            enc.lastBlockPixels);
        assertEquals(1, enc.doneCalls);
    }

    // ---- Fast path with several blocks -----------------------------------

    @Test
    public void multipleFastPathBlocksAreEachForwardedImmediately() throws IOException {
        RecordingEncoder enc = newEncoder(new NoopProducer());
        enc.setDimensions(1, 2);
        enc.setHints(ImageConsumer.TOPDOWNLEFTRIGHT);

        enc.setPixels(0, 0, 1, 1, ColorModel.getRGBdefault(), new int[] {111}, 0, 1);
        enc.setPixels(0, 1, 1, 1, ColorModel.getRGBdefault(), new int[] {222}, 0, 1);
        enc.imageComplete(ImageConsumer.STATICIMAGEDONE);

        assertEquals(1, enc.startCalls);
        // No accumulation: each setPixels forwards immediately (two calls), and
        // imageComplete's encodeFinish adds NOTHING in non-accumulate mode.
        assertEquals(2, enc.encodePixelsCalls);
        assertArrayEquals(new int[] {222}, enc.lastBlockPixels); // the last block
        assertEquals(1, enc.doneCalls);
    }

    /**
     * Minimal ColorModel whose only useful behaviour is a deterministic
     * {@code getRGB(int)} = 0xFF0000 * pixel, so conversion is easy to assert.
     */
    private static final class StubColorModel extends ColorModel {
        StubColorModel() {
            super(32);
        }
        @Override
        public int getRGB(int pixel) {
            return 0xFF0000 * pixel;
        }
        @Override
        public int getRed(int pixel) {
            return (getRGB(pixel) >> 16) & 0xff;
        }
        @Override
        public int getGreen(int pixel) {
            return (getRGB(pixel) >> 8) & 0xff;
        }
        @Override
        public int getBlue(int pixel) {
            return getRGB(pixel) & 0xff;
        }
        @Override
        public int getAlpha(int pixel) {
            return 0xff;
        }
    }
}
