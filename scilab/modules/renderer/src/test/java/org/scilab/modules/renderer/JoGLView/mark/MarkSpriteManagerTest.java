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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.scilab.forge.scirenderer.texture.Texture;
import org.scilab.forge.scirenderer.texture.TextureManager;
import org.scilab.modules.graphic_objects.contouredObject.Mark;
import org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties;

/**
 * Hermetic unit tests for {@link MarkSpriteManager}, the per-object /
 * per-size sprite cache. The class only needs a {@link TextureManager} to
 * mint and release textures; the real GL-backed manager is replaced here by
 * a recording fake, and each {@link Texture} is a dynamic proxy (its only
 * exercised method is {@code setDrawer}, from {@code MarkSpriteFactory}).
 * This lets the caching, per-size separation, eviction and property-driven
 * {@code update} routing all run without a display.
 */
class MarkSpriteManagerTest {

    /**
     * A {@link TextureManager} that hands back proxy textures and counts how
     * many times each {@code dispose} overload is called.
     */
    private static final class RecordingTextureManager implements TextureManager {
        final List<Texture> created = new ArrayList<Texture>();
        int disposeCollectionCalls = 0;
        int disposeSingleCalls = 0;

        @Override
        public Texture createTexture() {
            Texture t = (Texture) Proxy.newProxyInstance(
                                Texture.class.getClassLoader(),
                                new Class<?>[] { Texture.class },
                                new InvocationHandler() {
                @Override
                public Object invoke(Object proxy, Method method, Object[] args) {
                    String name = method.getName();
                    if ("hashCode".equals(name)) {
                        return System.identityHashCode(proxy);
                    }
                    if ("equals".equals(name)) {
                        return proxy == args[0];
                    }
                    if ("toString".equals(name)) {
                        return "FakeTexture";
                    }
                    return null;
                }
            });
            created.add(t);
            return t;
        }

        @Override
        public void dispose(Collection<Texture> textures) {
            disposeCollectionCalls++;
        }

        @Override
        public void dispose(Texture texture) {
            disposeSingleCalls++;
        }
    }

    private static Mark markOfSize(int size) {
        Mark m = new Mark();
        m.setSize(size);
        return m;
    }

    @Test
    void constructorAcceptsATextureManager() {
        assertDoesNotThrow(() -> new MarkSpriteManager(new RecordingTextureManager()));
    }

    @Test
    void getMarkSpriteIsCachedPerIdAndSize() {
        RecordingTextureManager tm = new RecordingTextureManager();
        MarkSpriteManager manager = new MarkSpriteManager(tm);

        Texture first = manager.getMarkSprite(10, markOfSize(0), null, null);
        Texture second = manager.getMarkSprite(10, markOfSize(0), null, null);

        assertSame(first, second, "same id + same size must reuse the cached sprite");
        assertEquals(1, tm.created.size(), "only one texture should have been minted");
    }

    @Test
    void differentSizesGetDistinctSprites() {
        RecordingTextureManager tm = new RecordingTextureManager();
        MarkSpriteManager manager = new MarkSpriteManager(tm);

        Texture small = manager.getMarkSprite(10, markOfSize(0), null, null);
        Texture big = manager.getMarkSprite(10, markOfSize(5), null, null);

        assertNotSame(small, big, "distinct mark sizes must not share a sprite");
        assertEquals(2, tm.created.size());
    }

    @Test
    void differentObjectIdsGetDistinctSprites() {
        RecordingTextureManager tm = new RecordingTextureManager();
        MarkSpriteManager manager = new MarkSpriteManager(tm);

        Texture a = manager.getMarkSprite(1, markOfSize(0), null, null);
        Texture b = manager.getMarkSprite(2, markOfSize(0), null, null);

        assertNotSame(a, b);
        assertEquals(2, tm.created.size());
    }

    @Test
    void disposeReleasesTexturesAndClearsTheCache() {
        RecordingTextureManager tm = new RecordingTextureManager();
        MarkSpriteManager manager = new MarkSpriteManager(tm);

        Texture before = manager.getMarkSprite(10, markOfSize(0), null, null);
        manager.dispose(10);
        assertEquals(1, tm.disposeCollectionCalls, "the size-map's textures must be disposed");

        Texture after = manager.getMarkSprite(10, markOfSize(0), null, null);
        assertNotSame(before, after, "after eviction a fresh sprite is minted");
        assertEquals(2, tm.created.size());
    }

    @Test
    void disposeOfAnUnknownIdIsANoOp() {
        RecordingTextureManager tm = new RecordingTextureManager();
        MarkSpriteManager manager = new MarkSpriteManager(tm);

        assertDoesNotThrow(() -> manager.dispose(999));
        assertEquals(0, tm.disposeCollectionCalls, "nothing cached -> nothing disposed");
    }

    @Test
    void disposeAllReleasesEverySizeMap() {
        RecordingTextureManager tm = new RecordingTextureManager();
        MarkSpriteManager manager = new MarkSpriteManager(tm);

        manager.getMarkSprite(1, markOfSize(0), null, null);
        manager.getMarkSprite(2, markOfSize(0), null, null);

        manager.disposeAll();
        assertEquals(2, tm.disposeCollectionCalls, "one dispose per cached object id");

        // Cache is empty again: re-fetching mints brand-new textures.
        manager.getMarkSprite(1, markOfSize(0), null, null);
        assertEquals(3, tm.created.size());
    }

    @Test
    void updateEvictsTheSpriteForMarkAffectingProperties() {
        RecordingTextureManager tm = new RecordingTextureManager();
        MarkSpriteManager manager = new MarkSpriteManager(tm);

        Texture before = manager.getMarkSprite(10, markOfSize(0), null, null);
        manager.update(10, GraphicObjectProperties.__GO_MARK_STYLE__);
        assertEquals(1, tm.disposeCollectionCalls, "a mark-affecting change must drop the sprite");

        Texture after = manager.getMarkSprite(10, markOfSize(0), null, null);
        assertNotSame(before, after);
    }

    @Test
    void updateEvictsForEachListedMarkProperty() {
        int[] triggering = {
            GraphicObjectProperties.__GO_MARK_MODE__,
            GraphicObjectProperties.__GO_MARK_STYLE__,
            GraphicObjectProperties.__GO_MARK_SIZE_UNIT__,
            GraphicObjectProperties.__GO_MARK_SIZE__,
            GraphicObjectProperties.__GO_MARK_FOREGROUND__,
            GraphicObjectProperties.__GO_MARK_BACKGROUND__,
            GraphicObjectProperties.__GO_LINE_THICKNESS__,
            GraphicObjectProperties.__GO_SELECTED__,
            GraphicObjectProperties.__GO_COLOR_SET__,
        };
        for (int property : triggering) {
            RecordingTextureManager tm = new RecordingTextureManager();
            MarkSpriteManager manager = new MarkSpriteManager(tm);
            manager.getMarkSprite(10, markOfSize(0), null, null);
            manager.update(10, property);
            assertEquals(1, tm.disposeCollectionCalls,
                         "property " + property + " should evict the sprite");
        }
    }

    @Test
    void updateIgnoresPropertiesUnrelatedToMarks() {
        RecordingTextureManager tm = new RecordingTextureManager();
        MarkSpriteManager manager = new MarkSpriteManager(tm);

        Texture before = manager.getMarkSprite(10, markOfSize(0), null, null);
        manager.update(10, GraphicObjectProperties.__GO_VISIBLE__);

        assertEquals(0, tm.disposeCollectionCalls, "an unrelated change must not touch the cache");
        Texture after = manager.getMarkSprite(10, markOfSize(0), null, null);
        assertSame(before, after, "the sprite survives an unrelated update");
    }
}
