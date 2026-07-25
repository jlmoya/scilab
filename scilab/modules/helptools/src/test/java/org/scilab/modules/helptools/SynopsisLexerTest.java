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

package org.scilab.modules.helptools;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for the JFlex-generated {@link SynopsisLexer}.
 *
 * <p>{@code SynopsisLexer.convert(name, str)} is a pure static function: it wraps
 * each identifier in a {@code <span>} — {@code functionid} when the identifier is
 * the documented function's own name, {@code default} otherwise — and HTML-escapes
 * the XML metacharacters. There is no leading-whitespace state, so single-token
 * inputs have fully determined output.
 */
public class SynopsisLexerTest {

    @Test
    public void theDocumentedFunctionNameGetsTheFunctionidSpan() {
        assertEquals("<span class=\"functionid\">sin</span>",
                     SynopsisLexer.convert("sin", "sin"));
    }

    @Test
    public void anyOtherIdentifierGetsTheDefaultSpan() {
        assertEquals("<span class=\"default\">cos</span>",
                     SynopsisLexer.convert("sin", "cos"));
    }

    @Test
    public void xmlMetacharactersAreEscaped() {
        assertEquals("&lt;", SynopsisLexer.convert("f", "<"));
        assertEquals("&gt;", SynopsisLexer.convert("f", ">"));
        assertEquals("&amp;", SynopsisLexer.convert("f", "&"));
        assertEquals("&#0034;", SynopsisLexer.convert("f", "\""));
        assertEquals("&#0039;", SynopsisLexer.convert("f", "'"));
    }

    @Test
    public void lineCommentsGetTheCommentSpan() {
        assertEquals("<span class=\"comment\">// see also</span>",
                     SynopsisLexer.convert("f", "// see also"));
    }

    @Test
    public void aRealSynopsisHighlightsOnlyTheOwnName() {
        String html = SynopsisLexer.convert("myfun", "y = myfun(x)");
        assertNotNull(html);
        assertTrue(html.contains("<span class=\"functionid\">myfun</span>"), html);
        assertTrue(html.contains("<span class=\"default\">y</span>"), html);
        assertTrue(html.contains("<span class=\"default\">x</span>"), html);
    }

    @Test
    public void bufferIsResetBetweenCalls() {
        // The lexer's output buffer is static; convert() must clear it each call.
        SynopsisLexer.convert("f", "alpha");
        String second = SynopsisLexer.convert("f", "beta");
        assertEquals("<span class=\"default\">beta</span>", second);
        assertFalse(second.contains("alpha"));
    }

    // ==================================================================
    // Richer synopses: multiple returns, numeric/operator/bracket tokens,
    // multi-line input, and a long input that forces the scanner to refill
    // its buffer. Only the own function name may carry the functionid span.
    // ==================================================================

    @Test
    public void multipleReturnValuesHighlightOnlyTheOwnName() {
        String html = SynopsisLexer.convert("solve", "[x, y] = solve(A, b)");
        assertTrue(html.contains("<span class=\"functionid\">solve</span>"), html);
        assertTrue(html.contains("<span class=\"default\">x</span>"), html);
        assertTrue(html.contains("<span class=\"default\">y</span>"), html);
        // The other identifiers must NOT be marked as the documented function.
        assertFalse(html.contains("<span class=\"functionid\">A</span>"), html);
    }

    @Test
    public void numbersAndOperatorsAreCarriedThrough() {
        String html = SynopsisLexer.convert("f", "y = f(x, 3.14) + 2");
        assertNotNull(html);
        assertTrue(html.contains("<span class=\"functionid\">f</span>"), html);
        // Numeric literals survive in the rendered output.
        assertTrue(html.contains("3.14"), html);
        assertTrue(html.contains("2"), html);
    }

    @Test
    public void multiLineSynopsisIsHandled() {
        String html = SynopsisLexer.convert("myfun",
                      "y = myfun(x)\n[a, b] = myfun(x, opts)");
        assertNotNull(html);
        // Both occurrences of the own name are highlighted.
        int first = html.indexOf("<span class=\"functionid\">myfun</span>");
        assertTrue(first >= 0, html);
        assertTrue(html.indexOf("<span class=\"functionid\">myfun</span>", first + 1) > first,
                   "the own name must be highlighted on every line");
    }

    @Test
    public void longInputForcesBufferRefillWithoutError() {
        // Build an input well past the initial scanner buffer to exercise the refill path.
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4000; i++) {
            sb.append("y").append(i).append(" = fn(x").append(i).append(")\n");
        }
        String html = SynopsisLexer.convert("fn", sb.toString());
        assertNotNull(html, "a long synopsis must still convert");
        assertTrue(html.contains("<span class=\"functionid\">fn</span>"), "own name highlighted");
    }
}
