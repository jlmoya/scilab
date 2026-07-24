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

package org.scilab.modules.gui.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Hermetic unit tests for {@link Debug}.
 *
 * <p>{@code Debug} is a pair of static {@code DEBUG(...)} logging helpers gated
 * by the compile-time constant {@code enableDebug}, which ships as {@code false}.
 * These tests characterise that shipped state: both overloads are silent no-ops
 * and, because the guarded branch never runs, they never dereference their
 * arguments (null is safe). {@code System.err} is captured around each call and
 * always restored.</p>
 */
public class DebugTest {

    private final PrintStream originalErr = System.err;

    @AfterEach
    public void restoreErr() {
        System.setErr(originalErr);
    }

    /** Runs {@code r} with {@code System.err} redirected and returns what it wrote. */
    private static String captureErr(Runnable r) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream capturing = new PrintStream(baos, true, StandardCharsets.UTF_8);
        PrintStream previous = System.err;
        System.setErr(capturing);
        try {
            r.run();
        } finally {
            capturing.flush();
            System.setErr(previous);
        }
        return new String(baos.toByteArray(), StandardCharsets.UTF_8);
    }

    @Test
    public void twoArgDebugProducesNoOutputWhenDisabled() {
        // Characterisation: enableDebug is false, so DEBUG is a silent sink.
        assertEquals("", captureErr(() -> Debug.DEBUG("SomeClass", "a message")));
    }

    @Test
    public void oneArgDebugProducesNoOutputWhenDisabled() {
        assertEquals("", captureErr(() -> Debug.DEBUG("a message")));
    }

    @Test
    public void debugDoesNotThrowOnNullArguments() {
        // The disabled guard means the null args are never concatenated / dereferenced.
        assertDoesNotThrow(() -> Debug.DEBUG(null, null));
        assertDoesNotThrow(() -> Debug.DEBUG((String) null));
    }

    @Test
    public void nullArgumentsStillProduceNoOutput() {
        assertEquals("", captureErr(() -> Debug.DEBUG(null, null)));
        assertEquals("", captureErr(() -> Debug.DEBUG((String) null)));
    }

    @Test
    public void debugExposesAnImplicitPublicConstructor() {
        // The utility is used purely via its static methods, but the class keeps
        // the default public constructor; constructing it must not fail.
        assertNotNull(new Debug());
    }
}
