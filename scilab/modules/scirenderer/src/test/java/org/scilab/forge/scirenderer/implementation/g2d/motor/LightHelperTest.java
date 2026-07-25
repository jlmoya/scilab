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

package org.scilab.forge.scirenderer.implementation.g2d.motor;

import org.junit.jupiter.api.Test;
import org.scilab.forge.scirenderer.implementation.g2d.lighting.G2DLight;
import org.scilab.forge.scirenderer.shapes.appearance.Material;
import org.scilab.forge.scirenderer.tranformations.Vector3d;
import org.scilab.forge.scirenderer.tranformations.Vector3f;

import java.awt.Color;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Hermetic unit tests for {@link LightHelper}, the pure-math helper behind the
 * Graphics2D software lighting pipeline (buffer unpacking, affine transforms and
 * the ambient / diffuse / specular colour accumulation).
 *
 * <p>The lighting maths uses {@link java.awt.Color}; the scirenderer appearance
 * {@code Color} extends {@code java.awt.Color}, so both interchange freely.</p>
 */
public class LightHelperTest {

    private static final float EPS = 1.0e-6f;

    /** scirenderer appearance colour (a java.awt.Color subclass) for the light / material API. */
    private static org.scilab.forge.scirenderer.shapes.appearance.Color sci(float r, float g, float b) {
        return new org.scilab.forge.scirenderer.shapes.appearance.Color(r, g, b);
    }

    private static void assertRGB(Color c, float r, float g, float b) {
        float[] comp = c.getRGBColorComponents(null);
        assertEquals(r, comp[0], EPS, "red");
        assertEquals(g, comp[1], EPS, "green");
        assertEquals(b, comp[2], EPS, "blue");
    }

    private static void assertVector(Vector3f v, float x, float y, float z) {
        assertEquals(x, v.getX(), EPS, "x");
        assertEquals(y, v.getY(), EPS, "y");
        assertEquals(z, v.getZ(), EPS, "z");
    }

    private static FloatBuffer floats(float... values) {
        FloatBuffer b = FloatBuffer.allocate(values.length);
        b.put(values);
        b.rewind();
        return b;
    }

    private static IntBuffer ints(int... values) {
        IntBuffer b = IntBuffer.allocate(values.length);
        b.put(values);
        b.rewind();
        return b;
    }

    // ----------------------------------------------------------------- getVector3f

    @Test
    public void getVector3fRejectsNullBuffer() {
        assertNull(LightHelper.getVector3f(null, 3));
    }

    @Test
    public void getVector3fRejectsStrideBelowThree() {
        assertNull(LightHelper.getVector3f(floats(1, 2, 3), 2));
    }

    @Test
    public void getVector3fReadsASingleVectorFromArrayBackedBuffer() {
        Vector3f[] r = LightHelper.getVector3f(floats(1, 2, 3), 3);
        assertEquals(1, r.length);
        assertVector(r[0], 1, 2, 3);
    }

    @Test
    public void getVector3fUsesOnlyTheFirstThreeComponentsOfAStride4Vector() {
        Vector3f[] r = LightHelper.getVector3f(floats(5, 6, 7, 1), 4);
        assertEquals(1, r.length);
        assertVector(r[0], 5, 6, 7);
    }

    @Test
    public void getVector3fHandlesABufferWithoutABackingArray() {
        // A read-only view reports hasArray() == false, exercising the copy branch.
        FloatBuffer readOnly = floats(9, 8, 7).asReadOnlyBuffer();
        Vector3f[] r = LightHelper.getVector3f(readOnly, 3);
        assertEquals(1, r.length);
        assertVector(r[0], 9, 8, 7);
    }

    @Test
    public void getVector3fOnEmptyBufferReturnsEmptyArray() {
        Vector3f[] r = LightHelper.getVector3f(FloatBuffer.allocate(0), 3);
        assertEquals(0, r.length);
    }

    /**
     * Defect characterization: {@code getVector3f} sizes the result as
     * {@code floats.length / stride} but then writes to {@code ret[i]} using the
     * raw float offset {@code i} (which advances by {@code stride}) instead of the
     * vector index {@code i / stride}. For any buffer holding more than one vector
     * the second write lands out of bounds. This test pins the current (buggy)
     * behaviour so a future fix will visibly flip it.
     */
    @Test
    public void getVector3fThrowsForMoreThanOneVectorDueToIndexingDefect() {
        FloatBuffer twoVectors = floats(1, 2, 3, 4, 5, 6);
        assertThrows(ArrayIndexOutOfBoundsException.class,
                     () -> LightHelper.getVector3f(twoVectors, 3));
    }

    // ---------------------------------------------------------- getIndexedVector3f

    @Test
    public void getIndexedVector3fRejectsNullInputs() {
        assertNull(LightHelper.getIndexedVector3f(null, ints(0), 3, null));
        assertNull(LightHelper.getIndexedVector3f(floats(1, 2, 3), null, 3, null));
        assertNull(LightHelper.getIndexedVector3f(floats(1, 2, 3), ints(0), 2, null));
    }

    @Test
    public void getIndexedVector3fGathersVerticesByIndexInOrder() {
        FloatBuffer vertices = floats(10, 11, 12, 20, 21, 22);
        Vector3f[] r = LightHelper.getIndexedVector3f(vertices, ints(1, 0), 3, null);
        assertEquals(2, r.length);
        assertVector(r[0], 20, 21, 22);
        assertVector(r[1], 10, 11, 12);
    }

    @Test
    public void getIndexedVector3fAppliesTheTransformationMatrix() {
        // Column-major translate by (100, 200, 300).
        float[] translate = {
            1, 0, 0, 0,
            0, 1, 0, 0,
            0, 0, 1, 0,
            100, 200, 300, 1
        };
        FloatBuffer vertices = floats(1, 2, 3);
        Vector3f[] r = LightHelper.getIndexedVector3f(vertices, ints(0), 3, translate);
        assertEquals(1, r.length);
        assertVector(r[0], 101, 202, 303);
    }

    @Test
    public void getIndexedVector3fIgnoresATransformOfWrongLength() {
        // A 15-element array is not 16, so the plain (untransformed) path is taken.
        float[] wrong = new float[15];
        Vector3f[] r = LightHelper.getIndexedVector3f(floats(4, 5, 6), ints(0), 3, wrong);
        assertVector(r[0], 4, 5, 6);
    }

    @Test
    public void getIndexedVector3fHandlesBuffersWithoutBackingArrays() {
        FloatBuffer vertices = floats(7, 8, 9).asReadOnlyBuffer();
        IntBuffer index = ints(0).asReadOnlyBuffer();
        Vector3f[] r = LightHelper.getIndexedVector3f(vertices, index, 3, null);
        assertEquals(1, r.length);
        assertVector(r[0], 7, 8, 9);
    }

    // --------------------------------------------------------- transform / reflect

    @Test
    public void transformAppliesRotationScaleAndTranslation() {
        float[] m = {
            2, 0, 0, 0,
            0, 2, 0, 0,
            0, 0, 2, 0,
            10, 20, 30, 1
        };
        assertVector(LightHelper.transform(1, 2, 3, m), 12, 24, 36);
    }

    @Test
    public void transformDirectionIgnoresTheTranslationColumn() {
        float[] m = {
            2, 0, 0, 0,
            0, 2, 0, 0,
            0, 0, 2, 0,
            10, 20, 30, 1
        };
        assertVector(LightHelper.transformDirection(1, 2, 3, m), 2, 4, 6);
    }

    @Test
    public void reflectMirrorsTheIncidentVectorAboutTheNormal() {
        // reflect(I, N) = I - N * (2 * I.N); for I=(1,-1,0), N=(0,1,0) -> (1,1,0).
        Vector3f reflected = LightHelper.reflect(new Vector3f(1, -1, 0), new Vector3f(0, 1, 0));
        assertVector(reflected, 1, 1, 0);
    }

    // -------------------------------------------------------------- applyAmbient

    @Test
    public void applyAmbientReplacesEveryOutputColourWhenNotAdditive() {
        Color ambient = new Color(0.2f, 0.4f, 0.6f);
        Color[] output = { new Color(1f, 1f, 1f), new Color(0f, 0f, 0f) };
        Color[] result = LightHelper.applyAmbient(ambient, output, false);
        assertSame(output, result);
        assertRGB(result[0], 0.2f, 0.4f, 0.6f);
        assertRGB(result[1], 0.2f, 0.4f, 0.6f);
    }

    @Test
    public void applyAmbientAddsAndClampsWhenAdditive() {
        Color ambient = new Color(0.2f, 0.2f, 0.2f);
        Color[] output = { new Color(0.9f, 0.5f, 0.0f) };
        LightHelper.applyAmbient(ambient, output, true);
        // (1.1 -> clamp 1.0, 0.7, 0.2)
        assertRGB(output[0], 1.0f, 0.7f, 0.2f);
    }

    @Test
    public void applyAmbientWithInputMultipliesWhenNotAdditive() {
        Color ambient = new Color(0.5f, 0.5f, 0.5f);
        Color[] input = { new Color(0.4f, 0.6f, 0.8f) };
        Color[] output = { new Color(0f, 0f, 0f) };
        LightHelper.applyAmbient(ambient, input, output, false);
        assertRGB(output[0], 0.2f, 0.3f, 0.4f);
    }

    @Test
    public void applyAmbientWithInputAddsProductWhenAdditive() {
        Color ambient = new Color(0.5f, 0.5f, 0.5f);
        Color[] input = { new Color(0.4f, 0.6f, 0.8f) };
        Color[] output = { new Color(0.1f, 0.1f, 0.1f) };
        LightHelper.applyAmbient(ambient, input, output, true);
        // product (0.2,0.3,0.4) + (0.1,0.1,0.1) = (0.3,0.4,0.5)
        assertRGB(output[0], 0.3f, 0.4f, 0.5f);
    }

    // -------------------------------------------------------------- applyDiffuse

    @Test
    public void applyDiffuseDirectionalWithPerVertexColours() {
        Vector3f light = new Vector3f(0, 0, 1);
        Vector3f[] normals = { new Vector3f(0, 0, 1) };
        Color[] colors = { new Color(1f, 1f, 1f) };
        Color diffuse = new Color(0.4f, 0.4f, 0.4f);
        Color[] output = { new Color(0f, 0f, 0f) };

        LightHelper.applyDiffuse(light, true, null, normals, colors, diffuse, output, false);
        // ndotl = 1 -> white * diffuse * 1
        assertRGB(output[0], 0.4f, 0.4f, 0.4f);
    }

    @Test
    public void applyDiffusePointLightUsesTheRayToTheVertex() {
        Vector3f lightPos = new Vector3f(0, 0, 2);
        Vector3f[] vertices = { new Vector3f(0, 0, 0) };
        Vector3f[] normals = { new Vector3f(0, 0, 1) };
        Color color = new Color(0.6f, 0.6f, 0.6f);
        Color[] output = { new Color(0f, 0f, 0f) };

        LightHelper.applyDiffuse(lightPos, false, vertices, normals, color, output, false);
        // ray = (0,0,1) normalized; ndotl = 1 -> colour unchanged
        assertRGB(output[0], 0.6f, 0.6f, 0.6f);
    }

    @Test
    public void applyDiffuseClampsNegativeDotToZero() {
        Vector3f light = new Vector3f(0, 0, -1);
        Vector3f[] normals = { new Vector3f(0, 0, 1) };
        Color color = new Color(0.6f, 0.6f, 0.6f);
        Color[] output = { new Color(0f, 0f, 0f) };

        LightHelper.applyDiffuse(light, true, null, normals, color, output, false);
        // ndotl = -1 -> clamped to 0 -> black
        assertRGB(output[0], 0f, 0f, 0f);
    }

    // ------------------------------------------------------------- applySpecular

    @Test
    public void applySpecularProducesFullHighlightWhenAligned() {
        Vector3f camera = new Vector3f(0, 0, 1);
        Vector3f light = new Vector3f(0, 0, 1);
        Vector3f[] vertices = { new Vector3f(0, 0, 0) };
        Vector3f[] normals = { new Vector3f(0, 0, 1) };
        Color specular = new Color(0.5f, 0.5f, 0.5f);
        Color[] output = { new Color(0f, 0f, 0f) };

        LightHelper.applySpecular(camera, light, 1.0f, true, vertices, normals, specular, output, false);
        assertRGB(output[0], 0.5f, 0.5f, 0.5f);
    }

    @Test
    public void applySpecularIsBlackWhenTheLightIsBehindTheSurface() {
        Vector3f camera = new Vector3f(0, 0, 1);
        Vector3f light = new Vector3f(0, 0, -1);
        Vector3f[] vertices = { new Vector3f(0, 0, 0) };
        Vector3f[] normals = { new Vector3f(0, 0, 1) };
        Color specular = new Color(0.5f, 0.5f, 0.5f);
        Color[] output = { new Color(0f, 0f, 0f) };

        LightHelper.applySpecular(camera, light, 1.0f, true, vertices, normals, specular, output, false);
        // ndotl <= 0 -> s stays 0 -> no highlight
        assertRGB(output[0], 0f, 0f, 0f);
    }

    // ---------------------------------------------------------------- applyLight

    @Test
    public void applyLightWithPointLightReturnsThePopulatedOutputArray() {
        G2DLight light = new G2DLight(0);
        light.setAmbientColor(sci(0.1f, 0.1f, 0.1f));
        light.setDiffuseColor(sci(0.4f, 0.4f, 0.4f));
        light.setSpecularColor(sci(0.5f, 0.5f, 0.5f));
        light.setPosition(new Vector3d(0, 0, 1));   // point light

        Material mat = new Material();
        mat.setAmbientColor(sci(0.2f, 0.2f, 0.2f));
        mat.setDiffuseColor(sci(0.6f, 0.6f, 0.6f));
        mat.setSpecularColor(sci(0.3f, 0.3f, 0.3f));
        mat.setShininess(8f);

        Vector3f camera = new Vector3f(0, 0, 5);
        Vector3f[] vertices = { new Vector3f(0, 0, 0) };
        Vector3f[] normals = { new Vector3f(0, 0, 1) };
        Color[] colors = { new Color(1f, 1f, 1f) };
        Color[] output = { new Color(0f, 0f, 0f) };

        Color[] result = LightHelper.applyLight(light, mat, camera, vertices, normals, colors, output, null, false);
        assertSame(output, result);
        assertNotNull(result[0]);
    }

    @Test
    public void applyLightWithDirectionalLightTransformAndPlainMaterial() {
        G2DLight light = new G2DLight(1);
        light.setAmbientColor(sci(0.1f, 0.1f, 0.1f));
        light.setDiffuseColor(sci(0.4f, 0.4f, 0.4f));
        light.setSpecularColor(sci(0.5f, 0.5f, 0.5f));
        light.setDirection(new Vector3d(0, 0, -1));  // directional light

        Material mat = new Material();
        mat.setColorMaterialEnable(false);           // exercise the non-colour-material path
        mat.setAmbientColor(sci(0.2f, 0.2f, 0.2f));
        mat.setDiffuseColor(sci(0.6f, 0.6f, 0.6f));
        mat.setSpecularColor(sci(0.3f, 0.3f, 0.3f));
        mat.setShininess(4f);

        float[] identity = {
            1, 0, 0, 0,
            0, 1, 0, 0,
            0, 0, 1, 0,
            0, 0, 0, 1
        };

        Vector3f camera = new Vector3f(0, 0, 5);
        Vector3f[] vertices = { new Vector3f(0, 0, 0) };
        Vector3f[] normals = { new Vector3f(0, 0, 1) };
        Color[] colors = { new Color(1f, 1f, 1f) };
        Color[] output = { new Color(0f, 0f, 0f) };

        Color[] result = LightHelper.applyLight(light, mat, camera, vertices, normals, colors, output, identity, false);
        assertSame(output, result);
        assertNotNull(result[0]);
    }
}
