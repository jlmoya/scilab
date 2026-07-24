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

package org.scilab.modules.xcos.modelica.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for the JAXB data-binding class {@link Info}.
 *
 * <p>{@code Info} is a plain generated POJO holding eleven {@link BigInteger}
 * counters (parameter/state/input/output sizes). No native runtime is required.
 */
public class InfoTest {

    @Test
    public void allCountersDefaultToNull() {
        Info info = new Info();

        assertNull(info.getNumberOfIntegerParameters());
        assertNull(info.getNumberOfRealParameters());
        assertNull(info.getNumberOfStringParameters());
        assertNull(info.getNumberOfDiscreteVariables());
        assertNull(info.getNumberOfContinuousVariables());
        assertNull(info.getNumberOfContinuousUnknowns());
        assertNull(info.getNumberOfContinuousStates());
        assertNull(info.getNumberOfInputs());
        assertNull(info.getNumberOfOutputs());
        assertNull(info.getNumberOfModes());
        assertNull(info.getNumberOfZeroCrossings());
    }

    /**
     * Sets every counter to a distinct value and reads each one back. Distinct
     * values guard against field cross-talk (a copy/paste hazard in generated
     * accessors): a getter/setter wired to the wrong field would fail here.
     */
    @Test
    public void eachSetterRoundTripsIndependently() {
        Info info = new Info();

        info.setNumberOfIntegerParameters(BigInteger.valueOf(1));
        info.setNumberOfRealParameters(BigInteger.valueOf(2));
        info.setNumberOfStringParameters(BigInteger.valueOf(3));
        info.setNumberOfDiscreteVariables(BigInteger.valueOf(4));
        info.setNumberOfContinuousVariables(BigInteger.valueOf(5));
        info.setNumberOfContinuousUnknowns(BigInteger.valueOf(6));
        info.setNumberOfContinuousStates(BigInteger.valueOf(7));
        info.setNumberOfInputs(BigInteger.valueOf(8));
        info.setNumberOfOutputs(BigInteger.valueOf(9));
        info.setNumberOfModes(BigInteger.valueOf(10));
        info.setNumberOfZeroCrossings(BigInteger.valueOf(11));

        assertEquals(BigInteger.valueOf(1), info.getNumberOfIntegerParameters());
        assertEquals(BigInteger.valueOf(2), info.getNumberOfRealParameters());
        assertEquals(BigInteger.valueOf(3), info.getNumberOfStringParameters());
        assertEquals(BigInteger.valueOf(4), info.getNumberOfDiscreteVariables());
        assertEquals(BigInteger.valueOf(5), info.getNumberOfContinuousVariables());
        assertEquals(BigInteger.valueOf(6), info.getNumberOfContinuousUnknowns());
        assertEquals(BigInteger.valueOf(7), info.getNumberOfContinuousStates());
        assertEquals(BigInteger.valueOf(8), info.getNumberOfInputs());
        assertEquals(BigInteger.valueOf(9), info.getNumberOfOutputs());
        assertEquals(BigInteger.valueOf(10), info.getNumberOfModes());
        assertEquals(BigInteger.valueOf(11), info.getNumberOfZeroCrossings());
    }

    @Test
    public void setterOverwritesPreviousValue() {
        Info info = new Info();

        info.setNumberOfInputs(BigInteger.TEN);
        info.setNumberOfInputs(BigInteger.valueOf(42));

        assertEquals(BigInteger.valueOf(42), info.getNumberOfInputs());
    }

    @Test
    public void settersAcceptNullClearingAValue() {
        Info info = new Info();
        info.setNumberOfOutputs(BigInteger.ONE);

        info.setNumberOfOutputs(null);

        assertNull(info.getNumberOfOutputs());
    }

    /**
     * The zero counter is preserved exactly (not normalized/coalesced to null).
     */
    @Test
    public void zeroIsStoredAsZeroNotNull() {
        Info info = new Info();

        info.setNumberOfModes(BigInteger.ZERO);

        assertEquals(BigInteger.ZERO, info.getNumberOfModes());
    }

    /**
     * Defect characterization: although the XML schema types these counters as
     * {@code nonNegativeInteger}, the generated setter performs no validation
     * and happily stores a negative value. This locks the current (permissive)
     * behavior so a future change is noticed.
     */
    @Test
    public void setterAcceptsNegativeDespiteNonNegativeIntegerSchema_defectCharacterization() {
        Info info = new Info();

        info.setNumberOfZeroCrossings(BigInteger.valueOf(-5));

        assertEquals(BigInteger.valueOf(-5), info.getNumberOfZeroCrossings());
    }

    /**
     * Very large magnitudes survive intact ({@link BigInteger} is unbounded),
     * so counters above {@code long} range are not truncated.
     */
    @Test
    public void veryLargeCounterIsPreserved() {
        Info info = new Info();
        BigInteger huge = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE);

        info.setNumberOfContinuousStates(huge);

        assertEquals(huge, info.getNumberOfContinuousStates());
    }

    /**
     * Defect/behavior characterization: {@code Info} does not override
     * {@code equals}/{@code hashCode}, so equality is JVM identity. Two
     * instances with identical field values are NOT equal.
     */
    @Test
    public void equalsIsIdentityBased_defectCharacterization() {
        Info a = new Info();
        Info b = new Info();
        a.setNumberOfInputs(BigInteger.ONE);
        b.setNumberOfInputs(BigInteger.ONE);

        assertNotEquals(a, b, "no value semantics: distinct instances must not be equal");
        assertEquals(a, a);
        assertSame(a, a);
        assertEquals(a.hashCode(), a.hashCode(), "hashCode must be stable across calls");
    }
}
