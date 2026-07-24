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

package org.scilab.modules.helptools.scilab;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import org.scilab.modules.helptools.scilab.AbstractScilabCodeHandler.LinkWriter;

/**
 * Hermetic unit tests for {@link AbstractScilabCodeHandler} and its nested
 * {@link LinkWriter}.
 *
 * <p>The abstract base defines every {@code handle*} method as a no-op (concrete
 * highlighters override the ones they care about); an anonymous subclass verifies
 * those defaults neither throw nor emit anything. The default {@link LinkWriter}
 * is an identity mapping, and it is overridable.
 */
public class AbstractScilabCodeHandlerTest {

    /** Minimal concrete handler that overrides nothing — exercises the no-op base bodies. */
    private static final class NoopHandler extends AbstractScilabCodeHandler { }

    @Test
    public void baseHandleMethodsAreNoOpsAndDoNotThrow() {
        NoopHandler h = new NoopHandler();
        assertDoesNotThrow(() -> {
            h.handleDefault("a");
            h.handleOperator("+");
            h.handleOpenClose("(");
            h.handleFKeywords("function");
            h.handleSKeywords("if");
            h.handleCKeywords("return");
            h.handleConstants("%pi");
            h.handleCommand("plot");
            h.handleMacro("m");
            h.handleFunctionId("f");
            h.handleFunctionIdDecl("f");
            h.handleId("x");
            h.handleInputOutputArgs("y");
            h.handleInputOutputArgsDecl("y");
            h.handleNumber("1");
            h.handleSpecial("$");
            h.handleString("\"s\"");
            h.handleNothing(" ");
            h.handleField("bar");
            h.handleComment("// c");
        });
    }

    @Test
    public void defaultLinkWriterIsIdentity() {
        LinkWriter lw = new LinkWriter();
        assertEquals("sin", lw.getLink("sin"));
        assertEquals("", lw.getLink(""));
    }

    @Test
    public void linkWriterCanBeOverridden() {
        LinkWriter custom = new LinkWriter() {
            public String getLink(String id) {
                return id == null ? null : id.toUpperCase();
            }
        };
        assertEquals("SIN", custom.getLink("sin"));
        assertNull(custom.getLink(null));
    }
}
