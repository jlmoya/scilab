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

import java.io.IOException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.scilab.modules.helptools.scilab.AbstractScilabCodeHandler.LinkWriter;

/**
 * Hermetic unit tests for {@link HTMLScilabCodeHandler}, the syntax-highlighter that
 * wraps Scilab tokens in {@code <span class="scilab*">} markup.
 *
 * <p>The class is a reset-on-{@code getInstance} singleton with a mutable static
 * {@link LinkWriter}. Each test calls {@code getInstance(...)} first (which zeroes the
 * shared buffer) and, where the command/macro link branch matters, installs a known
 * {@link LinkWriter}. {@link #restoreDefaultLinkWriter()} puts the default back so the
 * static state never leaks between tests.
 */
public class HTMLScilabCodeHandlerTest {

    @AfterEach
    public void restoreDefaultLinkWriter() {
        HTMLScilabCodeHandler.setLinkWriter(new LinkWriter());
    }

    @Test
    public void defaultOperatorNumberStringSpans() throws IOException {
        AbstractScilabCodeHandler h = HTMLScilabCodeHandler.getInstance("cmd", "file.xml");
        h.handleDefault("d");
        assertEquals("<span class=\"scilabdefault\">d</span>", h.toString());

        // getInstance zeroes the shared buffer, giving each assertion a clean slate.
        h = HTMLScilabCodeHandler.getInstance("cmd", "file.xml");
        h.handleOperator("+");
        assertEquals("<span class=\"scilaboperator\">+</span>", h.toString());

        h = HTMLScilabCodeHandler.getInstance("cmd", "file.xml");
        h.handleNumber("42");
        assertEquals("<span class=\"scilabnumber\">42</span>", h.toString());

        h = HTMLScilabCodeHandler.getInstance("cmd", "file.xml");
        h.handleString("\"hi\"");
        assertEquals("<span class=\"scilabstring\">\"hi\"</span>", h.toString());
    }

    @Test
    public void keywordAndStructuralSpans() throws IOException {
        AbstractScilabCodeHandler h = HTMLScilabCodeHandler.getInstance("cmd", "f");
        h.handleFKeywords("function");
        assertEquals("<span class=\"scilabfkeyword\">function</span>", h.toString());

        h = HTMLScilabCodeHandler.getInstance("cmd", "f");
        h.handleSKeywords("if");
        assertEquals("<span class=\"scilabskeyword\">if</span>", h.toString());

        h = HTMLScilabCodeHandler.getInstance("cmd", "f");
        h.handleCKeywords("return");
        assertEquals("<span class=\"scilabckeyword\">return</span>", h.toString());

        h = HTMLScilabCodeHandler.getInstance("cmd", "f");
        h.handleConstants("%pi");
        assertEquals("<span class=\"scilabconstants\">%pi</span>", h.toString());

        h = HTMLScilabCodeHandler.getInstance("cmd", "f");
        h.handleSpecial("$");
        assertEquals("<span class=\"scilabspecial\">$</span>", h.toString());

        h = HTMLScilabCodeHandler.getInstance("cmd", "f");
        h.handleOpenClose("(");
        assertEquals("<span class=\"scilabopenclose\">(</span>", h.toString());
    }

    @Test
    public void identifierFieldAndCommentSpans() throws IOException {
        AbstractScilabCodeHandler h = HTMLScilabCodeHandler.getInstance("cmd", "f");
        h.handleId("x");
        assertEquals("<span class=\"scilabid\">x</span>", h.toString());

        h = HTMLScilabCodeHandler.getInstance("cmd", "f");
        h.handleField("bar");
        assertEquals("<span class=\"scilabfield\">bar</span>", h.toString());

        h = HTMLScilabCodeHandler.getInstance("cmd", "f");
        h.handleFunctionId("foo");
        assertEquals("<span class=\"scilabfunctionid\">foo</span>", h.toString());

        h = HTMLScilabCodeHandler.getInstance("cmd", "f");
        h.handleInputOutputArgs("y");
        assertEquals("<span class=\"scilabinputoutputargs\">y</span>", h.toString());

        h = HTMLScilabCodeHandler.getInstance("cmd", "f");
        h.handleComment("// note");
        assertEquals("<span class=\"scilabcomment\">// note</span>", h.toString());
    }

    @Test
    public void handleNothingEmitsRawSequenceWithoutSpan() throws IOException {
        AbstractScilabCodeHandler h = HTMLScilabCodeHandler.getInstance("cmd", "f");
        h.handleNothing("   \n");
        assertEquals("   \n", h.toString());
    }

    @Test
    public void commandMatchingCurrentCommandIsRenderedAsSpanNotLink() throws IOException {
        // When the token IS the page's own command, it must not link to itself.
        HTMLScilabCodeHandler.setLinkWriter(new LinkWriter() {
            public String getLink(String id) {
                return "SHOULD-NOT-BE-USED";
            }
        });
        AbstractScilabCodeHandler h = HTMLScilabCodeHandler.getInstance("plot", "f");
        h.handleCommand("plot");
        assertEquals("<span class=\"scilabcommand\">plot</span>", h.toString());
    }

    @Test
    public void commandWithResolvableLinkBecomesAnchor() throws IOException {
        HTMLScilabCodeHandler.setLinkWriter(new LinkWriter() {
            public String getLink(String id) {
                return "ref/" + id + ".html";
            }
        });
        AbstractScilabCodeHandler h = HTMLScilabCodeHandler.getInstance("plot", "f");
        h.handleCommand("disp");
        assertEquals("<a class=\"scilabcommand\" href=\"ref/disp.html\">disp</a>", h.toString());
    }

    @Test
    public void commandWithNullLinkStaysASpan() throws IOException {
        // A null link means "not internal" => plain span (and it is recorded as undoc'd).
        HTMLScilabCodeHandler.setLinkWriter(new LinkWriter() {
            public String getLink(String id) {
                return null;
            }
        });
        AbstractScilabCodeHandler h = HTMLScilabCodeHandler.getInstance("plot", "f");
        h.handleCommand("mystery");
        assertEquals("<span class=\"scilabcommand\">mystery</span>", h.toString());
    }

    @Test
    public void macroWithResolvableLinkBecomesAnchor() throws IOException {
        HTMLScilabCodeHandler.setLinkWriter(new LinkWriter() {
            public String getLink(String id) {
                return id + ".html";
            }
        });
        AbstractScilabCodeHandler h = HTMLScilabCodeHandler.getInstance("plot", "f");
        h.handleMacro("myMacro");
        assertEquals("<a class=\"scilabmacro\" href=\"myMacro.html\">myMacro</a>", h.toString());
    }

    @Test
    public void macroWithNullLinkStaysASpan() throws IOException {
        HTMLScilabCodeHandler.setLinkWriter(new LinkWriter() {
            public String getLink(String id) {
                return null;
            }
        });
        AbstractScilabCodeHandler h = HTMLScilabCodeHandler.getInstance("plot", "f");
        h.handleMacro("localThing");
        assertEquals("<span class=\"scilabmacro\">localThing</span>", h.toString());
    }
}
