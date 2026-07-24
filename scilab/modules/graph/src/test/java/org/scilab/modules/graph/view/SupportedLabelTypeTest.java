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

package org.scilab.modules.graph.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for the {@link SupportedLabelType} enum.
 *
 * The MathML detection branch of getFromText/getFromHTML is intentionally not
 * exercised: it triggers LoadClassPath.loadOnUse (classpath side effects). The
 * Latex/HTML detection paths and the escape() logic are pure and covered here.
 */
public class SupportedLabelTypeTest {

    @Test
    public void enumHasThreeValuesWithHtmlDefaultFirst() {
        assertEquals(3, SupportedLabelType.values().length);
        assertEquals(0, SupportedLabelType.HTML.ordinal());
        assertSame(SupportedLabelType.HTML, SupportedLabelType.valueOf("HTML"));
        assertSame(SupportedLabelType.Latex, SupportedLabelType.valueOf("Latex"));
        assertSame(SupportedLabelType.MathML, SupportedLabelType.valueOf("MathML"));
    }

    @Test
    public void getFromTextDetectsLatexWhenWrappedInDollars() {
        assertSame(SupportedLabelType.Latex, SupportedLabelType.getFromText("$a+b$"));
    }

    @Test
    public void getFromTextSingleDollarIsLatex_edgeCase() {
        // For "$" the first and last char are the same char, so both tag checks
        // pass and it is classified as Latex.
        assertSame(SupportedLabelType.Latex, SupportedLabelType.getFromText("$"));
    }

    @Test
    public void getFromTextRequiresBothDelimiters() {
        assertSame(SupportedLabelType.HTML, SupportedLabelType.getFromText("$a+b"));
        assertSame(SupportedLabelType.HTML, SupportedLabelType.getFromText("a+b$"));
    }

    @Test
    public void getFromTextDefaultsToHtml() {
        assertSame(SupportedLabelType.HTML, SupportedLabelType.getFromText("hello"));
    }

    @Test
    public void getFromTextEmptyStringIsHtml() {
        assertSame(SupportedLabelType.HTML, SupportedLabelType.getFromText(""));
    }

    @Test
    public void getFromHtmlDelegatesToTextWhenNotAngleBracket() {
        // A payload not starting with '<' is routed through getFromText.
        assertSame(SupportedLabelType.Latex, SupportedLabelType.getFromHTML("$a+b$"));
        assertSame(SupportedLabelType.HTML, SupportedLabelType.getFromHTML("plain"));
        assertSame(SupportedLabelType.HTML, SupportedLabelType.getFromHTML(""));
    }

    @Test
    public void escapeHtmlLeavesPlainTextUntouched() {
        assertEquals("plain", SupportedLabelType.HTML.escape("plain"));
    }

    @Test
    public void escapeLatexStripsSurroundingDollarTags() {
        assertEquals("x+y", SupportedLabelType.Latex.escape("$x+y$"));
    }

    @Test
    public void escapeLatexRemovesHtmlLineBreaks() {
        assertEquals("ab", SupportedLabelType.Latex.escape("$a<br>b$"));
    }

    @Test
    public void escapeLatexUnescapesHtmlEntities() {
        assertEquals("a<b", SupportedLabelType.Latex.escape("$a&lt;b$"));
    }

    @Test
    public void escapeMathMLStripsCircumflexTags() {
        // escape() itself has no classpath side effects (unlike getFromText).
        assertEquals("m", SupportedLabelType.MathML.escape("^m^"));
    }
}
