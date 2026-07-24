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

package org.scilab.modules.renderer.JoGLView.label;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.scilab.forge.scirenderer.texture.AnchorPosition;
import org.scilab.forge.scirenderer.tranformations.Vector3d;

/**
 * Hermetic unit tests for {@link LabelPositioner}'s state surface. The
 * class is abstract but declares no abstract methods, so a trivial
 * subclass instantiates it. The geometry-computing methods
 * ({@code positionLabel}, {@code getLowerLeftCornerPosition}) need live
 * drawing tools and are intentionally out of scope; the accessors and
 * their defensive copying are pure and covered here.
 */
class LabelPositionerTest {

    /** Minimal concrete positioner: inherits every default behaviour. */
    private static final class TestableLabelPositioner extends LabelPositioner {
    }

    private static void assertVec(Vector3d v, double x, double y, double z) {
        assertEquals(x, v.getX(), 0.0);
        assertEquals(y, v.getY(), 0.0);
        assertEquals(z, v.getZ(), 0.0);
    }

    @Test
    void constructorEstablishesDocumentedDefaults() {
        LabelPositioner p = new TestableLabelPositioner();
        assertFalse(p.getAutoPosition());
        assertFalse(p.getAutoRotation());
        assertEquals(0.0, p.getRotationAngle(), 0.0);
        assertEquals(0.0, p.getUserRotationAngle(), 0.0);
        assertFalse(p.getUseWindowCoordinates());
        assertEquals(AnchorPosition.LOWER_LEFT, p.getAnchorPosition());
        assertVec(p.getLabelPosition(), 0.0, 0.0, 0.0);
        assertVec(p.getLabelUserPosition(), 0.0, 0.0, 0.0);
        assertVec(p.getLabelDisplacement(), 0.0, 0.0, 0.0);
        assertVec(p.getAnchorPoint(), 0.0, 0.0, 0.0);
        assertEquals(4, p.getProjCorners().length);
    }

    @Test
    void booleanFlagsRoundTrip() {
        LabelPositioner p = new TestableLabelPositioner();
        p.setAutoPosition(true);
        p.setAutoRotation(true);
        assertTrue(p.getAutoPosition());
        assertTrue(p.getAutoRotation());
    }

    @Test
    void rotationAnglesRoundTrip() {
        LabelPositioner p = new TestableLabelPositioner();
        p.setRotationAngle(45.0);
        p.setUserRotationAngle(-30.0);
        assertEquals(45.0, p.getRotationAngle(), 0.0);
        assertEquals(-30.0, p.getUserRotationAngle(), 0.0);
    }

    @Test
    void setLabelPositionStoresADefensiveCopy() {
        LabelPositioner p = new TestableLabelPositioner();
        Vector3d in = new Vector3d(1.0, 2.0, 3.0);
        p.setLabelPosition(in);
        Vector3d stored = p.getLabelPosition();
        assertNotSame(in, stored, "must not alias the caller's vector");
        assertVec(stored, 1.0, 2.0, 3.0);
    }

    @Test
    void setLabelUserPositionStoresADefensiveCopy() {
        LabelPositioner p = new TestableLabelPositioner();
        Vector3d in = new Vector3d(-4.0, 5.0, -6.0);
        p.setLabelUserPosition(in);
        Vector3d stored = p.getLabelUserPosition();
        assertNotSame(in, stored);
        assertVec(stored, -4.0, 5.0, -6.0);
    }

    @Test
    void setLabelDisplacementStoresADefensiveCopy() {
        LabelPositioner p = new TestableLabelPositioner();
        Vector3d in = new Vector3d(7.0, 8.0, 9.0);
        p.setLabelDisplacement(in);
        Vector3d stored = p.getLabelDisplacement();
        assertNotSame(in, stored);
        assertVec(stored, 7.0, 8.0, 9.0);
    }
}
