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

package org.scilab.modules.xcos.palette.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link VariablePath}.
 *
 * <p>
 * A {@code VariablePath} is a {@code (variable, path)} pair whose
 * {@link VariablePath#getEvaluatedPath()} resolves the environment variable at
 * runtime and concatenates its value with the stored path. The tests that
 * touch the environment read the very same {@link System#getenv} value they
 * assert against (and guard with {@link Assumptions}), so they remain
 * deterministic and self-contained.
 */
public class VariablePathTest {

    @Test
    public void freshInstanceHasNullPathAndVariable() {
        VariablePath vp = new VariablePath();
        assertNull(vp.getPath());
        assertNull(vp.getVariable());
    }

    @Test
    public void pathRoundTrips() {
        VariablePath vp = new VariablePath();
        vp.setPath("/palettes/Commonly.xml");
        assertEquals("/palettes/Commonly.xml", vp.getPath());

        vp.setPath(null);
        assertNull(vp.getPath());
    }

    @Test
    public void variableRoundTrips() {
        VariablePath vp = new VariablePath();
        vp.setVariable("SCI");
        assertEquals("SCI", vp.getVariable());

        vp.setVariable(null);
        assertNull(vp.getVariable());
    }

    @Test
    public void evaluatedPathIsJustThePathWhenVariableIsNull() {
        VariablePath vp = new VariablePath();
        vp.setPath("/absolute/path");
        assertEquals("/absolute/path", vp.getEvaluatedPath());
    }

    @Test
    public void evaluatedPathPrependsEnvironmentVariableValue() {
        final String var = "PATH";
        final String envValue = System.getenv(var);
        Assumptions.assumeTrue(envValue != null, "requires the PATH environment variable to be set");

        VariablePath vp = new VariablePath();
        vp.setVariable(var);
        vp.setPath("/suffix");

        assertEquals(envValue + "/suffix", vp.getEvaluatedPath());
    }

    @Test
    public void toStringDelegatesToEvaluatedPath() {
        VariablePath vp = new VariablePath();
        vp.setPath("/some/path");

        assertEquals(vp.getEvaluatedPath(), vp.toString());
        assertEquals("/some/path", vp.toString());
    }

    /**
     * Defect characterization: {@link VariablePath#getEvaluatedPath()} appends
     * {@code getPath()} to a {@link StringBuilder} with no null guard, so a
     * default-constructed instance (path == null, variable == null) yields the
     * literal string {@code "null"} rather than an empty string or {@code null}.
     */
    @Test
    public void evaluatedPathOfEmptyInstanceIsLiteralNull_defectCharacterization() {
        VariablePath vp = new VariablePath();
        assertEquals("null", vp.getEvaluatedPath());
        assertEquals("null", vp.toString());
    }

    /**
     * Defect characterization: an unresolved environment variable makes
     * {@link System#getenv} return {@code null}, which
     * {@link StringBuilder#append(String)} renders as the literal {@code "null"}
     * prefix rather than treating it as empty.
     */
    @Test
    public void evaluatedPathOfUnsetVariableGetsLiteralNullPrefix_defectCharacterization() {
        final String missing = "SCILAB_VARIABLEPATH_TEST_DOES_NOT_EXIST_9Z7Q";
        Assumptions.assumeTrue(System.getenv(missing) == null, "the sentinel env var must be unset");

        VariablePath vp = new VariablePath();
        vp.setVariable(missing);
        vp.setPath("/x");

        assertEquals("null/x", vp.getEvaluatedPath());
    }
}
