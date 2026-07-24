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
 * Hermetic unit tests for {@link TitlePositioner}. This test sits in the
 * positioner package so it can reach the {@code protected}
 * {@code getAutoAnchorPosition} override directly. The
 * {@code computeDisplacedPosition} algorithm needs live drawing tools and is
 * out of scope; the constructor's window-coordinate opt-in, the fixed anchor,
 * the public offset constant and the inherited state surface are pure and
 * covered here.
 */
class TitlePositionerTest {

    private static void assertVec(Vector3d v, double x, double y, double z) {
        assertEquals(x, v.getX(), 0.0);
        assertEquals(y, v.getY(), 0.0);
        assertEquals(z, v.getZ(), 0.0);
    }

    @Test
    void constructorOptsIntoWindowCoordinates() {
        // The base positioner defaults this flag to false; a title must
        // flip it on to keep the label fixed when the viewpoint changes.
        assertTrue(new TitlePositioner().getUseWindowCoordinates());
    }

    @Test
    void autoAnchorPositionIsAlwaysDown() {
        assertEquals(AnchorPosition.DOWN, new TitlePositioner().getAutoAnchorPosition());
    }

    @Test
    void titleOffsetConstantIsEightPixels() {
        assertEquals(8.0, TitlePositioner.TITLEOFFSET, 0.0);
    }

    @Test
    void inheritsTheBasePositionerDefaults() {
        TitlePositioner p = new TitlePositioner();
        assertFalse(p.getAutoPosition());
        assertFalse(p.getAutoRotation());
        assertEquals(0.0, p.getRotationAngle(), 0.0);
        assertEquals(AnchorPosition.LOWER_LEFT, p.getAnchorPosition());
        assertVec(p.getLabelPosition(), 0.0, 0.0, 0.0);
    }

    @Test
    void inheritedLabelPositionSetterStillStoresADefensiveCopy() {
        TitlePositioner p = new TitlePositioner();
        Vector3d in = new Vector3d(1.0, 2.0, 3.0);
        p.setLabelPosition(in);
        Vector3d stored = p.getLabelPosition();
        assertNotSame(in, stored);
        assertVec(stored, 1.0, 2.0, 3.0);
    }

    @Test
    void configurationSettersAcceptValuesWithoutDisturbingTheWindowFlag() {
        // setDistanceRatio / setXLabelHeight feed the (display-bound)
        // displacement computation; here we only assert they are side-effect
        // free plain setters that leave the constructed state intact.
        TitlePositioner p = new TitlePositioner();
        p.setDistanceRatio(2.5);
        p.setXLabelHeight(17);
        p.setDistanceRatio(-1.0);
        p.setXLabelHeight(0);
        assertTrue(p.getUseWindowCoordinates());
        assertEquals(AnchorPosition.DOWN, p.getAutoAnchorPosition());
    }
}
