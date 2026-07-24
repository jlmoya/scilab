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

package org.scilab.forge.scirenderer.tranformations;

import org.junit.jupiter.api.Test;
import org.scilab.forge.scirenderer.Canvas;

import java.awt.Dimension;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hermetic unit tests for {@link TransformationManagerImpl}. The only collaborator is a
 * {@link Canvas}, and the manager touches it solely through {@code getWidth()} /
 * {@code getHeight()}; we supply a fixed-size stub via {@link Proxy} so no GL context,
 * window, or event loop is involved.
 */
public class TransformationManagerImplTest {

    private static final double EPS = 1e-9;

    /**
     * A dimension-only {@link Canvas} stub. Only width/height/dimension carry values; every
     * other method returns a type-appropriate default (the manager never calls them here).
     */
    private static Canvas canvasOf(final int width, final int height) {
        InvocationHandler handler = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) {
                if (method.getDeclaringClass() == Object.class) {
                    switch (method.getName()) {
                        case "hashCode":
                            return System.identityHashCode(proxy);
                        case "equals":
                            return proxy == args[0];
                        default:
                            return "CanvasStub(" + width + "x" + height + ")";
                    }
                }
                switch (method.getName()) {
                    case "getWidth":
                        return width;
                    case "getHeight":
                        return height;
                    case "getDimension":
                        return new Dimension(width, height);
                    default:
                        break;
                }
                Class<?> rt = method.getReturnType();
                if (rt == boolean.class) {
                    return false;
                }
                if (rt == int.class || rt == short.class || rt == byte.class) {
                    return 0;
                }
                if (rt == long.class) {
                    return 0L;
                }
                if (rt == double.class) {
                    return 0.0;
                }
                if (rt == float.class) {
                    return 0.0f;
                }
                if (rt == char.class) {
                    return '\0';
                }
                return null;
            }
        };
        return (Canvas) Proxy.newProxyInstance(Canvas.class.getClassLoader(), new Class<?>[] { Canvas.class }, handler);
    }

    @Test
    public void freshManagerExposesTwoDistinctNonNullStacks() {
        TransformationManagerImpl mgr = new TransformationManagerImpl(canvasOf(200, 100));
        assertNotNull(mgr.getModelViewStack());
        assertNotNull(mgr.getProjectionStack());
        assertNotSame(mgr.getModelViewStack(), mgr.getProjectionStack());
    }

    @Test
    public void sceneCoordinateIsTheDefault() {
        TransformationManagerImpl mgr = new TransformationManagerImpl(canvasOf(200, 100));
        assertTrue(mgr.isUsingSceneCoordinate());
    }

    @Test
    public void coordinateModeTogglesAndFiresOnlyOnActualChange() {
        TransformationManagerImpl mgr = new TransformationManagerImpl(canvasOf(200, 100));
        AtomicInteger fired = new AtomicInteger();
        mgr.addListener(m -> fired.incrementAndGet());

        mgr.useWindowCoordinate();
        assertFalse(mgr.isUsingSceneCoordinate());
        assertEquals(1, fired.get());

        // Already in window coordinate => no state change => no event.
        mgr.useWindowCoordinate();
        assertEquals(1, fired.get());

        mgr.useSceneCoordinate();
        assertTrue(mgr.isUsingSceneCoordinate());
        assertEquals(2, fired.get());

        mgr.useSceneCoordinate();
        assertEquals(2, fired.get());
    }

    @Test
    public void removedListenerIsNoLongerNotified() {
        TransformationManagerImpl mgr = new TransformationManagerImpl(canvasOf(200, 100));
        AtomicInteger fired = new AtomicInteger();
        TransformationManagerListener listener = m -> fired.incrementAndGet();
        mgr.addListener(listener);
        mgr.useWindowCoordinate();
        assertEquals(1, fired.get());

        mgr.removeListener(listener);
        mgr.useSceneCoordinate();
        assertEquals(1, fired.get());
    }

    @Test
    public void getTransformationIsTheIdentityForFreshStacksAndIsCached() {
        TransformationManagerImpl mgr = new TransformationManagerImpl(canvasOf(200, 100));
        Transformation first = mgr.getTransformation();
        assertTrue(first.isIdentity());
        // Cached: nothing changed, so the same instance comes back.
        assertSame(first, mgr.getTransformation());
    }

    @Test
    public void pushingOnAStackInvalidatesTheCachedTransformation() {
        TransformationManagerImpl mgr = new TransformationManagerImpl(canvasOf(200, 100));
        assertTrue(mgr.getTransformation().isIdentity());

        mgr.getModelViewStack().push(TransformationFactory.getTranslateTransformation(1, 2, 3));

        Transformation t = mgr.getTransformation();
        assertFalse(t.isIdentity(), "the manager listens to its stacks and recomputes");
        // projection is still identity, so the composite equals the model-view translation.
        assertTrue(new Vector3d(1, 2, 3).equals(t.project(new Vector3d(0, 0, 0))));
    }

    @Test
    public void windowTransformationMapsTheCanvasCentreToTheOrigin() {
        TransformationManagerImpl mgr = new TransformationManagerImpl(canvasOf(200, 100));
        Transformation window = mgr.getWindowTransformation();

        // Centre (100, 50) -> (0, 0); lower-left corner (0, 0) -> (-1, -1).
        assertTrue(new Vector3d(0, 0, 0).equals(window.project(new Vector3d(100, 50, 0))),
                   "canvas centre must map to NDC origin");
        assertTrue(new Vector3d(-1, -1, 0).equals(window.project(new Vector3d(0, 0, 0))),
                   "canvas origin must map to NDC (-1,-1)");
    }

    @Test
    public void windowAndInverseWindowTransformationsRoundTrip() {
        TransformationManagerImpl mgr = new TransformationManagerImpl(canvasOf(200, 100));
        Transformation window = mgr.getWindowTransformation();
        Transformation inverse = mgr.getInverseWindowTransformation();

        Vector3d p = new Vector3d(37, 11, 0);
        Vector3d back = inverse.project(window.project(p));
        assertEquals(p.getX(), back.getX(), EPS);
        assertEquals(p.getY(), back.getY(), EPS);
        assertEquals(p.getZ(), back.getZ(), EPS);
    }

    @Test
    public void g2dWindowProjectionFlipsYAboutTheCanvasHeight() {
        TransformationManagerImpl mgr = new TransformationManagerImpl(canvasOf(200, 100));
        Transformation g2d = mgr.getG2DWindowProjection();

        // f(x, y) = (x, height - y): (0,0) -> (0,100) and (5,100) -> (5,0).
        assertTrue(new Vector3d(0, 100, 0).equals(g2d.project(new Vector3d(0, 0, 0))));
        assertTrue(new Vector3d(5, 0, 0).equals(g2d.project(new Vector3d(5, 100, 0))));
    }

    @Test
    public void resetClearsBothStacks() {
        TransformationManagerImpl mgr = new TransformationManagerImpl(canvasOf(200, 100));
        mgr.getModelViewStack().push(TransformationFactory.getTranslateTransformation(1, 1, 1));
        mgr.getProjectionStack().push(TransformationFactory.getTranslateTransformation(2, 2, 2));

        mgr.reset();

        assertTrue(mgr.getModelViewStack().peek().isIdentity());
        assertTrue(mgr.getProjectionStack().peek().isIdentity());
    }

    @Test
    public void projectionOnlyHelpersDoNotDependOnTheModelViewStack() {
        TransformationManagerImpl mgr = new TransformationManagerImpl(canvasOf(200, 100));
        // getG2DSingleProjection() uses only the projection stack; a model-view push must not change it.
        Vector3d probe = new Vector3d(0, 0, 0);
        Vector3d before = mgr.getG2DSingleProjection().project(probe);
        mgr.getModelViewStack().push(TransformationFactory.getTranslateTransformation(10, 20, 0));
        Vector3d after = mgr.getG2DSingleProjection().project(probe);
        assertTrue(before.equals(after), "single projection ignores the model-view stack");
    }
}
