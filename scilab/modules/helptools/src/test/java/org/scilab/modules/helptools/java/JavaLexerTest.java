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

package org.scilab.modules.helptools.java;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.scilab.modules.helptools.c.AbstractCCodeHandler;

/**
 * Hermetic unit tests for the JFlex-generated {@link JavaLexer}.
 *
 * <p>{@code JavaLexer} shares the {@link AbstractCCodeHandler} sink with the C
 * lexer but carries Java-specific keyword/type/modifier tables. A recording
 * handler lets us assert those Java classifications directly: {@code class} is a
 * keyword (the C lexer would call it an id), {@code String} is a type,
 * {@code public} a modifier, and so on. {@code convert(handler, String)} runs off a
 * {@code StringReader}, so nothing external is needed.
 */
public class JavaLexerTest {

    private static final class Rec extends AbstractCCodeHandler {
        final List<String> calls = new ArrayList<>();
        public void handleDefault(String s)      {
            calls.add("default:" + s);
        }
        public void handleComment(String s)      {
            calls.add("comment:" + s);
        }
        public void handleString(String s)       {
            calls.add("string:" + s);
        }
        public void handleNumber(String s)       {
            calls.add("number:" + s);
        }
        public void handleKeyword(String s)      {
            calls.add("keyword:" + s);
        }
        public void handleType(String s)         {
            calls.add("type:" + s);
        }
        public void handleModifier(String s)     {
            calls.add("modifier:" + s);
        }
        public void handleOpenClose(String s)    {
            calls.add("openclose:" + s);
        }
        public void handleOperator(String s)     {
            calls.add("operator:" + s);
        }
        public void handleId(String s)           {
            calls.add("id:" + s);
        }
    }

    private static List<String> lex(String code) {
        Rec rec = new Rec();
        new JavaLexer().convert(rec, code);
        return rec.calls;
    }

    @Test
    public void classKeywordIsAKeyword() {
        // "class" is a Java keyword — a distinguishing case from the C lexer,
        // whose keyword table has no "class" (it would be an id there).
        assertTrue(lex("class").contains("keyword:class"));
    }

    @Test
    public void stringTypeIsAType() {
        assertTrue(lex("String").contains("type:String"));
        assertTrue(lex("int").contains("type:int"));
    }

    @Test
    public void publicModifierIsAModifier() {
        assertTrue(lex("public").contains("modifier:public"));
    }

    @Test
    public void numbersAndIdentifiers() {
        assertTrue(lex("42").contains("number:42"));
        List<String> id = lex("myVar");
        assertTrue(id.contains("id:myVar"));
        assertFalse(id.contains("keyword:myVar"));
    }

    @Test
    public void punctuationAndComments() {
        assertTrue(lex("(").contains("openclose:("));
        assertTrue(lex("+").contains("operator:+"));
        assertTrue(lex("// note").contains("comment:// note"));
    }

    @Test
    public void nullReaderConvertReturnsNull() {
        assertNull(new JavaLexer().convert(new Rec(), (java.io.Reader) null, true));
    }
}
