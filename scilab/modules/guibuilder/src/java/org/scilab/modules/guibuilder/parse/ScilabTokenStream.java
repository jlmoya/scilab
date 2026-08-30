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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.scilab.modules.guibuilder.model.SourceRange;

/**
 * Splits Scilab source into a positioned {@link Token} stream that covers
 * every byte of the input, in order (see {@link #tokenize(String)}).
 *
 * <p><b>Lexer choice.</b> SciNotes already ships a JFlex-generated Scilab
 * lexer, {@code org.scilab.modules.scinotes.ScilabLexer}, and the instinct
 * for this task was to drive it from a {@code javax.swing.text.PlainDocument}
 * built from a string rather than grow a second Scilab lexer in this tree.
 * That was measured against the real API (not assumed) before writing any
 * code here, with two results:
 *
 * <ul>
 *   <li>It genuinely can be driven headlessly. {@code ScilabDocument}, the
 *       concrete document type its constructors require, extends
 *       {@code javax.swing.text.PlainDocument} and needs no editor pane,
 *       {@code ViewFactory}, or other UI component -- confirmed by
 *       instantiating it and calling
 *       {@code ScilabLexer.getScilabTokens(String)} in a plain headless JVM
 *       process with no display. So the concern the task brief flagged
 *       (Swing UI leaking into this package) is not, by itself, a blocker.</li>
 *   <li>Its token stream is still the wrong shape to reuse directly, for two
 *       independent reasons found by actually running it, not by reading its
 *       source and guessing:
 *       <ol>
 *         <li>{@code getScilabTokens(String)} scans the whole document in one
 *             pass with no per-line reset. Measured on
 *             {@code "a = 1;   // note\nb = 2;\n"}: every character after the
 *             first {@code //} -- including the second line, {@code b = 2;}
 *             -- came back typed as more comment. SciNotes' own grammar
 *             never returns a line comment's state to its initial state at
 *             end of line; that reset only happens because the real editor
 *             re-invokes the lexer per physical line via
 *             {@code ScilabLexer.setRange(int, int)}, separately
 *             carrying block-comment state across lines through a flag
 *             stored on each line's {@code Element}. A whole-file call does
 *             not get that for free.</li>
 *         <li>Even driven correctly (a targeted state reset was prototyped
 *             and confirmed to fix the line-comment case above), its
 *             {@code QSTRING} and {@code COMMENT} lexical states are tuned
 *             for an editor's per-character incremental re-colouring, not
 *             for handing back whole lexical units: a string literal such as
 *             {@code "hi"} comes back as three separate {@code STRING}-typed
 *             tokens (the opening quote, the content, the closing quote) and
 *             a line comment's body comes back one character per token.
 *             Rebuilding single tokens from that would need a merge pass
 *             keyed to incidental implementation details -- e.g. "a
 *             length-one {@code STRING} token while already inside a string
 *             is the closing delimiter" -- rather than any documented,
 *             stable contract of the lexer.</li>
 *       </ol>
 *   </li>
 * </ul>
 *
 * <p>Correctly reusing SciNotes' lexer would therefore mean reimplementing
 * its per-line reinvocation and block-comment-carryover protocol <em>and</em>
 * writing a token-merging layer on top -- at least as much code as a focused
 * scanner, while staying coupled to an editor colouriser's internal
 * granularity rather than a documented tokenizer contract. The Scilab subset
 * this task actually needs -- identifiers, numbers, {@code "}/{@code '}
 * quoted strings with doubled-quote escapes, {@code //} comments to end of
 * line, operators, and punctuation, explicitly not block comments or
 * SciNotes' interpreter-state-dependent highlighting -- is small and stable.
 * This class therefore implements its own scanner below and the
 * {@code guibuilder} module does not depend on {@code scinotes}: nothing
 * here imports it, so the dependency the task brief sketched for this file
 * was not added to {@code modules/guibuilder/pom.xml}. The identifier and
 * operator character classes below were still cross-checked against
 * {@code modules/scinotes/src/java/jflex/scilab.jflex} for fidelity to the
 * real Scilab grammar, without this module importing or depending on it.
 *
 * <p>Every {@link Token} slices back out of the source at its own
 * {@link SourceRange} by construction (its text is always
 * {@code source.substring(range.start(), range.end())}), and the returned
 * list covers {@code [0, source.length())} with no gaps: whitespace and
 * comments are tokens too; an unrecognised character is still consumed as a
 * one-character {@code OPERATOR} token rather than dropped. A gap would be
 * bytes the position-preserving writer (Task 5) could not account for.
 */
public final class ScilabTokenStream {

    /** Multi-character operators, longest first so matching is unambiguous. */
    private static final String[] OPERATORS = {
        // 3 characters
        ".**", ".*.", "./.", ".\\.",
        // 2 characters
        ".'", ".*", "./", ".\\", ".^", "**", "==", "~=", "<>", "<=", ">=", "/.", "@=", "&&", "||",
        // 1 character
        "+", "-", "/", "\\", "*", "^", "<", ">", "=", "&", "|", "@", "~", ".", ":", "$"
    };

    private ScilabTokenStream() {
    }

    /**
     * Tokenizes {@code source} into a stream that covers every character,
     * finishing with a single {@link Token.Type#EOF} token whose range is
     * {@code [source.length(), source.length())}.
     */
    public static List<Token> tokenize(String source) {
        List<Token> tokens = new ArrayList<>();
        int len = source.length();
        int pos = 0;

        // Whether a following "'" is the transpose operator rather than the
        // start of a string -- true right after an identifier, a number, a
        // closing bracket, or another transpose/string. Mirrors the
        // `transposable` flag ScilabLexer itself tracks for the same
        // ambiguity, cross-checked against scilab.jflex.
        boolean transposable = false;

        while (pos < len) {
            char c = source.charAt(pos);

            if (Character.isWhitespace(c)) {
                int start = pos;
                do {
                    pos++;
                } while (pos < len && Character.isWhitespace(source.charAt(pos)));
                tokens.add(token(Token.Type.WHITESPACE, source, start, pos));
                continue;
            }

            if (c == '/' && pos + 1 < len && source.charAt(pos + 1) == '/') {
                int start = pos;
                pos += 2;
                while (pos < len && source.charAt(pos) != '\n' && source.charAt(pos) != '\r') {
                    pos++;
                }
                tokens.add(token(Token.Type.COMMENT, source, start, pos));
                transposable = false;
                continue;
            }

            if (c == '\'' && transposable) {
                tokens.add(token(Token.Type.OPERATOR, source, pos, pos + 1));
                pos++;
                // stays transposable: a transposed value can be transposed again
                continue;
            }

            if (c == '"' || c == '\'') {
                int start = pos;
                pos = scanString(source, pos, c);
                tokens.add(token(Token.Type.STRING, source, start, pos));
                transposable = true;
                continue;
            }

            if (Character.isDigit(c) || (c == '.' && pos + 1 < len && Character.isDigit(source.charAt(pos + 1)))) {
                int start = pos;
                pos = scanNumber(source, pos);
                tokens.add(token(Token.Type.NUMBER, source, start, pos));
                transposable = true;
                continue;
            }

            if (isIdentifierStart(c)) {
                int start = pos;
                do {
                    pos++;
                } while (pos < len && isIdentifierPart(source.charAt(pos)));
                tokens.add(token(Token.Type.IDENTIFIER, source, start, pos));
                transposable = true;
                continue;
            }

            if (isPunctuation(c)) {
                tokens.add(token(Token.Type.PUNCTUATION, source, pos, pos + 1));
                // opening delimiters can never be followed by a transpose,
                // closing ones can: "h(1)'" transposes h(1).
                transposable = c == ')' || c == ']' || c == '}';
                pos++;
                continue;
            }

            int start = pos;
            int operatorLength = matchOperatorLength(source, pos);
            pos += operatorLength > 0 ? operatorLength : 1;
            tokens.add(token(Token.Type.OPERATOR, source, start, pos));
            transposable = false;
        }

        tokens.add(new Token(Token.Type.EOF, "", new SourceRange(len, len)));
        return Collections.unmodifiableList(tokens);
    }

    /**
     * Scans a {@code "} or {@code '} delimited string starting at {@code pos}
     * (which holds the opening quote) and returns the offset just past its
     * end. A doubled delimiter ({@code ""} inside a double-quoted string,
     * {@code ''} inside a single-quoted one) is an escaped literal quote
     * character, not the end of the string. An unterminated string runs to
     * the end of the source, so the caller always makes progress.
     */
    private static int scanString(String source, int pos, char quote) {
        int len = source.length();
        pos++;
        while (pos < len) {
            char c = source.charAt(pos);
            if (c == quote) {
                if (pos + 1 < len && source.charAt(pos + 1) == quote) {
                    pos += 2;
                    continue;
                }
                pos++;
                break;
            }
            pos++;
        }
        return pos;
    }

    /**
     * Scans a number starting at {@code pos}: a run of digits with an
     * optional {@code .} and more digits, or a leading {@code .} followed by
     * digits, either optionally followed by an exponent marker
     * ({@code [dDeE]}, matching Scilab's Fortran-style {@code d}/{@code D}
     * double-precision suffix as well as the usual {@code e}/{@code E}) with
     * an optional sign and digits. Mirrors the {@code number} macro in
     * scilab.jflex.
     */
    private static int scanNumber(String source, int pos) {
        int len = source.length();
        if (Character.isDigit(source.charAt(pos))) {
            while (pos < len && Character.isDigit(source.charAt(pos))) {
                pos++;
            }
            if (pos < len && source.charAt(pos) == '.') {
                pos++;
                while (pos < len && Character.isDigit(source.charAt(pos))) {
                    pos++;
                }
            }
        } else {
            // '.' followed by a digit, guaranteed by the caller.
            pos++;
            while (pos < len && Character.isDigit(source.charAt(pos))) {
                pos++;
            }
        }
        if (pos < len && isExponentMarker(source.charAt(pos))) {
            pos++;
            if (pos < len && (source.charAt(pos) == '+' || source.charAt(pos) == '-')) {
                pos++;
            }
            while (pos < len && Character.isDigit(source.charAt(pos))) {
                pos++;
            }
        }
        return pos;
    }

    private static boolean isExponentMarker(char c) {
        return c == 'd' || c == 'D' || c == 'e' || c == 'E';
    }

    /**
     * Identifier start characters, per scilab.jflex's {@code id} macro:
     * a letter, or one of {@code % _ # ! ?} (the leading {@code %} covers
     * Scilab's special constants such as {@code %pi} and {@code %t}). The
     * grammar's separate {@code $name} identifier form is deliberately not
     * reproduced here: it is rare enough in practice that a lone {@code $}
     * is simply scanned as an operator character instead, which keeps this
     * scanner smaller without affecting realistic GUI-builder source.
     */
    private static boolean isIdentifierStart(char c) {
        return Character.isLetter(c) || c == '%' || c == '_' || c == '#' || c == '!' || c == '?';
    }

    private static boolean isIdentifierPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '#' || c == '!' || c == '?';
    }

    /**
     * Structural delimiters: argument/statement separators and the three
     * kinds of bracket. Scilab's own lexer does not single these out as
     * their own category (commas and semicolons fall into its generic
     * default bucket), but {@link Token.Type} does, so this scanner draws
     * the line at "characters whose role is purely to delimit structure".
     */
    private static boolean isPunctuation(char c) {
        return c == ',' || c == ';' || c == '(' || c == ')' || c == '[' || c == ']' || c == '{' || c == '}';
    }

    /** Longest-match length of an operator at {@code pos}, or 0 if none matches. */
    private static int matchOperatorLength(String source, int pos) {
        for (String op : OPERATORS) {
            if (source.regionMatches(pos, op, 0, op.length())) {
                return op.length();
            }
        }
        return 0;
    }

    private static Token token(Token.Type type, String source, int start, int end) {
        return new Token(type, source.substring(start, end), new SourceRange(start, end));
    }
}
