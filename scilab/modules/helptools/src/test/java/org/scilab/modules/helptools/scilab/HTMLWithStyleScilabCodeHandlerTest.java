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

import java.io.File;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Hermetic unit tests for {@link HTMLWithStyleScilabCodeHandler}.
 *
 * <p>This handler emits inline-styled {@code <span style=...>} markup and drives the
 * JFlex {@link ScilabLexer}. The {@link ScilabLexer#ScilabLexer(Set, Set)} constructor
 * takes the primitive/macro name sets directly (no file I/O), so the whole
 * lex-and-highlight pipeline runs hermetically here — no running Scilab, no data files.
 * Tests cover the public colour constants plus a few end-to-end {@code convert} paths.
 */
public class HTMLWithStyleScilabCodeHandlerTest {

    private static HTMLWithStyleScilabCodeHandler newHandler() {
        return new HTMLWithStyleScilabCodeHandler(Set.of("disp"), Set.of("myMacro"));
    }

    @Test
    public void colourConstantsHaveExpectedInlineStyles() {
        assertEquals("<span style=\"font-style:normal;color:rgb(0,0,0)\">",
                     HTMLWithStyleScilabCodeHandler.DEFAULT);
        assertEquals("<span style=\"font-style:italic;color:rgb(1,168,1)\">",
                     HTMLWithStyleScilabCodeHandler.COMMENT);
        assertEquals("<span style=\"font-style:normal;color:rgb(50,185,185)\">",
                     HTMLWithStyleScilabCodeHandler.COMMAND);
        assertEquals("<span style=\"font-style:normal;color:rgb(188,143,143)\">",
                     HTMLWithStyleScilabCodeHandler.NUMBER);
        assertEquals("<span style=\"font-weight:bold;color:rgb(131,67,16)\">",
                     HTMLWithStyleScilabCodeHandler.INPUTOUTPUTARGS);
    }

    @Test
    public void convertWrapsOutputInPreBlock() {
        String out = newHandler().convert("x");
        assertTrue(out.startsWith("<pre>\n"), () -> "missing <pre> prefix: " + out);
        assertTrue(out.endsWith("\n</pre>\n"), () -> "missing </pre> suffix: " + out);
        assertTrue(out.contains("x"), () -> "token text dropped: " + out);
    }

    @Test
    public void convertEmptyInputYieldsAnEmptyPreBlock() {
        assertEquals("<pre>\n\n</pre>\n", newHandler().convert(""));
    }

    @Test
    public void convertNumberEmitsNumberColourAndDigits() {
        String out = newHandler().convert("123");
        assertTrue(out.contains("123"), () -> "digits dropped: " + out);
        assertTrue(out.contains(HTMLWithStyleScilabCodeHandler.NUMBER),
                   () -> "number not styled: " + out);
    }

    @Test
    public void convertPrimitiveUsesCommandColour() {
        // "disp" was registered as a primitive => the lexer routes it to handleCommand,
        // exercising the ScilabLexer -> handler integration end to end.
        String out = newHandler().convert("disp");
        assertTrue(out.contains("disp"), () -> "command text dropped: " + out);
        assertTrue(out.contains(HTMLWithStyleScilabCodeHandler.COMMAND),
                   () -> "primitive not highlighted as command: " + out);
    }

    @Test
    public void convertPlainIdentifierIsNotStyledAsCommand() {
        // An identifier absent from both sets must NOT get the command colour.
        String out = newHandler().convert("zzz");
        assertTrue(out.contains("zzz"), () -> "identifier text dropped: " + out);
        assertFalse(out.contains(HTMLWithStyleScilabCodeHandler.COMMAND),
                    () -> "plain id wrongly highlighted as command: " + out);
    }

    @Test
    public void constructorAcceptsEmptyNameSets() {
        HTMLWithStyleScilabCodeHandler h = new HTMLWithStyleScilabCodeHandler(Set.of(), Set.of());
        String out = h.convert("y");
        assertTrue(out.startsWith("<pre>\n") && out.endsWith("\n</pre>\n"));
        assertTrue(out.contains("y"));
    }

    // ==================================================================
    // Richer convert() paths: each token category the lexer recognises
    // drives the matching handle* method, which emits its colour constant.
    // ==================================================================

    @Test
    public void operatorAndBracketsGetTheirColours() {
        String out = newHandler().convert("[1+2]");
        assertTrue(out.contains(HTMLWithStyleScilabCodeHandler.OPERATOR), () -> out);
        assertTrue(out.contains(HTMLWithStyleScilabCodeHandler.OPENCLOSE), () -> out);
        assertTrue(out.contains(HTMLWithStyleScilabCodeHandler.NUMBER), () -> out);
    }

    @Test
    public void structuralKeywordGetsTheSKeywordColour() {
        String out = newHandler().convert("for");
        assertTrue(out.contains(HTMLWithStyleScilabCodeHandler.SKEYWORD), () -> out);
        assertTrue(out.contains("for"), () -> out);
    }

    @Test
    public void controlKeywordGetsTheCKeywordColour() {
        String out = newHandler().convert("return");
        assertTrue(out.contains(HTMLWithStyleScilabCodeHandler.CKEYWORD), () -> out);
    }

    @Test
    public void doubleQuotedStringGetsTheStringColour() {
        String out = newHandler().convert("\"hi\"");
        assertTrue(out.contains(HTMLWithStyleScilabCodeHandler.STRING), () -> out);
    }

    @Test
    public void lineCommentGetsTheCommentColour() {
        String out = newHandler().convert("// a note");
        assertTrue(out.contains(HTMLWithStyleScilabCodeHandler.COMMENT), () -> out);
        assertTrue(out.contains("note"), () -> out);
    }

    @Test
    public void registeredMacroGetsTheMacroColour() {
        // "myMacro" was registered as a macro in newHandler().
        String out = newHandler().convert("myMacro");
        assertTrue(out.contains(HTMLWithStyleScilabCodeHandler.MACRO), () -> out);
    }

    @Test
    public void functionBlockGetsTheFunctionKeywordColour() {
        String out = newHandler().convert("function y = f(x)\n  y = x\nendfunction\n");
        assertTrue(out.contains(HTMLWithStyleScilabCodeHandler.FKEYWORD), () -> out);
        assertTrue(out.startsWith("<pre>\n") && out.endsWith("\n</pre>\n"), () -> out);
    }

    // ---- convert(Reader, Writer) overload ------------------------------

    @Test
    public void readerWriterOverloadWritesAPreWrappedResult() throws Exception {
        StringWriter sw = new StringWriter();
        newHandler().convert(new StringReader("x + 1"), sw);
        String out = sw.toString();
        assertTrue(out.startsWith("<pre>\n"), () -> out);
        assertTrue(out.endsWith("\n</pre>\n"), () -> out);
        assertTrue(out.contains(HTMLWithStyleScilabCodeHandler.OPERATOR), () -> out);
        assertTrue(out.contains(HTMLWithStyleScilabCodeHandler.NUMBER), () -> out);
    }

    // ---- (primFile, macroFile) file constructor ------------------------

    @Test
    public void fileBackedConstructorLoadsPrimitiveAndMacroNames(@TempDir Path dir) throws Exception {
        // ScilabLexer(String, String) reads one name per line from each file.
        File prim = dir.resolve("primitives.txt").toFile();
        File macro = dir.resolve("macros.txt").toFile();
        Files.write(prim.toPath(), "disp\n".getBytes(StandardCharsets.UTF_8));
        Files.write(macro.toPath(), "myMacro\n".getBytes(StandardCharsets.UTF_8));

        HTMLWithStyleScilabCodeHandler h =
            new HTMLWithStyleScilabCodeHandler(prim.getAbsolutePath(), macro.getAbsolutePath());

        // A primitive loaded from file must be highlighted with the command colour...
        assertTrue(h.convert("disp").contains(HTMLWithStyleScilabCodeHandler.COMMAND));
        // ...and a macro loaded from file with the macro colour.
        assertTrue(h.convert("myMacro").contains(HTMLWithStyleScilabCodeHandler.MACRO));
    }
}
