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
import org.scilab.forge.scirenderer.tranformations.Vector3d;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.Rectangle2D;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hermetic unit tests for {@link AbstractDrawable3DObject}. The static geometry
 * helpers are exercised directly (this test shares the class's package, so the
 * protected static predicates are reachable); instance behavior is exercised through
 * a minimal anonymous concrete subclass.
 */
public class AbstractDrawable3DObjectTest {

    /** A minimal concrete drawable used to reach the instance methods. */
    private static AbstractDrawable3DObject make(Vector3d[] vertices, Color[] colors) throws InvalidPolygonException {
        return new AbstractDrawable3DObject(vertices, colors) {
            @Override
            public void draw(Graphics2D g2d) {
                // no-op: rendering is out of scope for a hermetic test
            }
        };
    }

    // ----- static color / vector predicates -----

    @Test
    public void isMonochromaticStatic() {
        assertTrue(AbstractDrawable3DObject.isMonochromatic(null));
        assertTrue(AbstractDrawable3DObject.isMonochromatic(new Color[0]));
        assertTrue(AbstractDrawable3DObject.isMonochromatic(new Color[] {Color.RED, Color.RED}));
        assertFalse(AbstractDrawable3DObject.isMonochromatic(new Color[] {Color.RED, Color.BLUE}));
    }

    @Test
    public void isNanOrInfStatic() {
        assertFalse(AbstractDrawable3DObject.isNanOrInf(new Vector3d(1, 2, 3)));
        assertTrue(AbstractDrawable3DObject.isNanOrInf(new Vector3d(Double.NaN, 0, 0)));
        assertTrue(AbstractDrawable3DObject.isNanOrInf(new Vector3d(0, Double.POSITIVE_INFINITY, 0)));
    }

    @Test
    public void isBehindStatic() {
        // M.v + a >= 0
        assertTrue(AbstractDrawable3DObject.isBehind(new Vector3d(1, 0, 0), new Vector3d(1, 0, 0), 0));
        assertFalse(AbstractDrawable3DObject.isBehind(new Vector3d(1, 0, 0), new Vector3d(1, 0, 0), -2));
    }

    // ----- protected static numeric helpers (reachable from the same package) -----

    @Test
    public void nearZeroPredicates() {
        assertTrue(AbstractDrawable3DObject.isNull(1e-9));
        assertFalse(AbstractDrawable3DObject.isNull(1e-7));
        assertTrue(AbstractDrawable3DObject.isEqual(1.0, 1.0 + 1e-9));
        assertFalse(AbstractDrawable3DObject.isEqual(1.0, 1.1));
    }

    @Test
    public void signPredicates() {
        assertTrue(AbstractDrawable3DObject.isPositiveOrNull(0.5));
        assertTrue(AbstractDrawable3DObject.isPositiveOrNull(-1e-9));
        assertFalse(AbstractDrawable3DObject.isPositiveOrNull(-0.5));

        assertTrue(AbstractDrawable3DObject.isNegativeOrNull(-0.5));
        assertTrue(AbstractDrawable3DObject.isNegativeOrNull(1e-9));
        assertFalse(AbstractDrawable3DObject.isNegativeOrNull(0.5));

        assertTrue(AbstractDrawable3DObject.isGreaterOrEqual(2, 1));
        assertFalse(AbstractDrawable3DObject.isGreaterOrEqual(1, 2));
        assertTrue(AbstractDrawable3DObject.isLowerOrEqual(1, 2));
        assertFalse(AbstractDrawable3DObject.isLowerOrEqual(2, 1));
    }

    // ----- static clip helpers -----

    @Test
    public void getClipReturnsNullWhenAnyBoundIsNaN() {
        assertNull(AbstractDrawable3DObject.getClip(new double[] {Double.NaN, Double.NaN, Double.NaN, Double.NaN}));
        assertNull(AbstractDrawable3DObject.getClip(new double[] {0, 1, Double.NaN, 1}));
    }

    @Test
    public void makeClipBuildsARectangleFromFourPlanes() {
        double[] clip = new double[] {Double.NaN, Double.NaN, Double.NaN, Double.NaN};
        AbstractDrawable3DObject.makeClip(clip, new double[] {2, 0, 0, -4}); // vertical plane x = 2
        AbstractDrawable3DObject.makeClip(clip, new double[] {1, 0, 0, -6}); // vertical plane x = 6
        assertNull(AbstractDrawable3DObject.getClip(clip), "still missing the horizontal bounds");
        AbstractDrawable3DObject.makeClip(clip, new double[] {0, 2, 0, -4}); // horizontal plane y = 2
        AbstractDrawable3DObject.makeClip(clip, new double[] {0, 1, 0, -6}); // horizontal plane y = 6

        Shape shape = AbstractDrawable3DObject.getClip(clip);
        Rectangle2D r = (Rectangle2D) shape;
        assertEquals(2.0, r.getX(), 0.0);
        assertEquals(2.0, r.getY(), 0.0);
        assertEquals(4.0, r.getWidth(), 0.0);
        assertEquals(4.0, r.getHeight(), 0.0);
    }

    @Test
    public void makeClipReordersWhenNewPlaneIsSmaller() {
        double[] clip = new double[] {Double.NaN, Double.NaN, Double.NaN, Double.NaN};
        AbstractDrawable3DObject.makeClip(clip, new double[] {1, 0, 0, -5}); // x = 5
        AbstractDrawable3DObject.makeClip(clip, new double[] {1, 0, 0, -2}); // x = 2, smaller => becomes the min
        assertEquals(2.0, clip[0], 0.0);
        assertEquals(5.0, clip[1], 0.0);
    }

    // ----- construction guards -----

    @Test
    public void nullOrEmptyVerticesRejected() {
        assertThrows(InvalidPolygonException.class, () -> make(null, null));
        assertThrows(InvalidPolygonException.class, () -> make(new Vector3d[0], null));
    }

    @Test
    public void duplicateVerticesRejectedAsDegenerate() {
        assertThrows(InvalidPolygonException.class,
                     () -> make(new Vector3d[] {new Vector3d(0, 0, 0), new Vector3d(0, 0, 0)}, null));
    }

    @Test
    public void nonFiniteVerticesRejected() {
        assertThrows(InvalidPolygonException.class,
                     () -> make(new Vector3d[] {new Vector3d(0, 0, 0), new Vector3d(Double.NaN, 0, 0)}, null));
    }

    // ----- instance behavior -----

    @Test
    public void monochromaticColorsAreCollapsed() throws InvalidPolygonException {
        AbstractDrawable3DObject o = make(
                                         new Vector3d[] {new Vector3d(0, 0, 0), new Vector3d(1, 0, 0), new Vector3d(0, 1, 0)},
                                         new Color[] {Color.RED, Color.RED, Color.RED});
        assertTrue(o.isMonochromatic());
        assertEquals(Color.RED, o.getColor(0));
    }

    @Test
    public void differingColorsAreNotMonochromatic() throws InvalidPolygonException {
        AbstractDrawable3DObject o = make(
                                         new Vector3d[] {new Vector3d(0, 0, 0), new Vector3d(1, 0, 0), new Vector3d(0, 1, 0)},
                                         new Color[] {Color.RED, Color.GREEN, Color.BLUE});
        assertFalse(o.isMonochromatic());
        assertEquals(Color.GREEN, o.getColor(1));
    }

    @Test
    public void normalOfAPlanarTriangleIsTheUnitZAxis() throws InvalidPolygonException {
        AbstractDrawable3DObject o = make(
                                         new Vector3d[] {new Vector3d(0, 0, 0), new Vector3d(1, 0, 0), new Vector3d(0, 1, 0)},
                                         new Color[] {Color.RED, Color.RED, Color.RED});
        assertTrue(new Vector3d(0, 0, 1).equals(o.getNormal()), "actual: " + o.getNormal());
        assertTrue(o.isPlanar());
        assertNull(o.getProvidedNormal(), "no explicit normal was provided");
    }

    @Test
    public void boundingBoxSpansTheVertices() throws InvalidPolygonException {
        AbstractDrawable3DObject o = make(
                                         new Vector3d[] {new Vector3d(0, 0, 0), new Vector3d(2, 0, 0), new Vector3d(0, 3, 0)},
                                         new Color[] {Color.RED, Color.RED, Color.RED});
        assertEquals("[0.0;2.0]x[0.0;3.0]x[0.0;0.0]", o.getBBox().toString());
    }

    @Test
    public void precedenceSetterAndReset() throws InvalidPolygonException {
        Vector3d[] v = {new Vector3d(0, 0, 0), new Vector3d(1, 0, 0), new Vector3d(0, 1, 0)};
        Color[] c = {Color.RED, Color.RED, Color.RED};

        AbstractDrawable3DObject o = make(v, c);
        o.setPrecedence(42);
        assertEquals(42, o.getPrecedence());

        AbstractDrawable3DObject.resetDefaultPrecedence();
        AbstractDrawable3DObject first = make(v, c);
        AbstractDrawable3DObject second = make(v, c);
        assertEquals(0, first.getPrecedence());
        assertEquals(1, second.getPrecedence());
    }
}
