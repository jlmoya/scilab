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

package org.scilab.forge.scirenderer.ruler;

import org.junit.jupiter.api.Test;
import org.scilab.forge.scirenderer.ruler.graduations.Graduations;
import org.scilab.forge.scirenderer.ruler.graduations.LinearGraduations;
import org.scilab.forge.scirenderer.ruler.graduations.LogarithmicGraduations;
import org.scilab.forge.scirenderer.tranformations.Vector3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Hermetic unit tests for {@link DefaultRulerModel}.
 */
public class DefaultRulerModelTest {

    @Test
    public void defaultsComeFromTheRulerModelInterface() {
        DefaultRulerModel m = new DefaultRulerModel();
        assertEquals(RulerModel.DEFAULT_FIRST_VALUE, m.getFirstValue(), 0.0);
        assertEquals(RulerModel.DEFAULT_SECOND_VALUE, m.getSecondValue(), 0.0);
        assertEquals(RulerModel.DEFAULT_TICK_LENGTH, m.getTicksLength());
        assertEquals(RulerModel.DEFAULT_SUB_TICK_LENGTH, m.getSubTicksLength());
        assertEquals(RulerModel.DEFAULT_MARGIN, m.getMargin(), 0.0);
        assertEquals(RulerModel.DEFAULT_SPRITE_DISTANCE, m.getSpriteDistance());
        assertTrue(m.isLineVisible());
        assertTrue(m.isAutoTicks());
        assertFalse(m.isLogarithmic());
        assertEquals(-1, m.getSubticksNumber());
        assertEquals("", m.getFormat());
    }

    @Test
    public void infiniteValuesAreClampedToMaxValue() {
        DefaultRulerModel m = new DefaultRulerModel();
        m.setFirstValue(Double.POSITIVE_INFINITY);
        m.setSecondValue(Double.NEGATIVE_INFINITY);
        assertEquals(Double.MAX_VALUE, m.getFirstValue(), 0.0);
        assertEquals(Double.MAX_VALUE, m.getSecondValue(), 0.0);
    }

    @Test
    public void setValuesSetsBoth() {
        DefaultRulerModel m = new DefaultRulerModel();
        m.setValues(2, 8);
        assertEquals(2, m.getFirstValue(), 0.0);
        assertEquals(8, m.getSecondValue(), 0.0);
    }

    @Test
    public void linearPositionInterpolatesBetweenTheEndpoints() {
        DefaultRulerModel m = new DefaultRulerModel();
        m.setValues(0, 1);
        m.setPoints(new Vector3d(0, 0, 0), new Vector3d(10, 0, 0));
        assertTrue(new Vector3d(0, 0, 0).equals(m.getPosition(0)));
        assertTrue(new Vector3d(5, 0, 0).equals(m.getPosition(0.5)));
        assertTrue(new Vector3d(10, 0, 0).equals(m.getPosition(1)));
    }

    @Test
    public void logarithmicPositionInterpolatesInLogSpace() {
        DefaultRulerModel m = new DefaultRulerModel();
        m.setLogarithmic(true);
        m.setValues(1, 100);
        m.setPoints(new Vector3d(0, 0, 0), new Vector3d(2, 0, 0));
        // log10(10) is midway between log10(1) and log10(100) => midpoint position.
        assertTrue(new Vector3d(1, 0, 0).equals(m.getPosition(10)), "actual: " + m.getPosition(10));
    }

    @Test
    public void positionIsNullWhenAPointIsMissing() {
        DefaultRulerModel m = new DefaultRulerModel();
        m.setFirstPoint(null);
        assertNull(m.getPosition(0.5));
    }

    @Test
    public void autoGraduationsAreLinearByDefaultAndCached() {
        DefaultRulerModel m = new DefaultRulerModel();
        m.setValues(0, 10);
        Graduations g = m.getGraduations();
        assertInstanceOf(LinearGraduations.class, g);
        assertSame(g, m.getGraduations(), "graduations are cached until inputs change");
    }

    @Test
    public void switchingToLogarithmicProducesLogarithmicGraduations() {
        DefaultRulerModel m = new DefaultRulerModel();
        m.setValues(1, 1000);
        Graduations linear = m.getGraduations();
        m.setLogarithmic(true);
        Graduations log = m.getGraduations();
        assertInstanceOf(LogarithmicGraduations.class, log);
        assertNotSame(linear, log);
    }

    @Test
    public void changingAValueInvalidatesCachedGraduations() {
        DefaultRulerModel m = new DefaultRulerModel();
        m.setValues(0, 10);
        Graduations first = m.getGraduations();
        m.setSecondValue(20);
        assertNotSame(first, m.getGraduations());
    }

    @Test
    public void userGraduationsAreReturnedWhenAutoTicksDisabled() {
        DefaultRulerModel m = new DefaultRulerModel();
        m.setAutoTicks(false);
        LinearGraduations user = LinearGraduations.create(0, 5);
        m.setUserGraduation(user);
        assertSame(user, m.getGraduations());
    }

    @Test
    public void scaleTranslateFactors() {
        DefaultRulerModel m = new DefaultRulerModel();
        m.setSTFactors(new Double[] {2.5, -1.5});
        assertEquals(2.5, m.getScale(), 0.0);
        assertEquals(-1.5, m.getTranslate(), 0.0);
    }

    @Test
    public void miscSettersRoundTrip() {
        DefaultRulerModel m = new DefaultRulerModel();
        m.setTicksLength(20);
        m.setSubTicksLength(7);
        m.setSpriteDistance(15);
        m.setLineVisible(false);
        m.setSubticksNumber(4);
        m.setFormat("%.3f");
        m.setLineWidth(2.0);
        m.setMinimalSubTicksDistance(3.0);
        Vector3d dir = new Vector3d(0, 1, 0);
        m.setTicksDirection(dir);

        assertEquals(20, m.getTicksLength());
        assertEquals(7, m.getSubTicksLength());
        assertEquals(15, m.getSpriteDistance());
        assertFalse(m.isLineVisible());
        assertEquals(4, m.getSubticksNumber());
        assertEquals("%.3f", m.getFormat());
        assertEquals(2.0, m.getLineWidth(), 0.0);
        assertEquals(3.0, m.getMinimalSubTicksDistance(), 0.0);
        assertSame(dir, m.getTicksDirection());
    }
}
