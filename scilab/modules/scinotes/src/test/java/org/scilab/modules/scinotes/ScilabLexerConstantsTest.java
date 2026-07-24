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

package org.scilab.modules.scinotes;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hermetic unit tests for {@link ScilabLexerConstants}: the pure lexer token-type
 * constants, the {@code TOKENS} name map, {@code getStringRep} and the family of
 * {@code isXxx} classification predicates. No document, GUI or native state.
 */
public class ScilabLexerConstantsTest {

    /* ----------------------------------------------------------------- constants */

    @Test
    public void tokenCountBoundaries() {
        assertEquals(36, ScilabLexerConstants.NUMBEROFTOKENS);
        assertEquals(0, ScilabLexerConstants.DEFAULT, "DEFAULT is the first token");
        assertEquals(35, ScilabLexerConstants.EOF, "EOF is the last token");
        // The token ids run 0..NUMBEROFTOKENS-1, EOF being the highest.
        assertEquals(ScilabLexerConstants.NUMBEROFTOKENS - 1, ScilabLexerConstants.EOF);
    }

    @Test
    public void allTokenIdsAreDistinctAndInRange() {
        int[] ids = {
            ScilabLexerConstants.DEFAULT, ScilabLexerConstants.OPERATOR, ScilabLexerConstants.SKEYWORD,
            ScilabLexerConstants.OSKEYWORD, ScilabLexerConstants.CKEYWORD, ScilabLexerConstants.CONSTANTES,
            ScilabLexerConstants.ID, ScilabLexerConstants.NUMBER, ScilabLexerConstants.SPECIAL,
            ScilabLexerConstants.DOT, ScilabLexerConstants.TRANSP, ScilabLexerConstants.OPENCLOSE,
            ScilabLexerConstants.STRING, ScilabLexerConstants.COMMENT, ScilabLexerConstants.FKEYWORD,
            ScilabLexerConstants.COMMANDS, ScilabLexerConstants.MACROS, ScilabLexerConstants.MACROINFILE,
            ScilabLexerConstants.FIELD, ScilabLexerConstants.AUTHORS, ScilabLexerConstants.URL,
            ScilabLexerConstants.MAIL, ScilabLexerConstants.WHITE, ScilabLexerConstants.TAB,
            ScilabLexerConstants.LATEX, ScilabLexerConstants.LATEXINSTRING, ScilabLexerConstants.VARIABLES,
            ScilabLexerConstants.INPUTOUTPUTARGS, ScilabLexerConstants.WHITE_COMMENT, ScilabLexerConstants.TAB_COMMENT,
            ScilabLexerConstants.WHITE_STRING, ScilabLexerConstants.TAB_STRING, ScilabLexerConstants.ELSEIF,
            ScilabLexerConstants.ERROR, ScilabLexerConstants.TODO, ScilabLexerConstants.EOF
        };
        assertEquals(36, ids.length, "all 36 declared token ids are covered");
        Set<Integer> distinct = new HashSet<Integer>();
        for (int id : ids) {
            assertTrue(id >= 0 && id < ScilabLexerConstants.NUMBEROFTOKENS, "token id in [0,36): " + id);
            assertTrue(distinct.add(id), "duplicate token id: " + id);
        }
        assertEquals(36, distinct.size(), "the 36 token ids are pairwise distinct");
    }

    /* -------------------------------------------------------------------- TOKENS */

    @Test
    public void tokensMapHasExpectedEntries() {
        Map<String, Integer> tokens = ScilabLexerConstants.TOKENS;
        assertEquals(29, tokens.size(), "29 named tokens are registered");
        assertEquals(Integer.valueOf(ScilabLexerConstants.DEFAULT), tokens.get("Default"));
        assertEquals(Integer.valueOf(ScilabLexerConstants.COMMANDS), tokens.get("Primitive"));
        assertEquals(Integer.valueOf(ScilabLexerConstants.MACROS), tokens.get("Macro"));
        assertEquals(Integer.valueOf(ScilabLexerConstants.SKEYWORD), tokens.get("Structure"));
        assertEquals(Integer.valueOf(ScilabLexerConstants.STRING), tokens.get("String"));
        assertEquals(Integer.valueOf(ScilabLexerConstants.TODO), tokens.get("Todo"));
        assertEquals(Integer.valueOf(ScilabLexerConstants.OPENCLOSE), tokens.get("OpenClose"));
        assertNotNull(tokens.get("Identifier"));
        assertFalse(tokens.containsKey("NoSuchToken"));
    }

    /* --------------------------------------------------------------- getStringRep */

    @Test
    public void getStringRepIsInverseOfTokensMap() {
        // Every registered (name -> id) pair must round-trip back to its name,
        // because getStringRep builds the exact inverse of TOKENS.
        for (Map.Entry<String, Integer> e : ScilabLexerConstants.TOKENS.entrySet()) {
            assertEquals(e.getKey(), ScilabLexerConstants.getStringRep(e.getValue()),
                         "round-trip failed for token name " + e.getKey());
        }
    }

    @Test
    public void getStringRepMapsStructureAliasesToStructure() {
        // OSKEYWORD and ELSEIF are not TOKENS values but are special-cased to "Structure".
        assertEquals("Structure", ScilabLexerConstants.getStringRep(ScilabLexerConstants.OSKEYWORD));
        assertEquals("Structure", ScilabLexerConstants.getStringRep(ScilabLexerConstants.ELSEIF));
        // SKEYWORD reaches "Structure" through the inverse map instead.
        assertEquals("Structure", ScilabLexerConstants.getStringRep(ScilabLexerConstants.SKEYWORD));
    }

    @Test
    public void getStringRepFallsBackToDefaultForUnknownIds() {
        assertEquals("Default", ScilabLexerConstants.getStringRep(ScilabLexerConstants.AUTHORS),
                     "AUTHORS is not a named token so it degrades to Default");
        assertEquals("Default", ScilabLexerConstants.getStringRep(ScilabLexerConstants.ERROR));
        assertEquals("Default", ScilabLexerConstants.getStringRep(-1));
        assertEquals("Default", ScilabLexerConstants.getStringRep(9999));
    }

    /* ------------------------------------------------------------------ isLaTeX */

    @Test
    public void isLaTeX() {
        assertTrue(ScilabLexerConstants.isLaTeX(ScilabLexerConstants.LATEX));
        assertTrue(ScilabLexerConstants.isLaTeX(ScilabLexerConstants.LATEXINSTRING));
        assertFalse(ScilabLexerConstants.isLaTeX(ScilabLexerConstants.STRING));
        assertFalse(ScilabLexerConstants.isLaTeX(ScilabLexerConstants.COMMENT));
    }

    /* ------------------------------------------------------------------ isString */

    @Test
    public void isString() {
        assertTrue(ScilabLexerConstants.isString(ScilabLexerConstants.STRING));
        assertTrue(ScilabLexerConstants.isString(ScilabLexerConstants.WHITE_STRING));
        assertTrue(ScilabLexerConstants.isString(ScilabLexerConstants.TAB_STRING));
        assertFalse(ScilabLexerConstants.isString(ScilabLexerConstants.ID));
        assertFalse(ScilabLexerConstants.isString(ScilabLexerConstants.WHITE));
        assertFalse(ScilabLexerConstants.isString(ScilabLexerConstants.LATEXINSTRING));
    }

    /* ----------------------------------------------------------------- isComment */

    @Test
    public void isComment() {
        assertTrue(ScilabLexerConstants.isComment(ScilabLexerConstants.COMMENT));
        assertTrue(ScilabLexerConstants.isComment(ScilabLexerConstants.AUTHORS));
        assertTrue(ScilabLexerConstants.isComment(ScilabLexerConstants.URL));
        assertTrue(ScilabLexerConstants.isComment(ScilabLexerConstants.MAIL));
        assertTrue(ScilabLexerConstants.isComment(ScilabLexerConstants.LATEX));
        assertTrue(ScilabLexerConstants.isComment(ScilabLexerConstants.WHITE_COMMENT));
        assertTrue(ScilabLexerConstants.isComment(ScilabLexerConstants.TAB_COMMENT));
        assertTrue(ScilabLexerConstants.isComment(ScilabLexerConstants.TODO));
        assertFalse(ScilabLexerConstants.isComment(ScilabLexerConstants.STRING));
        // LaTeX-in-string is a LaTeX token but NOT a comment.
        assertFalse(ScilabLexerConstants.isComment(ScilabLexerConstants.LATEXINSTRING));
    }

    /* ---------------------------------------------------------------- isHelpable */

    @Test
    public void isHelpable() {
        for (int t : new int[] {
                    ScilabLexerConstants.ID, ScilabLexerConstants.COMMANDS, ScilabLexerConstants.MACROS,
                    ScilabLexerConstants.OPERATOR, ScilabLexerConstants.FKEYWORD, ScilabLexerConstants.CKEYWORD,
                    ScilabLexerConstants.OSKEYWORD, ScilabLexerConstants.SKEYWORD, ScilabLexerConstants.ELSEIF,
                    ScilabLexerConstants.CONSTANTES, ScilabLexerConstants.VARIABLES, ScilabLexerConstants.FIELD }) {
            assertTrue(ScilabLexerConstants.isHelpable(t), "expected helpable: " + t);
        }
        assertFalse(ScilabLexerConstants.isHelpable(ScilabLexerConstants.MACROINFILE));
        assertFalse(ScilabLexerConstants.isHelpable(ScilabLexerConstants.NUMBER));
        assertFalse(ScilabLexerConstants.isHelpable(ScilabLexerConstants.INPUTOUTPUTARGS));
        assertFalse(ScilabLexerConstants.isHelpable(ScilabLexerConstants.STRING));
        assertFalse(ScilabLexerConstants.isHelpable(ScilabLexerConstants.COMMENT));
    }

    /* -------------------------------------------------------------- isSearchable */

    @Test
    public void isSearchable() {
        for (int t : new int[] {
                    ScilabLexerConstants.ID, ScilabLexerConstants.COMMANDS, ScilabLexerConstants.MACROS,
                    ScilabLexerConstants.MACROINFILE, ScilabLexerConstants.INPUTOUTPUTARGS, ScilabLexerConstants.OPERATOR,
                    ScilabLexerConstants.FKEYWORD, ScilabLexerConstants.CKEYWORD, ScilabLexerConstants.OSKEYWORD,
                    ScilabLexerConstants.SKEYWORD, ScilabLexerConstants.ELSEIF, ScilabLexerConstants.NUMBER,
                    ScilabLexerConstants.CONSTANTES, ScilabLexerConstants.VARIABLES, ScilabLexerConstants.FIELD }) {
            assertTrue(ScilabLexerConstants.isSearchable(t), "expected searchable: " + t);
        }
        assertFalse(ScilabLexerConstants.isSearchable(ScilabLexerConstants.STRING));
        assertFalse(ScilabLexerConstants.isSearchable(ScilabLexerConstants.COMMENT));
    }

    @Test
    public void searchableIsStrictlyWiderThanHelpable() {
        // MACROINFILE, INPUTOUTPUTARGS and NUMBER are searchable but not helpable.
        for (int t : new int[] { ScilabLexerConstants.MACROINFILE, ScilabLexerConstants.INPUTOUTPUTARGS,
                                 ScilabLexerConstants.NUMBER }) {
            assertTrue(ScilabLexerConstants.isSearchable(t));
            assertFalse(ScilabLexerConstants.isHelpable(t));
        }
    }

    /* ---------------------------------------------------------------- isOpenable */

    @Test
    public void isOpenable() {
        assertTrue(ScilabLexerConstants.isOpenable(ScilabLexerConstants.MACROS));
        assertTrue(ScilabLexerConstants.isOpenable(ScilabLexerConstants.MACROINFILE));
        assertFalse(ScilabLexerConstants.isOpenable(ScilabLexerConstants.ID));
        assertFalse(ScilabLexerConstants.isOpenable(ScilabLexerConstants.COMMANDS));
    }

    /* --------------------------------------------------------------- isMatchable */

    @Test
    public void isMatchable() {
        assertTrue(ScilabLexerConstants.isMatchable(ScilabLexerConstants.OPENCLOSE));
        assertTrue(ScilabLexerConstants.isMatchable(ScilabLexerConstants.FKEYWORD));
        assertTrue(ScilabLexerConstants.isMatchable(ScilabLexerConstants.OSKEYWORD));
        assertTrue(ScilabLexerConstants.isMatchable(ScilabLexerConstants.ELSEIF));
        // A plain structure keyword ('then'/'else') is NOT itself a matchable opener.
        assertFalse(ScilabLexerConstants.isMatchable(ScilabLexerConstants.SKEYWORD));
        assertFalse(ScilabLexerConstants.isMatchable(ScilabLexerConstants.ID));
    }

    /* --------------------------------------------------------------- isClickable */

    @Test
    public void isClickable() {
        assertTrue(ScilabLexerConstants.isClickable(ScilabLexerConstants.URL));
        assertTrue(ScilabLexerConstants.isClickable(ScilabLexerConstants.MAIL));
        assertTrue(ScilabLexerConstants.isClickable(ScilabLexerConstants.MACROS));
        assertTrue(ScilabLexerConstants.isClickable(ScilabLexerConstants.MACROINFILE));
        assertFalse(ScilabLexerConstants.isClickable(ScilabLexerConstants.ID));
        assertFalse(ScilabLexerConstants.isClickable(ScilabLexerConstants.COMMANDS));
    }

    /* --------------------------------------------------------------- isOpenClose */

    @Test
    public void isOpenClose() {
        assertTrue(ScilabLexerConstants.isOpenClose(ScilabLexerConstants.OPENCLOSE));
        assertFalse(ScilabLexerConstants.isOpenClose(ScilabLexerConstants.FKEYWORD));
        assertFalse(ScilabLexerConstants.isOpenClose(ScilabLexerConstants.DEFAULT));
    }
}
