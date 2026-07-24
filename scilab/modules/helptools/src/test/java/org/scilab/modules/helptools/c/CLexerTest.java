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

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for the JFlex-generated {@link CLexer}.
 *
 * <p>The lexer classifies a C/C++ token stream and pushes each token to an
 * {@link AbstractCCodeHandler}. Instead of the HTML handler, these tests use a
 * recording handler so we can assert the classification directly: {@code int} is a
 * type, {@code for} a keyword, {@code const} a modifier, {@code #define} a
 * preprocessor directive, {@code NULL} a number, and so on. The public
 * {@code convert(handler, String)} entry point reads a {@code StringReader}, so no
 * files or Scilab runtime are involved. Single-token inputs are used to keep the
 * expectations independent of the leading-whitespace cleanup state.
 */
public class CLexerTest {

    /** Records every {@code handle*} call as a "kind:text" line. */
    private static final class Rec extends AbstractCCodeHandler {
        final List<String> calls = new ArrayList<>();
        final StringBuilder text = new StringBuilder();
        public void handleDefault(String s)      {
            calls.add("default:" + s);
            text.append(s);
        }
        public void handleComment(String s)      {
            calls.add("comment:" + s);
            text.append(s);
        }
        public void handleNothing(String s)      {
            text.append(s);
        }
        public void handleString(String s)       {
            calls.add("string:" + s);
            text.append(s);
        }
        public void handleNumber(String s)       {
            calls.add("number:" + s);
            text.append(s);
        }
        public void handleKeyword(String s)      {
            calls.add("keyword:" + s);
            text.append(s);
        }
        public void handleType(String s)         {
            calls.add("type:" + s);
            text.append(s);
        }
        public void handleModifier(String s)     {
            calls.add("modifier:" + s);
            text.append(s);
        }
        public void handlePreprocessor(String s) {
            calls.add("preprocessor:" + s);
            text.append(s);
        }
        public void handleOpenClose(String s)    {
            calls.add("openclose:" + s);
            text.append(s);
        }
        public void handleOperator(String s)     {
            calls.add("operator:" + s);
            text.append(s);
        }
        public void handleId(String s)           {
            calls.add("id:" + s);
            text.append(s);
        }
        public String toString() {
            return text.toString();
        }
    }

    private static List<String> lex(String code) {
        Rec rec = new Rec();
        new CLexer().convert(rec, code);
        return rec.calls;
    }

    @Test
    public void classifiesNumbersKeywordsTypesModifiersAndPreprocessor() {
        assertTrue(lex("42").contains("number:42"));
        assertTrue(lex("for").contains("keyword:for"));
        assertTrue(lex("int").contains("type:int"));
        assertTrue(lex("const").contains("modifier:const"));
        assertTrue(lex("#define").contains("preprocessor:#define"));
    }

    @Test
    public void plainIdentifierIsAnId() {
        List<String> calls = lex("myVariable");
        assertTrue(calls.contains("id:myVariable"));
        assertFalse(calls.contains("keyword:myVariable"));
        assertFalse(calls.contains("type:myVariable"));
    }

    @Test
    public void nullLiteralIsLexedAsANumber() {
        // The C number rule explicitly includes the "NULL" literal.
        assertTrue(lex("NULL").contains("number:NULL"));
    }

    @Test
    public void punctuationSplitsIntoOpenCloseAndOperator() {
        assertTrue(lex("(").contains("openclose:("));
        assertTrue(lex("+").contains("operator:+"));
    }

    @Test
    public void oneLineCommentIsCaptured() {
        assertTrue(lex("// hello").contains("comment:// hello"));
    }

    @Test
    public void convertReturnsTheHandlersRenderedText() {
        Rec rec = new Rec();
        String out = new CLexer().convert(rec, "int");
        assertEquals("int", out, "convert returns handler.toString()");
    }

    @Test
    public void nullReaderConvertReturnsNull() {
        assertNull(new CLexer().convert(new Rec(), (java.io.Reader) null, true));
    }
}
