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

package org.scilab.modules.console;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.io.StringWriter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.artenum.rosetta.interfaces.core.GenericInterpreter;

/**
 * Hermetic characterization tests for {@link SciInterpreter}.
 *
 * <p>{@code SciInterpreter} is the rosetta {@link GenericInterpreter} the console
 * plugs into Scilab, but only {@code eval(String)} is actually wired to the
 * native interpreter queue (via {@code InterpreterManagement}); every other
 * method of the interface is an unimplemented stub. These tests pin down that
 * current contract — the constructor is side-effect free and the stubbed
 * accessors/mutators return {@code null} / do nothing — without ever calling the
 * native {@code eval(String)} path.
 */
public class SciInterpreterTest {

    private SciInterpreter interpreter;

    @BeforeEach
    public void setUp() {
        interpreter = new SciInterpreter();
    }

    @Test
    public void isAGenericInterpreter() {
        assertTrue(interpreter instanceof GenericInterpreter);
    }

    @Test
    public void evalFromAReaderIsAStubReturningNull() throws Exception {
        // Only eval(String) reaches Scilab; the Reader overload is unimplemented.
        assertNull(interpreter.eval(new StringReader("disp(1)")));
    }

    @Test
    public void getByKeyIsAStubReturningNull() {
        assertNull(interpreter.get("anyVariable"));
    }

    @Test
    public void theThreeStreamAccessorsAreStubsReturningNull() {
        assertNull(interpreter.getErrorWriter());
        assertNull(interpreter.getReader());
        assertNull(interpreter.getWriter());
    }

    @Test
    public void theMutatorsAreNoOpsThatAcceptRealArgumentsWithoutThrowing() {
        assertDoesNotThrow(() -> {
            interpreter.put("key", new Object());
            interpreter.setErrorWriter(new StringWriter());
            interpreter.setReader(new StringReader("x"));
            interpreter.setWriter(new StringWriter());
        });
    }

    @Test
    public void theMutatorsAlsoTolerateNullArguments() {
        assertDoesNotThrow(() -> {
            interpreter.put(null, null);
            interpreter.setErrorWriter(null);
            interpreter.setReader(null);
            interpreter.setWriter(null);
        });
    }
}
