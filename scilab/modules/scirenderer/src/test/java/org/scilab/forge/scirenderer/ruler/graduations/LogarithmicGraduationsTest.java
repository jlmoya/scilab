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

package org.scilab.forge.scirenderer.ruler.graduations;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hermetic unit tests for {@link LogarithmicGraduations}.
 */
public class LogarithmicGraduationsTest {

    @Test
    public void createNormalizesBoundOrder() {
        LogarithmicGraduations g = LogarithmicGraduations.create(1000, 1);
        assertEquals(1.0, g.getLowerBound(), 0.0);
        assertEquals(1000.0, g.getUpperBound(), 0.0);
    }

    @Test
    public void allValuesArePowersOfTen() {
        List<Double> values = LogarithmicGraduations.create(1, 1000).getAllValues();
        assertEquals(List.of(1.0, 10.0, 100.0, 1000.0), values);
    }

    @Test
    public void newValuesEqualAllValues() {
        LogarithmicGraduations g = LogarithmicGraduations.create(1, 1000);
        assertEquals(g.getAllValues(), g.getNewValues());
    }

    @Test
    public void containRespectsBounds() {
        LogarithmicGraduations g = LogarithmicGraduations.create(1, 1000);
        assertTrue(g.contain(1));
        assertTrue(g.contain(500));
        assertTrue(g.contain(1000));
        assertFalse(g.contain(0.5));
        assertFalse(g.contain(2000));
    }

    @Test
    public void rootHasNoParent() {
        assertNull(LogarithmicGraduations.create(1, 1000).getParentGraduations());
    }

    @Test
    public void toStringExposesStepAndParent() {
        String s = LogarithmicGraduations.create(1, 1000).toString();
        assertTrue(s.startsWith("LogarithmicGraduations"), s);
        assertTrue(s.contains("stepExponent=1"), s);
        assertTrue(s.contains("parent=null"), s);
    }
}
