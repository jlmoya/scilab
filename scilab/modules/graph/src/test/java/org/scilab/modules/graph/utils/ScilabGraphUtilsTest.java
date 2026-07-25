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

package org.scilab.modules.graph.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.awt.GraphicsEnvironment;

import javax.swing.Icon;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.scilab.forge.jlatexmath.ParseException;

/**
 * Hermetic unit tests for {@link ScilabGraphUtils}.
 *
 * Only the display-free surface is exercised here: the two in-place
 * {@link StringBuilder} rewriters ({@code removeBlanks} and {@code unescape})
 * are pure logic, and the {@code getTexIcon} error path throws while still
 * parsing (before any glyph layout). The rendering-heavy SVG/batik methods and
 * the successful {@code getTexIcon} layout are only touched behind a
 * non-headless assumption so this class never fails in a headless CI run.
 */
public class ScilabGraphUtilsTest {

    // ----- removeBlanks ---------------------------------------------------

    @Test
    public void removeBlanksStripsLeadingSpacesAndNewlines() {
        StringBuilder sb = new StringBuilder("   \n \nhello");
        ScilabGraphUtils.removeBlanks(sb);
        assertEquals("hello", sb.toString());
    }

    @Test
    public void removeBlanksLeavesInteriorAndTrailingBlanksUntouched() {
        // Only the leading run of ' ' / '\n' is removed; the first non-blank
        // char stops the scan, so inner and trailing blanks survive.
        StringBuilder sb = new StringBuilder("a b \n");
        ScilabGraphUtils.removeBlanks(sb);
        assertEquals("a b \n", sb.toString());
    }

    @Test
    public void removeBlanksOnAllBlankSequenceEmptiesIt() {
        StringBuilder sb = new StringBuilder("   \n\n ");
        ScilabGraphUtils.removeBlanks(sb);
        assertEquals("", sb.toString());
    }

    @Test
    public void removeBlanksOnEmptyStringIsANoOp() {
        StringBuilder sb = new StringBuilder("");
        ScilabGraphUtils.removeBlanks(sb);
        assertEquals("", sb.toString());
    }

    @Test
    public void removeBlanksIgnoresTabsWhichAreNotConsideredBlank() {
        // The scan only treats ' ' and '\n' as blank; a leading tab stops it.
        StringBuilder sb = new StringBuilder("\tx");
        ScilabGraphUtils.removeBlanks(sb);
        assertEquals("\tx", sb.toString());
    }

    // ----- unescape -------------------------------------------------------

    @Test
    public void unescapeReplacesLtGtAmp() {
        StringBuilder sb = new StringBuilder("a&lt;b&gt;c&amp;d");
        ScilabGraphUtils.unescape(sb, 0);
        assertEquals("a<b>c&d", sb.toString());
    }

    @Test
    public void unescapeHandlesAccentedEntities() {
        // Unicode escapes keep the assertion independent of the source encoding:
        // e-acute, e-grave, c-cedilla.
        StringBuilder sb = new StringBuilder("&eacute;&egrave;&ccedil;");
        ScilabGraphUtils.unescape(sb, 0);
        assertEquals("éèç", sb.toString());
    }

    @Test
    public void unescapeMapsRegAndCopyEntitiesToTheirSwappedGlyphs_defect() {
        // Defect characterization: HTML_ESCAPE_TABLE has the &reg; and &copy;
        // rows crossed over - "&reg;" yields U+00A9 ((c) COPYRIGHT SIGN) and
        // "&copy;" yields U+00AE ((R) REGISTERED SIGN), the opposite of the
        // conventional HTML entity meanings. This test pins the current
        // (inverted) behavior rather than the intended one.
        StringBuilder reg = new StringBuilder("&reg;");
        ScilabGraphUtils.unescape(reg, 0);
        assertEquals("©", reg.toString());

        StringBuilder copy = new StringBuilder("&copy;");
        ScilabGraphUtils.unescape(copy, 0);
        assertEquals("®", copy.toString());
    }

    @Test
    public void unescapeConvertsNbspToSpaceAndQuot() {
        StringBuilder sb = new StringBuilder("x&nbsp;&quot;y&quot;");
        ScilabGraphUtils.unescape(sb, 0);
        assertEquals("x \"y\"", sb.toString());
    }

    @Test
    public void unescapeLeavesUnknownEntityUntouched() {
        // "&unknown;" is not in HTML_ESCAPE_TABLE so no replacement happens and
        // the recursion stops (no further '&' found by the branch that matched).
        StringBuilder sb = new StringBuilder("a&unknown;b");
        ScilabGraphUtils.unescape(sb, 0);
        assertEquals("a&unknown;b", sb.toString());
    }

    @Test
    public void unescapeLeavesBareAmpersandWithoutSemicolonUntouched() {
        StringBuilder sb = new StringBuilder("a & b");
        ScilabGraphUtils.unescape(sb, 0);
        assertEquals("a & b", sb.toString());
    }

    @Test
    public void unescapeWithNoAmpersandIsANoOp() {
        StringBuilder sb = new StringBuilder("plain text");
        ScilabGraphUtils.unescape(sb, 0);
        assertEquals("plain text", sb.toString());
    }

    @Test
    public void unescapeStartingIndexSkipsEarlierEntities() {
        // Starting the scan past the first entity leaves it untouched but still
        // converts the later one.
        StringBuilder sb = new StringBuilder("&lt;&gt;");
        ScilabGraphUtils.unescape(sb, 4);
        assertEquals("&lt;>", sb.toString());
    }

    @Test
    public void unescapeRecursesAcrossAdjacentEntities() {
        StringBuilder sb = new StringBuilder("&lt;&lt;&lt;");
        ScilabGraphUtils.unescape(sb, 0);
        assertEquals("<<<", sb.toString());
    }

    // ----- getTexIcon (error path is display-free) ------------------------

    @Test
    public void getTexIconThrowsParseExceptionOnInvalidLatex() {
        // Latex.escape drops the surrounding '$' tags, leaving "\notacmd" which
        // TeXFormula rejects during parsing - before any font layout, so this is
        // safe to assert even in a headless environment.
        assertThrows(ParseException.class, () -> ScilabGraphUtils.getTexIcon("$\\notacmd$", 12.0f));
    }

    @Test
    public void getTexIconBuildsAndCachesIconWhenADisplayIsAvailable() {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());

        Icon first = ScilabGraphUtils.getTexIcon("$x$", 12.0f);
        assertNotNull(first);

        // Same text and size => the WeakHashMap cache returns the same object.
        Icon second = ScilabGraphUtils.getTexIcon("$x$", 12.0f);
        assertSame(first, second);
    }
}
