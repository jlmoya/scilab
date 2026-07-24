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

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for the JFlex-generated {@link XMLLexer}.
 *
 * <p>The lexer walks an XML fragment through its tag / comment / CDATA / attribute
 * states, pushing each piece to an {@link AbstractXMLCodeHandler}. A recording
 * handler captures the routing so we can assert, e.g., that {@code <a>} yields an
 * open-tag-name of {@code a}, that text content is "default", and that comment and
 * CDATA bodies land on their dedicated hooks. {@code convert(handler, String)} runs
 * off a {@code StringReader} — no files, no Scilab.
 */
public class XMLLexerTest {

    private static final class Rec extends AbstractXMLCodeHandler {
        final List<String> calls = new ArrayList<>();
        private void rec(String k, String s) {
            calls.add(k + ":" + s);
        }
        public void handleDefault(String s)        {
            rec("default", s);
        }
        public void handleEntity(String s)         {
            rec("entity", s);
        }
        public void handleNothing(String s)        { /* whitespace/newlines: not recorded */ }
        public void handleLow(String s)            {
            rec("low", s);
        }
        public void handleOpenTagName(String s)    {
            rec("openTagName", s);
        }
        public void handleLowClose(String s)       {
            rec("lowClose", s);
        }
        public void handleGreat(String s)          {
            rec("great", s);
        }
        public void handleOpenComment(String s)    {
            rec("openComment", s);
        }
        public void handleComment(String s)        {
            rec("comment", s);
        }
        public void handleCommentEnd(String s)     {
            rec("commentEnd", s);
        }
        public void handleOpenCdata(String s)      {
            rec("openCdata", s);
        }
        public void handleCdata(String s)          {
            rec("cdata", s);
        }
        public void handleCdataEnd(String s)       {
            rec("cdataEnd", s);
        }
        public void handleAttributeName(String s)  {
            rec("attributeName", s);
        }
        public void handleEqual(String s)          {
            rec("equal", s);
        }
        public void handleAttributeValue(String s) {
            rec("attributeValue", s);
        }
        public void handleAutoClose(String s)      {
            rec("autoClose", s);
        }
    }

    private static List<String> lex(String code) {
        Rec rec = new Rec();
        new XMLLexer().convert(rec, code);
        return rec.calls;
    }

    private static boolean anyStartsWithContaining(List<String> calls, String prefix, String needle) {
        return calls.stream().anyMatch(s -> s.startsWith(prefix) && s.contains(needle));
    }

    @Test
    public void plainTextBecomesDefault() {
        assertTrue(lex("hello world").contains("default:hello world"));
    }

    @Test
    public void openTagNameIsExtracted() {
        List<String> calls = lex("<tag");
        assertTrue(calls.contains("openTagName:tag"));
        assertTrue(calls.contains("low:&#0060;"));
    }

    @Test
    public void fullElementRoutesNameContentAndClose() {
        List<String> calls = lex("<a>x</a>");
        assertTrue(calls.contains("openTagName:a"), () -> calls.toString());
        assertTrue(calls.contains("default:x"), () -> calls.toString());
        assertTrue(anyStartsWithContaining(calls, "great", "&#0062;"));
    }

    @Test
    public void commentBodyLandsOnTheCommentHooks() {
        List<String> calls = lex("<!--hi-->");
        assertTrue(anyStartsWithContaining(calls, "openComment", "!--"), () -> calls.toString());
        assertTrue(anyStartsWithContaining(calls, "comment:", "hi"), () -> calls.toString());
        assertTrue(anyStartsWithContaining(calls, "commentEnd", "--"), () -> calls.toString());
    }

    @Test
    public void cdataBodyLandsOnTheCdataHooks() {
        List<String> calls = lex("<![CDATA[payload]]>");
        assertTrue(anyStartsWithContaining(calls, "openCdata", "CDATA"), () -> calls.toString());
        assertTrue(anyStartsWithContaining(calls, "cdata:", "payload"), () -> calls.toString());
        assertTrue(anyStartsWithContaining(calls, "cdataEnd", "]]"), () -> calls.toString());
    }

    @Test
    public void attributeSplitsIntoNameEqualAndValue() {
        List<String> calls = lex("<a b=\"c\">");
        assertTrue(calls.contains("attributeName:b"), () -> calls.toString());
        assertTrue(calls.contains("equal:="), () -> calls.toString());
        assertTrue(anyStartsWithContaining(calls, "attributeValue", "c"), () -> calls.toString());
    }

    @Test
    public void nullReaderConvertReturnsNull() {
        assertNull(new XMLLexer().convert(new Rec(), (java.io.Reader) null, true));
    }
}
