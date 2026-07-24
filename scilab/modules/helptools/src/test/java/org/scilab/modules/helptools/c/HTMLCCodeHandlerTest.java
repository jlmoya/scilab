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
 * Hermetic unit tests for {@link HTMLCCodeHandler} — the C-source syntax highlighter.
 *
 * <p>Two things are pinned: the pure static {@link HTMLCCodeHandler#replaceEntity}
 * (with its ampersand-first ordering, which double-encodes existing entities), and
 * the {@code handle*} span markup. A key asymmetry is exercised: only default /
 * comment / string / operator run their content through {@code replaceEntity}, while
 * numbers, keywords, ids, etc. are emitted verbatim.
 */
public class HTMLCCodeHandlerTest {

    // ---- replaceEntity (pure) ------------------------------------------

    @Test
    public void replaceEntityEncodesTheFiveSpecialsNumerically() {
        assertEquals("&#0060;&#0062;&amp;&#0034;&#0039;",
                     HTMLCCodeHandler.replaceEntity("<>&\"'"));
    }

    @Test
    public void replaceEntityDoubleEncodesExistingEntities() {
        // Defect characterization: '&' is replaced first, so "&amp;" becomes "&amp;amp;".
        assertEquals("&amp;amp;", HTMLCCodeHandler.replaceEntity("&amp;"));
    }

    @Test
    public void replaceEntityLeavesPlainTextUntouched() {
        assertEquals("int main(void)", HTMLCCodeHandler.replaceEntity("int main(void)"));
        assertEquals("", HTMLCCodeHandler.replaceEntity(""));
    }

    // ---- handle* markup -------------------------------------------------

    @Test
    public void handleDefaultWrapsAndEncodes() throws IOException {
        AbstractCCodeHandler h = HTMLCCodeHandler.getInstance();
        h.handleDefault("a<b");
        assertEquals("<span class=\"cdefault\">a&#0060;b</span>", h.toString());
    }

    @Test
    public void handleCommentStringOperatorEncode() throws IOException {
        AbstractCCodeHandler h = HTMLCCodeHandler.getInstance();
        h.handleComment("/* <c> */");
        assertEquals("<span class=\"ccomment\">/* &#0060;c&#0062; */</span>", h.toString());

        h = HTMLCCodeHandler.getInstance();
        h.handleString("\"a&b\"");
        assertEquals("<span class=\"cstring\">&#0034;a&amp;b&#0034;</span>", h.toString());

        h = HTMLCCodeHandler.getInstance();
        h.handleOperator("<");
        assertEquals("<span class=\"coperator\">&#0060;</span>", h.toString());
    }

    @Test
    public void handleNumberAndIdDoNotEncode() throws IOException {
        // Contrast with handleDefault: these emit their content verbatim.
        AbstractCCodeHandler h = HTMLCCodeHandler.getInstance();
        h.handleNumber("1<2");
        assertEquals("<span class=\"cnumber\">1<2</span>", h.toString());

        h = HTMLCCodeHandler.getInstance();
        h.handleId("a<b");
        assertEquals("<span class=\"cid\">a<b</span>", h.toString());
    }

    @Test
    public void handleTypeKeywordModifierPreprocessorOpenClose() throws IOException {
        AbstractCCodeHandler h = HTMLCCodeHandler.getInstance();
        h.handleKeyword("for");
        assertEquals("<span class=\"ckeyword\">for</span>", h.toString());

        h = HTMLCCodeHandler.getInstance();
        h.handleType("int");
        assertEquals("<span class=\"ctype\">int</span>", h.toString());

        h = HTMLCCodeHandler.getInstance();
        h.handleModifier("inline");
        assertEquals("<span class=\"cmodifier\">inline</span>", h.toString());

        h = HTMLCCodeHandler.getInstance();
        h.handlePreprocessor("#define");
        assertEquals("<span class=\"cpreprocessor\">#define</span>", h.toString());

        h = HTMLCCodeHandler.getInstance();
        h.handleOpenClose("{");
        assertEquals("<span class=\"copenclose\">{</span>", h.toString());
    }

    @Test
    public void handleNothingEmitsRawSequence() throws IOException {
        AbstractCCodeHandler h = HTMLCCodeHandler.getInstance();
        h.handleNothing("  <not encoded>  ");
        assertEquals("  <not encoded>  ", h.toString());
    }

    @Test
    public void getInstanceResetsTheSharedBuffer() throws IOException {
        AbstractCCodeHandler h = HTMLCCodeHandler.getInstance();
        h.handleId("first");
        assertEquals("<span class=\"cid\">first</span>", h.toString());
        // A fresh getInstance() must zero the buffer of the shared singleton.
        h = HTMLCCodeHandler.getInstance();
        assertEquals("", h.toString());
    }
}
