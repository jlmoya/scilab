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

package org.scilab.modules.helptools.XML;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link AbstractXMLCodeHandler}.
 *
 * <p>The base class defines every {@code handle*} hook as a do-nothing default so a
 * concrete handler only has to override the ones it cares about; the {@link XMLLexer}
 * relies on that contract when it dispatches a token whose hook the handler left alone.
 * These tests pin the two halves of that contract: (1) the inherited defaults are pure
 * no-ops that never throw, and (2) a subclass override is what actually runs when the
 * same method is called through the base-typed reference.
 */
public class AbstractXMLCodeHandlerTest {

    /** A bare subclass: it overrides nothing, so every call hits a base-class default. */
    private static final class Bare extends AbstractXMLCodeHandler { }

    @Test
    public void everyDefaultHookIsANoOpThatDoesNotThrow() {
        Bare h = new Bare();
        assertDoesNotThrow(() -> {
            h.handleDefault("x");
            h.handleEntity("&amp;");
            h.handleNothing("  ");
            h.handleOpenInstr("<?");
            h.handleCloseInstr("?>");
            h.handleInstrName("xml");
            h.handleLow("<");
            h.handleOpenTagName("tag");
            h.handleLowClose("</");
            h.handleGreat(">");
            h.handleOpenComment("<!--");
            h.handleOpenCdata("<![CDATA[");
            h.handleAttributeName("id");
            h.handleEqual("=");
            h.handleAttributeValue("v");
            h.handleAutoClose("/>");
            h.handleCommentEnd("-->");
            h.handleComment("note");
            h.handleCdataEnd("]]>");
            h.handleCdata("body");
        });
    }

    @Test
    public void defaultsAcceptNullSequencesWithoutThrowing() {
        // The base hooks never dereference their argument, so null is tolerated.
        Bare h = new Bare();
        assertDoesNotThrow(() -> {
            h.handleDefault(null);
            h.handleComment(null);
            h.handleCdata(null);
            h.handleAttributeValue(null);
        });
    }

    @Test
    public void aSubclassOverrideRunsInsteadOfTheDefault() throws IOException {
        // Dispatch through the base type must reach the override — the mechanism the
        // lexer depends on to route tokens into a concrete handler.
        final List<String> seen = new ArrayList<>();
        AbstractXMLCodeHandler h = new AbstractXMLCodeHandler() {
            @Override
            public void handleComment(String seq) {
                seen.add(seq);
            }
        };
        h.handleComment("hello");
        h.handleDefault("ignored-by-default"); // still a no-op, must not be recorded
        assertEquals(List.of("hello"), seen);
    }
}
