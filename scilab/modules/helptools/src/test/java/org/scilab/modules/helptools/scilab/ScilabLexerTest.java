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
}
