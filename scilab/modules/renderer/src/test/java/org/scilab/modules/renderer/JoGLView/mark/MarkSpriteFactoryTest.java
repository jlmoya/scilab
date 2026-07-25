/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Jose Moya
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 *
 */

package org.scilab.modules.renderer.JoGLView.mark;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collection;

import org.junit.jupiter.api.Test;
import org.scilab.forge.scirenderer.texture.Texture;
import org.scilab.forge.scirenderer.texture.TextureDrawer;
import org.scilab.forge.scirenderer.texture.TextureManager;
import org.scilab.modules.graphic_objects.contouredObject.Mark;
import org.scilab.modules.graphic_objects.contouredObject.Mark.MarkSizeUnitType;

/**
 * Hermetic unit tests for {@link MarkSpriteFactory}, the static factory that
 * turns a graphic_objects {@link Mark} into a scirenderer sprite drawer.
 *
 * <p>The factory mints a {@link Texture} from a {@link TextureManager} and
 * hands it a {@link TextureDrawer}. Here the manager is a fake whose proxy
 * texture <em>captures</em> the drawer it is given, letting the tests assert
 * which concrete drawer the factory selected for each mark style and how it
 * sizes the sprite - all without a GL context (the drawer's {@code draw()} is
 * never invoked). The colour-map path is exercised with a {@code null} map
 * (the common auto-colour case); the drawer constructors only compute
 * geometry, so they run on any JVM.
 */
class MarkSpriteFactoryTest {

    /** Fake manager whose proxy texture records the drawer set on it. */
    private static final class CapturingTextureManager implements TextureManager {
        TextureDrawer captured;

        @Override
        public Texture createTexture() {
            return (Texture) Proxy.newProxyInstance(
                       Texture.class.getClassLoader(),
                       new Class<?>[] { Texture.class },
                       new InvocationHandler() {
                @Override
                public Object invoke(Object proxy, Method method, Object[] args) {
                    if ("setDrawer".equals(method.getName())) {
                        captured = (TextureDrawer) args[0];
                        return null;
                    }
                    if ("hashCode".equals(method.getName())) {
                        return System.identityHashCode(proxy);
                    }
                    if ("equals".equals(method.getName())) {
                        return proxy == args[0];
                    }
                    if ("toString".equals(method.getName())) {
                        return "FakeTexture";
                    }
                    return null;
                }
            });
        }

        @Override
        public void dispose(Collection<Texture> textures) {
        }

        @Override
        public void dispose(Texture texture) {
        }
    }

    private static Mark mark(int style, int size) {
        Mark m = new Mark();
        m.setStyle(style);
        m.setSize(size);
        return m;
    }

    private static TextureDrawer drawerFor(Mark mark, Integer selectedColor) {
        CapturingTextureManager tm = new CapturingTextureManager();
        Texture t = MarkSpriteFactory.getMarkSprite(tm, mark, selectedColor, null, null);
        assertNotNull(t, "the factory must return a texture");
        assertNotNull(tm.captured, "the factory must install a drawer on the texture");
        return tm.captured;
    }

    @Test
    void eachMarkStyleSelectsItsMatchingDrawer() {
        String[] expected = {
            "DotSpriteDrawer",             //  0
            "PlusSpriteDrawer",            //  1
            "CrossSpriteDrawer",           //  2
            "StarSpriteDrawer",            //  3
            "FilledDiamondSpriteDrawer",   //  4
            "DiamondSpriteDrawer",         //  5
            "TriangleUpSpriteDrawer",      //  6
            "TriangleDownSpriteDrawer",    //  7
            "DiamondPlusSpriteDrawer",     //  8
            "CircleSpriteDrawer",          //  9
            "AsteriskSpriteDrawer",        // 10
            "SquareSpriteDrawer",          // 11
            "TriangleRightSpriteDrawer",   // 12
            "TriangleLeftSpriteDrawer",    // 13
            "PentagramSpriteDrawer",       // 14
            "TriangleUpPickSpriteDrawer",  // 15
            "TriangleDownPickSpriteDrawer", // 16
            "TriangleRightPickSpriteDrawer", // 17
            "TriangleLeftPickSpriteDrawer", // 18
            "MinusSpriteDrawer",           // 19
            "VerticalLineSpriteDrawer",    // 20
        };
        for (int style = 0; style < expected.length; style++) {
            // A pixel size of 10 keeps finalSize != 1 so the style switch runs.
            TextureDrawer drawer = drawerFor(mark(style, 10), null);
            assertEquals(expected[style], drawer.getClass().getSimpleName(),
                         "style " + style + " must map to " + expected[style]);
        }
    }

    @Test
    void anUnknownStyleFallsBackToThePlusDrawer() {
        TextureDrawer drawer = drawerFor(mark(99, 10), null);
        assertEquals("PlusSpriteDrawer", drawer.getClass().getSimpleName());
    }

    @Test
    void aUnitPixelSizeAlwaysDrawsAPlusRegardlessOfStyle() {
        // finalSize == 1 short-circuits the style switch (see the else branch).
        TextureDrawer drawer = drawerFor(mark(9, 1), null);
        assertEquals("PlusSpriteDrawer", drawer.getClass().getSimpleName());
    }

    @Test
    void aZeroSizedNonDotMarkIsPromotedToAUnitPlus() {
        // finalSize 0 -> 1 (bug 13551), which then routes to the Plus fallback.
        TextureDrawer drawer = drawerFor(mark(5, 0), null);
        assertEquals("PlusSpriteDrawer", drawer.getClass().getSimpleName());
    }

    @Test
    void tabulatedSizeUnitRescalesTheSprite() {
        Mark m = mark(1, 3);
        m.setMarkSizeUnit(MarkSizeUnitType.TABULATED);
        // finalSize = 8 + 2*size = 14.
        TextureDrawer drawer = drawerFor(m, null);
        assertEquals("PlusSpriteDrawer", drawer.getClass().getSimpleName());
        // Documented margin formula: s = (int)(14 * 1.5) = 21 (odd) -> margin 2.
        assertEquals(23, drawer.getTextureSize().width);
        assertEquals(23, drawer.getTextureSize().height);
    }

    @Test
    void tabulatedDotOfZeroSizeIsASinglePixel() {
        Mark m = mark(0, 0);
        m.setMarkSizeUnit(MarkSizeUnitType.TABULATED);
        // Special-cased to a single pixel -> finalSize 1 -> Plus fallback.
        TextureDrawer drawer = drawerFor(m, null);
        assertEquals("PlusSpriteDrawer", drawer.getClass().getSimpleName());
    }

    @Test
    void spriteSizeFollowsTheDocumentedMarginFormula() {
        // size 10 -> s = 15 (odd) -> margin 2 -> 17
        assertEquals(17, drawerFor(mark(1, 10), null).getTextureSize().width);
        // size 8  -> s = 12 (even) -> margin 3 -> 15
        assertEquals(15, drawerFor(mark(1, 8), null).getTextureSize().width);
    }

    @Test
    void theSpriteOriginIsCentered() {
        TextureDrawer drawer = drawerFor(mark(1, 10), null);
        assertEquals(TextureDrawer.OriginPosition.CENTER, drawer.getOriginPosition());
    }

    @Test
    void aSelectedColorOverridesTheForegroundWithoutError() {
        // Exercises the selectedColor != null branch of the colour selection.
        TextureDrawer drawer = drawerFor(mark(1, 10), Integer.valueOf(7));
        assertEquals("PlusSpriteDrawer", drawer.getClass().getSimpleName());
    }

    @Test
    void handlesTheMinusThreeAutoColourSentinels() {
        // background == -3 && foreground == -3
        Mark both = mark(1, 10);
        both.setBackground(-3);
        both.setForeground(-3);
        assertNotNull(drawerFor(both, null));

        // background == -3 only
        Mark bg = mark(1, 10);
        bg.setBackground(-3);
        bg.setForeground(0);
        assertNotNull(drawerFor(bg, null));
    }
}
