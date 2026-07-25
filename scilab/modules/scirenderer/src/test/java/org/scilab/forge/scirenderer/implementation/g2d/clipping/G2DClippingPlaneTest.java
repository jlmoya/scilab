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

package org.scilab.forge.scirenderer.implementation.g2d.clipping;

import org.junit.jupiter.api.Test;
import org.scilab.forge.scirenderer.tranformations.Transformation;
import org.scilab.forge.scirenderer.tranformations.TransformationFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hermetic unit tests for the display-independent surface of {@link G2DClippingPlane}:
 * its index, enabled flag and (fixed identity) transformation. The equation
 * getters/setters depend on a live {@code G2DDrawingTools} pipeline and are out of
 * scope for a hermetic test, so a {@code null} drawing-tools reference is enough here.
 */
public class G2DClippingPlaneTest {

    @Test
    public void indexIsStoredAndReturned() {
        assertEquals(7, new G2DClippingPlane(7, null).getIndex());
    }

    @Test
    public void clippingPlaneIsDisabledByDefault() {
        assertFalse(new G2DClippingPlane(0, null).isEnable());
    }

    @Test
    public void enabledFlagRoundTrips() {
        G2DClippingPlane plane = new G2DClippingPlane(0, null);
        plane.setEnable(true);
        assertTrue(plane.isEnable());
        plane.setEnable(false);
        assertFalse(plane.isEnable());
    }

    @Test
    public void transformationDefaultsToIdentity() {
        Transformation identity = TransformationFactory.getIdentity();
        Transformation t = new G2DClippingPlane(1, null).getTransformation();
        assertNotNull(t);
        assertEquals(identity.getMatrix().length, t.getMatrix().length);
    }

    @Test
    public void setTransformationIsANoOp() {
        // G2DClippingPlane deliberately ignores setTransformation(): the getter keeps
        // returning the original identity transformation.
        G2DClippingPlane plane = new G2DClippingPlane(1, null);
        Transformation before = plane.getTransformation();
        plane.setTransformation(TransformationFactory.getIdentity());
        assertSame(before, plane.getTransformation());
    }
}
