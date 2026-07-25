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
import org.scilab.forge.scirenderer.shapes.geometry.DefaultGeometry;
import org.scilab.forge.scirenderer.shapes.geometry.Geometry;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hermetic unit tests for {@link TetrahedronFactory}, which subdivides a
 * tetrahedron and returns a filled + wired {@link Geometry}. The {@link Canvas}
 * is an inert recursive {@link Proxy} supplying buffer stubs.
 */
public class TetrahedronFactoryTest {

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
    public void createTetrahedronReturnsAFilledAndWiredGeometry() {
        DefaultGeometry g = TetrahedronFactory.createTetrahedron(fakeCanvas());
        assertNotNull(g);
        assertEquals(Geometry.FillDrawingMode.TRIANGLES, g.getFillDrawingMode());
        assertEquals(Geometry.LineDrawingMode.SEGMENTS, g.getLineDrawingMode());
        assertTrue(g.getPolygonOffsetMode());
        assertNotNull(g.getVertices());
        assertNotNull(g.getIndices());
        assertNotNull(g.getWireIndices());
    }
}
