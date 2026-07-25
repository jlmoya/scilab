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
import org.scilab.forge.scirenderer.shapes.appearance.Appearance;
import org.scilab.forge.scirenderer.shapes.appearance.Color;

import javax.swing.ImageIcon;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Hermetic unit tests for {@link BufferedImageTextureDrawingTools}. The class
 * renders into an in-memory {@link TextureBufferedImage}; every {@link java.awt}
 * call used here (BufferedImage, TextLayout, ImageIcon) works headlessly, so no
 * display or GPU is required.
 */
public class BufferedImageTextureDrawingToolsTest {

    static {
        // Prefer headless where possible; BufferedImage rendering works either way.
        System.setProperty("java.awt.headless", "true");
    }

    private static final Dimension SIZE = new Dimension(16, 16);

    /** A drawer that clears the whole texture to a single opaque colour. */
    private static final class ClearDrawer implements TextureDrawer {
        private final Color color;
        ClearDrawer(Color color) {
            this.color = color;
        }
        @Override
        public void draw(TextureDrawingTools t) {
            t.clear(color);
        }
        @Override
        public Dimension getTextureSize() {
            return SIZE;
        }
        @Override
        public OriginPosition getOriginPosition() {
            return OriginPosition.UPPER_LEFT;
        }
    }

    /** A drawer that exercises every primitive of the drawing tools. */
    private static final class EveryPrimitiveDrawer implements TextureDrawer {
        private final OriginPosition origin;
        EveryPrimitiveDrawer(OriginPosition origin) {
            this.origin = origin;
        }
        @Override
        public void draw(TextureDrawingTools t) {
            Appearance defaultAppearance = new Appearance(); // line == fill (default grey)

            Appearance distinctColors = new Appearance();
            distinctColors.setFillColor(new Color(1f, 0f, 0f, 1f)); // line != fill, opaque fill

            Appearance transparentFill = new Appearance();
            transparentFill.setFillColor(new Color(0f, 0f, 0f, 0f)); // alpha 0 => fill skipped

            t.clear(new Color(1f, 1f, 1f, 1f));

            t.drawPlus(6, defaultAppearance);
            t.drawPlus(0, defaultAppearance);       // r == 0 => single stroke branch
            t.drawMinus(6, defaultAppearance);
            t.drawVerticalLine(6, defaultAppearance);

            int[] line = {0, 0, 4, 4, 8, 0};
            t.drawPolyline(line, defaultAppearance);

            int[] quad = {0, 0, 6, 0, 6, 6, 0, 6};
            t.fillPolygon(quad, defaultAppearance);   // line == fill => border skipped
            t.fillPolygon(quad, distinctColors);      // line != fill => border drawn
            t.fillPolygon(quad, transparentFill);     // fill skipped, border drawn

            t.drawCircle(8, 8, 6, defaultAppearance);
            t.fillDisc(8, 8, 6, new Color(1f, 0f, 0f, 1f));  // opaque => drawn
            t.fillDisc(8, 8, 6, new Color(0f, 0f, 0f, 0f));  // alpha 0 => skipped

            t.draw(new ImageIcon(new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB)), 0, 0);

            t.draw(new TextEntity("Hi"), 1, 8);       // valid text, AA + fractional metrics on
            TextEntity plain = new TextEntity("x");
            plain.setTextAntiAliased(false);
            plain.setTextUseFractionalMetrics(false);
            t.draw(plain, 1, 12);                     // the "off" branches
            t.draw((TextEntity) null, 0, 0);          // null-guard branch, drawn nothing
        }
        @Override
        public Dimension getTextureSize() {
            return SIZE;
        }
        @Override
        public OriginPosition getOriginPosition() {
            return origin;
        }
    }

    @Test
    public void constructorAllocatesAnImageOfTheRequestedSize() {
        BufferedImageTextureDrawingTools tools = new BufferedImageTextureDrawingTools(SIZE);
        TextureBufferedImage image = tools.getImage();
        assertInstanceOf(TextureBufferedImage.class, image);
        assertEquals(16, image.getWidth());
        assertEquals(16, image.getHeight());
    }

    @Test
    public void getImageReturnsAStableInstance() {
        BufferedImageTextureDrawingTools tools = new BufferedImageTextureDrawingTools(SIZE);
        assertSame(tools.getImage(), tools.getImage());
    }

    @Test
    public void clearFillsTheEntireImageWithTheOpaqueColour() {
        BufferedImageTextureDrawingTools tools = new BufferedImageTextureDrawingTools(SIZE);
        tools.accept(new ClearDrawer(new Color(1f, 0f, 0f, 1f)));

        BufferedImage image = tools.getImage();
        assertEquals(0xFFFF0000, image.getRGB(0, 0));
        assertEquals(0xFFFF0000, image.getRGB(15, 15));
        assertEquals(0xFFFF0000, image.getRGB(8, 8));
    }

    @Test
    public void acceptExercisesEveryPrimitiveWithoutError() {
        BufferedImageTextureDrawingTools tools = new BufferedImageTextureDrawingTools(SIZE);
        tools.accept(new EveryPrimitiveDrawer(TextureDrawer.OriginPosition.UPPER_LEFT));

        ByteBuffer buffer = tools.getImage().getRGBABuffer();
        assertNotNull(buffer);
        assertEquals(16 * 16 * 4, buffer.capacity());
    }

    @Test
    public void acceptWithCenterOriginTranslatesTheDrawing() {
        // The CENTER branch shifts the origin to the texture centre before drawing.
        BufferedImageTextureDrawingTools tools = new BufferedImageTextureDrawingTools(SIZE);
        tools.accept(new EveryPrimitiveDrawer(TextureDrawer.OriginPosition.CENTER));
        assertNotNull(tools.getImage().getRGBABuffer());
    }
}
