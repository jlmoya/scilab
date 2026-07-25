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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Hermetic unit tests for {@link SphereFactory} (the lazy singleton that builds a
 * lat/long tessellated sphere geometry). The {@link Canvas} collaborator is an
 * inert recursive {@link Proxy}, so no GL context or display is involved.
 */
public class SphereFactoryTest {

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
    public void getSingletonAlwaysReturnsTheSameInstance() {
        SphereFactory first = SphereFactory.getSingleton();
        assertNotNull(first);
        assertSame(first, SphereFactory.getSingleton());
    }

    @Test
    public void createBuildsATriangulatedSphereGeometry() {
        Geometry g = SphereFactory.getSingleton().create(fakeCanvas(), 2.0f, 8, 16);
        assertNotNull(g);
        assertEquals(Geometry.FillDrawingMode.TRIANGLES, g.getFillDrawingMode());
        assertNotNull(g.getVertices());
        assertNotNull(g.getIndices());
    }

    @Test
    public void resolutionsBelowTheMinimumAreClampedNotRejected() {
        // latitude < 3 clamps to 3 and longitude < 4 clamps to 4; the call must
        // still succeed and yield a valid geometry.
        Geometry g = SphereFactory.getSingleton().create(fakeCanvas(), 1.0f, 2, 3);
        assertNotNull(g);
        assertNotNull(g.getVertices());
        assertNotNull(g.getIndices());
    }
}
