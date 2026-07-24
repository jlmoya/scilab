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

package org.scilab.modules.helptools.c;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link AbstractCCodeHandler}.
 *
 * <p>Every {@code handle*} method on the base class is a no-op; concrete C/C++
 * highlighters (e.g. {@link HTMLCCodeHandler}) override the ones they care about.
 * This test pins that no-op contract with a minimal subclass that overrides
 * nothing — the defaults must neither throw nor produce any observable effect.
 */
public class AbstractCCodeHandlerTest {

    /** Concrete handler overriding nothing — exercises the empty base bodies. */
    private static final class NoopCHandler extends AbstractCCodeHandler { }

    @Test
    public void everyHandleMethodIsANoOpAndDoesNotThrow() {
        NoopCHandler h = new NoopCHandler();
        assertDoesNotThrow(() -> {
            h.handleDefault("x");
            h.handleComment("/* c */");
            h.handleNothing("   ");
            h.handleString("\"s\"");
            h.handleNumber("42");
            h.handleKeyword("for");
            h.handleType("int");
            h.handleModifier("inline");
            h.handlePreprocessor("#define");
            h.handleOpenClose("{");
            h.handleOperator("+");
            h.handleId("foo");
        });
    }

    @Test
    public void noOpBodiesAcceptNullAndEmptyWithoutThrowing() throws IOException {
        NoopCHandler h = new NoopCHandler();
        // The base methods never inspect their argument, so null is harmless.
        h.handleDefault(null);
        h.handleId("");
        // Nothing to assert on output — the point is the absence of any exception.
        assertNotNull(h);
    }
}
