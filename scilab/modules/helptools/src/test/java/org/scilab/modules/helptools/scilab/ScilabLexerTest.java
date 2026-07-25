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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for the JFlex-generated {@link ScilabLexer}.
 *
 * <p>The {@link ScilabLexer#ScilabLexer(Set, Set)} constructor takes the primitive
 * and macro name sets directly (no data files), so the whole classify-and-dispatch
 * pipeline runs offline. A recording {@link AbstractScilabCodeHandler} captures the
 * routing, letting us assert that a registered primitive becomes a "command", a
 * registered macro becomes a "macro", an unknown identifier stays an "id", and
 * digits become a "number".
 */
public class ScilabLexerTest {

    private static final class Rec extends AbstractScilabCodeHandler {
        final List<String> calls = new ArrayList<>();
        public void handleNumber(String s)          {
            calls.add("number:" + s);
        }
        public void handleId(String s)              {
            calls.add("id:" + s);
        }
        public void handleCommand(String s)         {
            calls.add("command:" + s);
        }
        public void handleMacro(String s)           {
            calls.add("macro:" + s);
        }
        public void handleFunctionId(String s)      {
            calls.add("functionId:" + s);
        }
        public void handleInputOutputArgs(String s) {
            calls.add("ioargs:" + s);
        }
        public void handleString(String s)          {
            calls.add("string:" + s);
        }
        public void handleComment(String s)         {
            calls.add("comment:" + s);
        }
    }

    private static List<String> lex(ScilabLexer lexer, String code) {
        Rec rec = new Rec();
        lexer.convert(rec, code);
        return rec.calls;
    }

    @Test
    public void registeredPrimitiveBecomesACommand() {
        ScilabLexer lexer = new ScilabLexer(Set.of("disp"), Set.of("myMacro"));
        assertTrue(lex(lexer, "disp").contains("command:disp"));
    }

    @Test
    public void registeredMacroBecomesAMacro() {
        ScilabLexer lexer = new ScilabLexer(Set.of("disp"), Set.of("myMacro"));
        assertTrue(lex(lexer, "myMacro").contains("macro:myMacro"));
    }

    @Test
    public void unknownIdentifierStaysAnId() {
        ScilabLexer lexer = new ScilabLexer(Set.of("disp"), Set.of("myMacro"));
        List<String> calls = lex(lexer, "notAKnownName");
        assertTrue(calls.contains("id:notAKnownName"), () -> calls.toString());
        assertFalse(calls.contains("command:notAKnownName"));
        assertFalse(calls.contains("macro:notAKnownName"));
    }

    @Test
    public void digitsBecomeANumber() {
        ScilabLexer lexer = new ScilabLexer(Set.of(), Set.of());
        assertTrue(lex(lexer, "123").contains("number:123"));
    }

    @Test
    public void convertReturnsHandlerToString() {
        ScilabLexer lexer = new ScilabLexer(Set.of(), Set.of());
        Rec rec = new Rec();
        // The recording handler's toString is Object's default — non-null and stable per call.
        assertEquals(rec.toString(), lexer.convert(rec, ""));
    }

    @Test
    public void nullReaderConvertReturnsNull() {
        ScilabLexer lexer = new ScilabLexer(Set.of(), Set.of());
        assertNull(lexer.convert(new Rec(), (java.io.Reader) null, true));
    }

    // ==================================================================
    // Richer inputs: drive the lexer through its string / comment /
    // keyword / operator / function states. A fuller recording handler
    // captures every routing category so we can assert the classification.
    // ==================================================================

    /** Records every meaningful {@code handle*} call as "kind:text". */
    private static final class FullRec extends AbstractScilabCodeHandler {
        final List<String> calls = new ArrayList<>();
        public void handleDefault(String s)             {
            calls.add("default:" + s);
        }
        public void handleOperator(String s)            {
            calls.add("operator:" + s);
        }
        public void handleOpenClose(String s)           {
            calls.add("openclose:" + s);
        }
        public void handleFKeywords(String s)           {
            calls.add("fkeyword:" + s);
        }
        public void handleSKeywords(String s)           {
            calls.add("skeyword:" + s);
        }
        public void handleCKeywords(String s)           {
            calls.add("ckeyword:" + s);
        }
        public void handleConstants(String s)           {
            calls.add("constants:" + s);
        }
        public void handleCommand(String s)             {
            calls.add("command:" + s);
        }
        public void handleMacro(String s)               {
            calls.add("macro:" + s);
        }
        public void handleFunctionId(String s)          {
            calls.add("functionId:" + s);
        }
        public void handleFunctionIdDecl(String s)      {
            calls.add("functionIdDecl:" + s);
        }
        public void handleId(String s)                  {
            calls.add("id:" + s);
        }
        public void handleInputOutputArgs(String s)     {
            calls.add("ioargs:" + s);
        }
        public void handleInputOutputArgsDecl(String s) {
            calls.add("ioargsDecl:" + s);
        }
        public void handleNumber(String s)              {
            calls.add("number:" + s);
        }
        public void handleSpecial(String s)             {
            calls.add("special:" + s);
        }
        public void handleString(String s)              {
            calls.add("string:" + s);
        }
        public void handleField(String s)               {
            calls.add("field:" + s);
        }
        public void handleComment(String s)             {
            calls.add("comment:" + s);
        }
    }

    private static List<String> lexFull(ScilabLexer lexer, String code) {
        FullRec rec = new FullRec();
        lexer.convert(rec, code);
        return rec.calls;
    }

    private static boolean any(List<String> calls, String prefix) {
        return calls.stream().anyMatch(s -> s.startsWith(prefix));
    }

    @Test
    public void doubleQuotedStringIsClassifiedAsAString() {
        List<String> calls = lexFull(new ScilabLexer(Set.of(), Set.of()), "\"hello\"");
        assertTrue(any(calls, "string:"), () -> calls.toString());
    }

    @Test
    public void lineCommentIsClassifiedAsAComment() {
        List<String> calls = lexFull(new ScilabLexer(Set.of(), Set.of()), "// a note");
        assertTrue(calls.stream().anyMatch(s -> s.startsWith("comment:") && s.contains("note")),
                   () -> calls.toString());
    }

    @Test
    public void floatingPointLiteralIsANumber() {
        List<String> calls = lexFull(new ScilabLexer(Set.of(), Set.of()), "1.5e-3");
        assertTrue(any(calls, "number:"), () -> calls.toString());
    }

    @Test
    public void structuralKeywordIsAnSKeyword() {
        // "for" is in the structure-keyword table (if/for/while/end/...).
        List<String> calls = lexFull(new ScilabLexer(Set.of(), Set.of()), "for");
        assertTrue(calls.contains("skeyword:for"), () -> calls.toString());
    }

    @Test
    public void controlKeywordIsACKeyword() {
        // "return" is in the control-keyword table (abort/break/return/...).
        List<String> calls = lexFull(new ScilabLexer(Set.of(), Set.of()), "return");
        assertTrue(calls.contains("ckeyword:return"), () -> calls.toString());
    }

    @Test
    public void arithmeticOperatorsAndBracketsAreRouted() {
        List<String> calls = lexFull(new ScilabLexer(Set.of(), Set.of()), "[1+2]");
        assertTrue(calls.contains("operator:+"), () -> calls.toString());
        assertTrue(any(calls, "openclose:"), () -> calls.toString());
        assertTrue(any(calls, "number:"), () -> calls.toString());
    }

    @Test
    public void functionDeclarationEmitsTheFunctionKeyword() {
        // The 'function ... endfunction' block exercises the FUNCTION/RETS/ARGS states.
        String code = "function y = f(x)\n  y = x + 1\nendfunction\n";
        List<String> calls = lexFull(new ScilabLexer(Set.of(), Set.of()), code);
        assertTrue(calls.contains("fkeyword:function"), () -> calls.toString());
    }

    @Test
    public void aRealisticSnippetIsFullyConsumedWithoutError() {
        // A multi-construct program drives many DFA states in one pass; the lexer must
        // still return the handler's rendering (non-null) and have classified real tokens.
        String code =
            "// demo\n"
            + "function r = g(a, b)\n"
            + "  s = \"txt\";\n"
            + "  if a > b then\n"
            + "    r = a;\n"
            + "  else\n"
            + "    r = b;\n"
            + "  end\n"
            + "endfunction\n";
        FullRec rec = new FullRec();
        ScilabLexer lexer = new ScilabLexer(Set.of("disp"), Set.of("g"));
        String rendered = lexer.convert(rec, code);
        assertNotNull(rendered, "convert must return the handler rendering, not null");
        assertTrue(any(rec.calls, "comment:"), () -> rec.calls.toString());
        assertTrue(any(rec.calls, "string:"), () -> rec.calls.toString());
        assertTrue(any(rec.calls, "fkeyword:"), () -> rec.calls.toString());
        assertTrue(any(rec.calls, "skeyword:"), () -> rec.calls.toString());
    }

    @Test
    public void readerOverloadConsumesTheWholeStreamAndReturnsRendering() throws Exception {
        ScilabLexer lexer = new ScilabLexer(Set.of(), Set.of());
        FullRec rec = new FullRec();
        try (java.io.Reader r = new java.io.StringReader("x = 1 + 2")) {
            String rendered = lexer.convert(rec, r, true);
            assertNotNull(rendered);
        }
        assertTrue(rec.calls.contains("operator:+"), () -> rec.calls.toString());
        assertTrue(any(rec.calls, "number:"), () -> rec.calls.toString());
    }
}
