/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 *
 */

package org.scilab.modules.guibuilder.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

public class ScilabTokenStreamTest {

    @Test
    public void everyTokenRangeIndexesBackIntoTheSource() {
        // This is the property the whole writer depends on. If a token's range
        // does not slice its own text out of the source, every rewrite is wrong.
        String src = "h = uicontrol(f, \"style\", \"pushbutton\"); // make it\n";
        for (Token t : ScilabTokenStream.tokenize(src)) {
            if (t.type() != Token.Type.EOF) {
                assertEquals(t.text(), src.substring(t.range().start(), t.range().end()),
                             "token " + t.type() + " does not slice back to its own text");
            }
        }
    }

    @Test
    public void tokensCoverTheSourceWithNoGaps() {
        // Comments and whitespace are tokens too. A gap would mean bytes the
        // writer cannot account for, and formatting would be lost on save.
        String src = "a = 1;   // note\nb = 2;\n";
        List<Token> tokens = ScilabTokenStream.tokenize(src);
        int cursor = 0;
        for (Token t : tokens) {
            if (t.type() == Token.Type.EOF) {
                continue;
            }
            assertEquals(cursor, t.range().start(), "gap or overlap before " + t.text());
            cursor = t.range().end();
        }
        assertEquals(src.length(), cursor, "tokens do not reach the end of the source");
    }

    @Test
    public void stringsAndCommentsAreRecognisedAsSuch() {
        List<Token> tokens = ScilabTokenStream.tokenize("x = \"hi\"; // done\n");
        assertTrue(tokens.stream().anyMatch(t -> t.type() == Token.Type.STRING && t.text().equals("\"hi\"")));
        assertTrue(tokens.stream().anyMatch(t -> t.type() == Token.Type.COMMENT && t.text().startsWith("//")));
    }

    @Test
    public void anEmptySourceYieldsOnlyEof() {
        List<Token> tokens = ScilabTokenStream.tokenize("");
        assertEquals(1, tokens.size());
        assertEquals(Token.Type.EOF, tokens.get(0).type());
    }

    @Test
    public void stringImmediatelyFollowedByAnotherStringIsNotReadAsTranspose() {
        // Real Scilab sets its transpose-eligibility flag to false the moment
        // a string closes -- a string is never a transpose trigger
        // (scanscilab.ll:791-817, scilab.jflex:499-503). If the scanner ever
        // left that flag set after closing a string, the next string's
        // opening quote would be misread as a false transpose instead of
        // opening a new string, breaking the ordinary string-array idiom.
        List<Token> tokens = significant(ScilabTokenStream.tokenize("['a' 'b']"));
        assertEquals(
            List.of(Token.Type.PUNCTUATION, Token.Type.STRING, Token.Type.STRING, Token.Type.PUNCTUATION),
            types(tokens));
        assertEquals("'a'", tokens.get(1).text());
        assertEquals("'b'", tokens.get(2).text());

        tokens = significant(ScilabTokenStream.tokenize("\"hi\" 'x'"));
        assertEquals(List.of(Token.Type.STRING, Token.Type.STRING), types(tokens));
        assertEquals("\"hi\"", tokens.get(0).text());
        assertEquals("'x'", tokens.get(1).text());
    }

    @Test
    public void whitespaceBeforeAQuoteForcesAStringButAdjacentQuoteStaysATranspose() {
        // Scilab's {spaces}{quote} rule (scanscilab.ll:776-787) makes any
        // space or tab directly before a quote open a new string
        // unconditionally, whatever token preceded the whitespace -- even an
        // identifier, which would otherwise make the quote a transpose.
        List<Token> tokens = significant(ScilabTokenStream.tokenize("A ';"));
        assertEquals(List.of(Token.Type.IDENTIFIER, Token.Type.STRING), types(tokens));
        assertEquals("A", tokens.get(0).text());
        assertEquals("';", tokens.get(1).text());

        // The other half of the same rule: with no whitespace in between, a
        // quote right after a closing bracket is still a genuine transpose.
        tokens = significant(ScilabTokenStream.tokenize("h(1)'"));
        assertEquals(
            List.of(Token.Type.IDENTIFIER, Token.Type.PUNCTUATION, Token.Type.NUMBER,
                    Token.Type.PUNCTUATION, Token.Type.OPERATOR),
            types(tokens));
        assertEquals("'", tokens.get(4).text());
    }

    @Test
    public void anUnterminatedStringStopsAtTheLineSoTheNextLineStillTokenizesAsCode() {
        // A bare newline inside an open string is a hard parse error in real
        // Scilab -- strings cannot cross lines (scanscilab.ll:1323-1330 for
        // single-quoted strings, :1382-1389 for double-quoted ones). This
        // scanner has no ERROR token to raise instead, but a missing closing
        // quote must not swallow every following line of real code the way
        // an unbounded scan would; it stops the string at the line boundary,
        // the same way this file's own "//" comment handling already does.
        String src = "a = \"unterminated;\nb = 2;\n";
        List<Token> tokens = significant(ScilabTokenStream.tokenize(src));
        assertEquals(
            List.of(Token.Type.IDENTIFIER, Token.Type.OPERATOR, Token.Type.STRING,
                    Token.Type.IDENTIFIER, Token.Type.OPERATOR, Token.Type.NUMBER, Token.Type.PUNCTUATION),
            types(tokens));
        assertEquals("\"unterminated;", tokens.get(2).text(), "the string must stop before the newline, not consume it");
        assertEquals("b", tokens.get(3).text(), "the second line must tokenize as code, not more string content");

        // Properties 1 and 2 still hold even for this malformed input: every
        // token slices back to its own text (asserted implicitly by `token()`
        // building both from the same start/end) and there is still no gap.
        int cursor = 0;
        for (Token t : ScilabTokenStream.tokenize(src)) {
            if (t.type() == Token.Type.EOF) {
                continue;
            }
            assertEquals(cursor, t.range().start(), "gap or overlap before " + t.text());
            cursor = t.range().end();
        }
        assertEquals(src.length(), cursor, "tokens do not reach the end of the source");
    }

    /** Drops {@code WHITESPACE} and {@code EOF} so a test can assert on the meaningful tokens only. */
    private static List<Token> significant(List<Token> tokens) {
        return tokens.stream()
            .filter(t -> t.type() != Token.Type.WHITESPACE && t.type() != Token.Type.EOF)
            .toList();
    }

    private static List<Token.Type> types(List<Token> tokens) {
        return tokens.stream().map(Token::type).toList();
    }
}
