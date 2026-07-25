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

package org.scilab.forge.scirenderer.utils.shapes.geometry;

import org.junit.jupiter.api.Test;
import org.scilab.forge.scirenderer.Canvas;
import org.scilab.forge.scirenderer.shapes.geometry.Geometry;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hermetic unit tests for {@link CubeFactory}. The factory only asks its
 * {@link Canvas} for buffers (which it fills and stores on a {@code Geometry}),
 * so an inert recursive {@link Proxy} that returns further proxies for every
 * interface-typed method is a sufficient stand-in — no display or GL needed.
 */
public class CubeFactoryTest {

    /** A recursive auto-stub: every interface return yields another auto-stub. */
    private static Object autoStub(Class<?> iface) {
        return Proxy.newProxyInstance(
                   iface.getClassLoader(),
                   new Class<?>[] { iface },
                   (proxy, method, args) -> {
                       Class<?> rt = method.getReturnType();
                       if (rt.isInterface()) {
                           return autoStub(rt);
                       }
                       if (rt == boolean.class) {
                           return false;
                       }
                       if (rt == char.class) {
                           return '\0';
                       }
                       if (rt == byte.class) {
                           return (byte) 0;
                       }
                       if (rt == short.class) {
                           return (short) 0;
                       }
                       if (rt == int.class) {
                           return 0;
                       }
                       if (rt == long.class) {
                           return 0L;
                       }
                       if (rt == float.class) {
                           return 0f;
                       }
                       if (rt == double.class) {
                           return 0d;
                       }
                       return null;
                   });
    }

    private static Canvas fakeCanvas() {
        return (Canvas) autoStub(Canvas.class);
    }

    @Test
    public void defaultCubeIsATriangulatedUnwiredCube() {
        Geometry g = CubeFactory.createCube(fakeCanvas());
        assertNotNull(g);
        assertEquals(Geometry.FaceCullingMode.BOTH, g.getFaceCullingMode());
        assertEquals(Geometry.FillDrawingMode.TRIANGLES, g.getFillDrawingMode());
        assertNotNull(g.getVertices());
        assertNotNull(g.getNormals());
        assertNotNull(g.getIndices());
        // Not wired => no wire indices and the default (NONE) line mode.
        assertNull(g.getWireIndices());
        assertEquals(Geometry.LineDrawingMode.NONE, g.getLineDrawingMode());
        assertFalse(g.getPolygonOffsetMode());
    }

    @Test
    public void higherDensityStillBuildsAValidCube() {
        Geometry g = CubeFactory.createCube(fakeCanvas(), 3);
        assertNotNull(g);
        assertNotNull(g.getVertices());
        assertNotNull(g.getIndices());
    }

    @Test
    public void densityBelowOneReturnsNull() {
        assertNull(CubeFactory.createCube(fakeCanvas(), 0));
        assertNull(CubeFactory.createCube(fakeCanvas(), -5));
    }

    @Test
    public void wiredCubeExposesWireIndicesAndSegmentLineMode() {
        Geometry g = CubeFactory.createCube(fakeCanvas(), 1, true);
        assertNotNull(g);
        assertNotNull(g.getWireIndices());
        assertEquals(Geometry.LineDrawingMode.SEGMENTS, g.getLineDrawingMode());
        assertTrue(g.getPolygonOffsetMode());
    }
}
