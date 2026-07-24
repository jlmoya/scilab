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

package org.scilab.forge.scirenderer.shapes.geometry;

import org.junit.jupiter.api.Test;
import org.scilab.forge.scirenderer.buffers.ElementsBuffer;
import org.scilab.forge.scirenderer.buffers.IndicesBuffer;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hermetic unit tests for {@link DefaultGeometry} (the mutable {@link Geometry} value holder)
 * and the enums / default constants declared on {@link Geometry}. Buffer collaborators are
 * inert {@link Proxy} stubs, since the geometry only ever stores and returns their references.
 */
public class DefaultGeometryTest {

    private static ElementsBuffer elementsStub() {
        return (ElementsBuffer) Proxy.newProxyInstance(
                   ElementsBuffer.class.getClassLoader(),
                   new Class<?>[] { ElementsBuffer.class },
                   (proxy, method, args) -> defaultReturn(method.getReturnType()));
    }

    private static IndicesBuffer indicesStub() {
        return (IndicesBuffer) Proxy.newProxyInstance(
                   IndicesBuffer.class.getClassLoader(),
                   new Class<?>[] { IndicesBuffer.class },
                   (proxy, method, args) -> defaultReturn(method.getReturnType()));
    }

    private static Object defaultReturn(Class<?> rt) {
        if (rt == boolean.class) {
            return false;
        }
        if (rt == int.class) {
            return 0;
        }
        if (rt.isPrimitive()) {
            return 0;
        }
        return null;
    }

    @Test
    public void freshGeometryUsesTheInterfaceDefaults() {
        DefaultGeometry g = new DefaultGeometry();
        assertEquals(Geometry.DEFAULT_FACE_CULLING_MODE, g.getFaceCullingMode());
        assertEquals(Geometry.DEFAULT_FILL_DRAWING_MODE, g.getFillDrawingMode());
        assertEquals(Geometry.DEFAULT_LINE_DRAWING_MODE, g.getLineDrawingMode());
        assertEquals(Geometry.DEFAULT_POLYGON_OFFSET_MODE, g.getPolygonOffsetMode());

        assertEquals(Geometry.FaceCullingMode.BOTH, g.getFaceCullingMode());
        assertEquals(Geometry.FillDrawingMode.TRIANGLES, g.getFillDrawingMode());
        assertEquals(Geometry.LineDrawingMode.NONE, g.getLineDrawingMode());
        assertFalse(g.getPolygonOffsetMode());
    }

    @Test
    public void freshGeometryHasNoBuffers() {
        DefaultGeometry g = new DefaultGeometry();
        assertNull(g.getVertices());
        assertNull(g.getColors());
        assertNull(g.getNormals());
        assertNull(g.getTextureCoordinates());
        assertNull(g.getIndices());
        assertNull(g.getWireIndices());
    }

    @Test
    public void modeSettersRoundTrip() {
        DefaultGeometry g = new DefaultGeometry();
        g.setFaceCullingMode(Geometry.FaceCullingMode.CW);
        g.setFillDrawingMode(Geometry.FillDrawingMode.TRIANGLE_FAN);
        g.setLineDrawingMode(Geometry.LineDrawingMode.SEGMENTS_LOOP);
        g.setPolygonOffsetMode(true);

        assertEquals(Geometry.FaceCullingMode.CW, g.getFaceCullingMode());
        assertEquals(Geometry.FillDrawingMode.TRIANGLE_FAN, g.getFillDrawingMode());
        assertEquals(Geometry.LineDrawingMode.SEGMENTS_LOOP, g.getLineDrawingMode());
        assertTrue(g.getPolygonOffsetMode());
    }

    @Test
    public void bufferSettersReturnTheStoredReference() {
        DefaultGeometry g = new DefaultGeometry();
        ElementsBuffer vertices = elementsStub();
        ElementsBuffer colors = elementsStub();
        ElementsBuffer normals = elementsStub();
        ElementsBuffer texCoords = elementsStub();
        IndicesBuffer indices = indicesStub();
        IndicesBuffer wire = indicesStub();

        g.setVertices(vertices);
        g.setColors(colors);
        g.setNormals(normals);
        g.setTextureCoordinates(texCoords);
        g.setIndices(indices);
        g.setWireIndices(wire);

        assertSame(vertices, g.getVertices());
        assertSame(colors, g.getColors());
        assertSame(normals, g.getNormals());
        assertSame(texCoords, g.getTextureCoordinates());
        assertSame(indices, g.getIndices());
        assertSame(wire, g.getWireIndices());
    }

    @Test
    public void buffersCanBeClearedBackToNull() {
        DefaultGeometry g = new DefaultGeometry();
        g.setVertices(elementsStub());
        g.setVertices(null);
        assertNull(g.getVertices());
    }

    @Test
    public void faceCullingModeEnumIsComplete() {
        Geometry.FaceCullingMode[] values = Geometry.FaceCullingMode.values();
        assertEquals(3, values.length);
        assertEquals(Geometry.FaceCullingMode.CCW, Geometry.FaceCullingMode.valueOf("CCW"));
        assertEquals(Geometry.FaceCullingMode.BOTH, Geometry.DEFAULT_FACE_CULLING_MODE);
    }

    @Test
    public void fillDrawingModeEnumIsComplete() {
        Geometry.FillDrawingMode[] values = Geometry.FillDrawingMode.values();
        assertEquals(4, values.length);
        assertEquals(Geometry.FillDrawingMode.TRIANGLE_STRIP, Geometry.FillDrawingMode.valueOf("TRIANGLE_STRIP"));
        assertEquals(Geometry.FillDrawingMode.TRIANGLES, Geometry.DEFAULT_FILL_DRAWING_MODE);
    }

    @Test
    public void lineDrawingModeEnumIsComplete() {
        Geometry.LineDrawingMode[] values = Geometry.LineDrawingMode.values();
        assertEquals(4, values.length);
        assertEquals(Geometry.LineDrawingMode.SEGMENTS_STRIP, Geometry.LineDrawingMode.valueOf("SEGMENTS_STRIP"));
        assertEquals(Geometry.LineDrawingMode.NONE, Geometry.DEFAULT_LINE_DRAWING_MODE);
    }
}
