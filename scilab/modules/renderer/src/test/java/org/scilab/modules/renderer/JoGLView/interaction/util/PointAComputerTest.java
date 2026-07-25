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

package org.scilab.modules.renderer.JoGLView.interaction.util;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Point;

import org.junit.jupiter.api.Test;
import org.scilab.forge.scirenderer.tranformations.Vector3d;
import org.scilab.modules.graphic_objects.axes.Axes;

/**
 * Hermetic unit tests for {@link PointAComputer}, which drives its base
 * {@link CubeFacesPointComputer} to find the box-face point under a click.
 *
 * <p>The whole face-search runs through the constructor. With a fresh
 * {@link Axes} and no registered {@code DrawerVisitor}, the unprojection
 * basis collapses to the origin, so the interpolation lambdas are
 * non-finite. The class is written to tolerate that: it still completes and
 * returns a (degenerate, NaN-bearing) position rather than throwing. These
 * tests pin down that documented no-projection behaviour together with the
 * {@code PointComputer} contract wiring.
 */
class PointAComputerTest {

    private static boolean hasNaN(Vector3d v) {
        double[] d = v.getData();
        return Double.isNaN(d[0]) || Double.isNaN(d[1]) || Double.isNaN(d[2]);
    }

    @Test
    void constructionNeverThrowsEvenWithoutALiveProjection() {
        assertDoesNotThrow(() -> new PointAComputer(new Axes(), new Point(25, 60)));
    }

    @Test
    void aComputedPointIsAlwaysReportedAs3D() {
        PointAComputer a = new PointAComputer(new Axes(), new Point(25, 60));
        assertFalse(a.is2D(), "PointAComputer is fixed at 3D mode");
    }

    @Test
    void firstAndSecondPositionAliasTheSingleComputedPosition() {
        PointAComputer a = new PointAComputer(new Axes(), new Point(25, 60));
        assertNotNull(a.getPosition());
        assertSame(a.getPosition(), a.getFirstPosition(), "point A exposes one position as 'first'");
        assertSame(a.getPosition(), a.getSecondPosition(), "and the same one as 'second'");
    }

    @Test
    void firstAxisIndexMirrorsTheChosenBoxAxis() {
        PointAComputer a = new PointAComputer(new Axes(), new Point(25, 60));
        int idx = a.getFirstAxisIndex();
        assertTrue(idx >= 0 && idx < 3, "the chosen axis index must name one of the three axes");
    }

    @Test
    void isValidTracksWhetherAPositionWasProduced() {
        PointAComputer a = new PointAComputer(new Axes(), new Point(25, 60));
        // getPosition() is non-null here, so the computer reports valid.
        assertEquals(a.getPosition() != null, a.isValid());
        assertTrue(a.isValid());
    }

    @Test
    void withoutAProjectionTheResultingPositionIsDegenerate() {
        // Documents that a click with no live rendering context yields an
        // unusable (NaN-bearing) box point rather than a crash.
        PointAComputer a = new PointAComputer(new Axes(), new Point(25, 60));
        assertEquals(3, a.getPosition().getData().length);
        assertTrue(hasNaN(a.getPosition()),
                   "a collapsed projection basis leaves NaN components");
    }
}
