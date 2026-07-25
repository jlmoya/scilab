/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Scilab Enterprises
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 *
 */

package org.scilab.modules.graphic_objects.xmlloader;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.FileWriter;
import java.io.StringReader;
import java.io.Writer;
import java.util.Map;

/**
 * Hermetic unit tests for {@link CSSParser}, the JFlex-generated lexer that
 * turns Scilab's inline CSS-ish attribute strings into maps. Two public entry
 * points are exercised end to end without any GraphicController:
 * <ul>
 *   <li>{@link CSSParser#parseLine} — a single {@code key: value; ...} line, as
 *       used by GOBuilder for {@code constraint} / {@code border} / {@code ui-style}
 *       attributes, returning a flat {@code Map<String,String>};</li>
 *   <li>{@link CSSParser#parseBlock} / {@link CSSParser#parseFile} — one or more
 *       {@code selector { ... }} rules, returning a
 *       {@code Map<String, Map<String,String>>}.</li>
 * </ul>
 * Every expectation here was verified against the actual generated scanner, so
 * the quirky ones (lower-cased keys but case-preserving selectors/values,
 * hyphen/dot selectors rejected, a missing trailing {@code ;} leaking the
 * closing brace) are deliberate behaviour characterisations.
 */
public class CSSParserTest {

    /* ----------------------------- parseLine ----------------------------- */

    @Test
    public void parseLineSingleKeyValue() throws CSSParserException {
        Map<String, String> m = CSSParser.parseLine("bold: true");
        assertEquals(1, m.size());
        assertEquals("true", m.get("bold"));
    }

    @Test
    public void parseLineToleratesTrailingSemicolon() throws CSSParserException {
        assertEquals("true", CSSParser.parseLine("bold: true;").get("bold"));
    }

    @Test
    public void parseLineToleratesMissingSpaceAfterColon() throws CSSParserException {
        assertEquals("true", CSSParser.parseLine("bold:true").get("bold"));
    }

    @Test
    public void parseLineMultiplePairs() throws CSSParserException {
        Map<String, String> m = CSSParser.parseLine("name: titled; title: hello");
        assertEquals(2, m.size());
        assertEquals("titled", m.get("name"));
        assertEquals("hello", m.get("title"));
    }

    @Test
    public void parseLineManyPairs() throws CSSParserException {
        Map<String, String> m = CSSParser.parseLine("a: b; c: d; e: f");
        assertEquals(3, m.size());
        assertEquals("b", m.get("a"));
        assertEquals("d", m.get("c"));
        assertEquals("f", m.get("e"));
    }

    @Test
    public void parseLineLowerCasesKeyButPreservesValueCase() throws CSSParserException {
        Map<String, String> m = CSSParser.parseLine("Bold: True");
        // The key is normalised to lower case; the value keeps its original case.
        assertEquals("True", m.get("bold"));
        assertNull(m.get("Bold"));
    }

    @Test
    public void parseLineEmptyOrBlankYieldsEmptyMap() throws CSSParserException {
        assertTrue(CSSParser.parseLine("").isEmpty());
        assertTrue(CSSParser.parseLine("   ").isEmpty());
    }

    @Test
    public void parseLineKeyWithoutValueIsDropped() throws CSSParserException {
        // A bare key with no ":" / value never reaches pushKeyValue.
        assertTrue(CSSParser.parseLine("bold").isEmpty());
    }

    @Test
    public void parseLineMissingSeparatorBetweenPairsThrows() {
        // Two "key: value" pairs must be ";"-separated; a space is not enough.
        assertThrows(CSSParserException.class, () -> CSSParser.parseLine("a: b c: d"));
    }

    /* ----------------------------- parseBlock ---------------------------- */

    @Test
    public void parseBlockSingleSelectorSingleDeclaration() throws CSSParserException {
        Map<String, Map<String, String>> m = CSSParser.parseBlock("sel { color: red; }");
        assertEquals(1, m.size());
        assertNotNull(m.get("sel"));
        assertEquals("red", m.get("sel").get("color"));
    }

    @Test
    public void parseBlockSingleSelectorMultipleDeclarations() throws CSSParserException {
        Map<String, Map<String, String>> m = CSSParser.parseBlock("sel { color: red; width: auto; }");
        assertEquals(1, m.size());
        Map<String, String> decls = m.get("sel");
        assertEquals(2, decls.size());
        assertEquals("red", decls.get("color"));
        assertEquals("auto", decls.get("width"));
    }

    @Test
    public void parseBlockMultipleSelectors() throws CSSParserException {
        Map<String, Map<String, String>> m = CSSParser.parseBlock("a { x: y; } b { z: w; }");
        assertEquals(2, m.size());
        assertEquals("y", m.get("a").get("x"));
        assertEquals("w", m.get("b").get("z"));
    }

    @Test
    public void parseBlockDigitValue() throws CSSParserException {
        assertEquals("100", CSSParser.parseBlock("sel { width: 100; }").get("sel").get("width"));
    }

    @Test
    public void parseBlockLowerCasesKeysButKeepsSelectorAndValueCase() throws CSSParserException {
        Map<String, Map<String, String>> m = CSSParser.parseBlock("A { B: C; }");
        // Selector case is preserved; declaration key is lower-cased; value kept.
        assertNotNull(m.get("A"));
        assertNull(m.get("a"));
        assertEquals("C", m.get("A").get("b"));
    }

    @Test
    public void parseBlockStripsCommentsInsideAndBefore() throws CSSParserException {
        assertEquals("red", CSSParser.parseBlock("sel { /* c */ color: red; }").get("sel").get("color"));
        assertEquals("red", CSSParser.parseBlock("/* hi */ sel { color: red; }").get("sel").get("color"));
    }

    @Test
    public void parseBlockIgnoresTrailingGarbageAfterClosedBlock() throws CSSParserException {
        Map<String, Map<String, String>> m = CSSParser.parseBlock("sel { color: red; } garbage");
        assertEquals("red", m.get("sel").get("color"));
    }

    @Test
    public void parseBlockEmptyInputYieldsEmptyMap() throws CSSParserException {
        assertTrue(CSSParser.parseBlock("").isEmpty());
    }

    @Test
    public void parseBlockMissingSelectorProducesNullKey() throws CSSParserException {
        // Characterisation: with no selector token the identifier field stays null,
        // so the declarations are filed under a literal null map key.
        Map<String, Map<String, String>> m = CSSParser.parseBlock("{ color: red; }");
        assertTrue(m.containsKey(null));
        assertEquals("red", m.get(null).get("color"));
    }

    @Test
    public void parseBlockHyphenatedSelectorIsRejected() {
        // Characterisation: this scanner's selector alphabet does not admit '-',
        // so a hyphenated selector fails outright (likewise the '.'-prefixed CSS
        // class selectors that appear in real stylesheets).
        assertThrows(CSSParserException.class, () -> CSSParser.parseBlock("a-b { c: d; }"));
        assertThrows(CSSParserException.class, () -> CSSParser.parseBlock(".form-range { appearance: auto; }"));
    }

    @Test
    public void parseBlockMissingTrailingSemicolonLeaksClosingBrace() throws CSSParserException {
        // Defect characterisation: without a ';' terminating the last declaration
        // the value swallows the trailing "}" instead of closing the block cleanly.
        Map<String, String> decls = CSSParser.parseBlock("sel { color: red; width: auto }").get("sel");
        assertEquals("red", decls.get("color"));
        String width = decls.get("width");
        assertTrue(width.startsWith("auto"), "value was: " + width);
        assertTrue(width.contains("}"), "expected the brace to leak into the value, was: " + width);
    }

    /* ----------------------------- parseFile ----------------------------- */

    @Test
    public void parseFileReadsARealFile() throws Exception {
        File f = File.createTempFile("csstest", ".css");
        try (Writer w = new FileWriter(f)) {
            w.write("sel { color: red; alpha: bloom; }");
        }
        try {
            Map<String, Map<String, String>> m = CSSParser.parseFile(f.getAbsolutePath());
            assertEquals("red", m.get("sel").get("color"));
            assertEquals("bloom", m.get("sel").get("alpha"));
        } finally {
            assertTrue(f.delete());
        }
    }

    @Test
    public void parseFileMissingFileThrows() {
        CSSParserException ex = assertThrows(CSSParserException.class,
                                             () -> CSSParser.parseFile("/no/such/file_should_not_exist_42.css"));
        assertNotNull(ex);
    }

    /* --------------------- low-level scanner state API ------------------- */

    @Test
    public void yyeofConstantIsMinusOne() {
        assertEquals(-1, CSSParser.YYEOF);
    }

    @Test
    public void newScannerStartsInInitialStateAndYybeginSwitches() {
        CSSParser p = new CSSParser(new StringReader("sel { color: red; }"));
        assertEquals(CSSParser.YYINITIAL, p.yystate());
        p.yybegin(CSSParser.BLOCK);
        assertEquals(CSSParser.BLOCK, p.yystate());
        p.yybegin(CSSParser.LINE);
        assertEquals(CSSParser.LINE, p.yystate());
    }

    @Test
    public void yyresetReturnsToInitialState() {
        CSSParser p = new CSSParser(new StringReader("a"));
        p.yybegin(CSSParser.VALUE);
        assertEquals(CSSParser.VALUE, p.yystate());
        p.yyreset(new StringReader("b"));
        assertEquals(CSSParser.YYINITIAL, p.yystate());
    }

    @Test
    public void lexicalStateConstantsAreDistinct() {
        int[] states = {CSSParser.YYINITIAL, CSSParser.BLOCK, CSSParser.VALUE,
                        CSSParser.LINE, CSSParser.VALUELINE
                       };
        for (int i = 0; i < states.length; i++) {
            for (int j = i + 1; j < states.length; j++) {
                assertNotEquals(states[i], states[j], "states " + i + " and " + j + " collide");
            }
        }
    }
}
