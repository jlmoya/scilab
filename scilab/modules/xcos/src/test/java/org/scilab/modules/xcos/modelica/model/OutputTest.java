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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for the JAXB data-binding class {@link Output} and its
 * nested {@link Output.Dependencies} type. No native runtime is required.
 */
public class OutputTest {

    @Test
    public void newOutputHasAllFieldsNull() {
        Output output = new Output();

        assertNull(output.getName());
        assertNull(output.getOrder());
        assertNull(output.getDependencies());
    }

    @Test
    public void nameRoundTrips() {
        Output output = new Output();

        output.setName("y1");

        assertEquals("y1", output.getName());
    }

    @Test
    public void orderRoundTrips() {
        Output output = new Output();

        output.setOrder(BigInteger.valueOf(3));

        assertEquals(BigInteger.valueOf(3), output.getOrder());
    }

    @Test
    public void dependenciesRoundTripsPreservingIdentity() {
        Output output = new Output();
        Output.Dependencies deps = new Output.Dependencies();

        output.setDependencies(deps);

        assertSame(deps, output.getDependencies());
    }

    @Test
    public void settersAcceptNullClearingValues() {
        Output output = new Output();
        output.setName("y1");
        output.setOrder(BigInteger.ONE);
        output.setDependencies(new Output.Dependencies());

        output.setName(null);
        output.setOrder(null);
        output.setDependencies(null);

        assertNull(output.getName());
        assertNull(output.getOrder());
        assertNull(output.getDependencies());
    }

    /**
     * Defect characterization: {@code order} is schema-typed as
     * {@code nonNegativeInteger}, but the generated setter does not validate and
     * stores a negative value unchanged.
     */
    @Test
    public void orderAcceptsNegativeDespiteNonNegativeIntegerSchema_defectCharacterization() {
        Output output = new Output();

        output.setOrder(BigInteger.valueOf(-1));

        assertEquals(BigInteger.valueOf(-1), output.getOrder());
    }

    @Test
    public void dependenciesVariableDefaultsToNull() {
        Output.Dependencies deps = new Output.Dependencies();

        assertNull(deps.getVariable());
    }

    @Test
    public void dependenciesVariableRoundTrips() {
        Output.Dependencies deps = new Output.Dependencies();

        deps.setVariable("x");

        assertEquals("x", deps.getVariable());
    }

    /**
     * End-to-end assembly: a fully-populated {@code Output} reads back its whole
     * graph (name, order, and the nested dependency variable).
     */
    @Test
    public void fullyPopulatedOutputIsReadableEndToEnd() {
        Output output = new Output();
        output.setName("theta");
        output.setOrder(BigInteger.TWO);
        Output.Dependencies deps = new Output.Dependencies();
        deps.setVariable("omega");
        output.setDependencies(deps);

        assertEquals("theta", output.getName());
        assertEquals(BigInteger.TWO, output.getOrder());
        assertEquals("omega", output.getDependencies().getVariable());
    }
}
