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

import java.awt.Color;
import java.io.File;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.helpers.AttributesImpl;

import org.scilab.forge.jlatexmath.TeXConstants;

/**
 * Hermetic unit tests for {@link CopyConvert}.
 *
 * <p>{@code CopyConvert} is a SAX {@code DefaultHandler} that flattens a DocBook
 * document and converts embedded graphics; its rendering paths need external
 * tools (latex/dvips/gs), Batik and jlatexmath, so they are out of scope here.
 * What <em>is</em> pure and worth pinning:
 *
 * <ul>
 *   <li>the {@link CopyConvert.LaTeXElement} inner class, whose constructor parses
 *       a {@code <latex>} element's attributes (align/style/size/fg/bg) into fields
 *       and an {@code attribs} echo string — no I/O, no rendering; and</li>
 *   <li>the argument parsing in {@link CopyConvert#main} (usage / bad-args exit
 *       codes, and the failure code when the input file cannot be read).</li>
 * </ul>
 *
 * The test lives in the class's own package so it can reach the package-private
 * fields of the {@code protected} {@code LaTeXElement} inner class.
 */
public class CopyConvertTest {

    private static AttributesImpl attrs(String... localNameThenValue) {
        AttributesImpl a = new AttributesImpl();
        for (int i = 0; i < localNameThenValue.length; i += 2) {
            String local = localNameThenValue[i];
            String value = localNameThenValue[i + 1];
            a.addAttribute("", local, local, "CDATA", value);
        }
        return a;
    }

    private static CopyConvert.LaTeXElement latexElem(boolean exported, String... attrs) {
        // LaTeXElement is a non-static inner class => needs an enclosing instance.
        return new CopyConvert().new LaTeXElement(attrs(attrs), exported);
    }

    // ---- LaTeXElement: defaults ----------------------------------------

    @Test
    public void latexElementWithNoAttributesUsesDefaults() {
        CopyConvert.LaTeXElement el = latexElem(false);
        assertEquals(18, el.size);
        assertEquals(TeXConstants.STYLE_DISPLAY, el.disp);
        assertEquals("", el.align);
        assertNull(el.fg);
        assertNull(el.bg);
        assertFalse(el.exported);
        // The default 'code' skeleton is untouched when there is no align attribute.
        assertEquals("mediaobject><imageobject><imagedata", el.code);
        // attribs echoes align/size/style; bg/fg are omitted while null.
        assertEquals(" align='' size='18' style='display'", el.attribs);
    }

    @Test
    public void exportedFlagIsStored() {
        assertTrue(latexElem(true).exported);
    }

    // ---- LaTeXElement: align -------------------------------------------

    @Test
    public void alignAttributeIsRecordedAndFoldedIntoCode() {
        CopyConvert.LaTeXElement el = latexElem(false, "align", "center");
        assertEquals("center", el.align);
        assertEquals("mediaobject><imageobject><imagedata align='center'", el.code);
        assertTrue(el.attribs.startsWith(" align='center' size='18' style='display'"),
                   "attribs must lead with the recorded alignment, was: " + el.attribs);
    }

    // ---- LaTeXElement: style -> disp -----------------------------------

    @Test
    public void styleTextMapsToTeXTextStyle() {
        CopyConvert.LaTeXElement el = latexElem(false, "style", "text");
        assertEquals(TeXConstants.STYLE_TEXT, el.disp);
        assertEquals(" align='' size='18' style='text'", el.attribs);
    }

    @Test
    public void styleScriptMapsToTeXScriptStyle() {
        assertEquals(TeXConstants.STYLE_SCRIPT, latexElem(false, "style", "script").disp);
    }

    @Test
    public void styleScriptScriptMapsToTeXScriptScriptStyle() {
        assertEquals(TeXConstants.STYLE_SCRIPT_SCRIPT,
                     latexElem(false, "style", "script_script").disp);
    }

    @Test
    public void unknownStyleLeavesDisplayButIsStillEchoedInAttribs() {
        // The disp field keeps its default, yet the raw style token round-trips.
        CopyConvert.LaTeXElement el = latexElem(false, "style", "weird");
        assertEquals(TeXConstants.STYLE_DISPLAY, el.disp);
        assertEquals(" align='' size='18' style='weird'", el.attribs);
    }

    // ---- LaTeXElement: size --------------------------------------------

    @Test
    public void sizeAttributeIsParsed() {
        CopyConvert.LaTeXElement el = latexElem(false, "size", "24");
        assertEquals(24, el.size);
        assertEquals(" align='' size='24' style='display'", el.attribs);
    }

    @Test
    public void nonNumericSizeFallsBackToSixteen() {
        // Documents the catch-branch default (16, NOT the initial 18).
        assertEquals(16, latexElem(false, "size", "not-a-number").size);
    }

    // ---- LaTeXElement: colours -----------------------------------------

    @Test
    public void foregroundColourIsDecodedAndEchoed() {
        CopyConvert.LaTeXElement el = latexElem(false, "fg", "#FF0000");
        assertEquals(Color.decode("#FF0000"), el.fg);
        assertNull(el.bg);
        assertEquals(" align='' size='18' style='display' fg='#FF0000'", el.attribs);
    }

    @Test
    public void backgroundColourIsDecodedAndEchoed() {
        CopyConvert.LaTeXElement el = latexElem(false, "bg", "#00FF00");
        assertEquals(Color.decode("#00FF00"), el.bg);
        assertNull(el.fg);
        assertEquals(" align='' size='18' style='display' bg='#00FF00'", el.attribs);
    }

    @Test
    public void bothColoursEmitBackgroundBeforeForeground() {
        CopyConvert.LaTeXElement el = latexElem(false, "fg", "#010203", "bg", "#040506");
        // Source order in the builder is bg then fg, regardless of attribute order.
        assertEquals(" align='' size='18' style='display' bg='#040506' fg='#010203'", el.attribs);
    }

    @Test
    public void everyAttributeTogetherProducesTheFullEcho() {
        CopyConvert.LaTeXElement el =
            latexElem(true, "align", "left", "style", "text", "size", "20",
                      "fg", "#000000", "bg", "#ffffff");
        assertEquals("left", el.align);
        assertEquals(20, el.size);
        assertEquals(TeXConstants.STYLE_TEXT, el.disp);
        assertEquals(" align='left' size='20' style='text' bg='#ffffff' fg='#000000'", el.attribs);
    }

    @Test
    public void malformedColourThrowsFromTheConstructor() {
        // Defect-characterization: fg/bg decoding is unguarded, so a bad colour
        // aborts construction with NumberFormatException rather than being ignored.
        assertThrows(NumberFormatException.class, () -> latexElem(false, "bg", "notacolour"));
    }

    // ---- LaTeXElement: setLaTeX accumulates ----------------------------

    @Test
    public void setLaTeXAppendsSuccessiveChunks() {
        CopyConvert.LaTeXElement el = latexElem(false);
        assertEquals("", el.LaTeX);
        el.setLaTeX("x^2");
        el.setLaTeX(" + 1");
        assertEquals("x^2 + 1", el.LaTeX);
    }

    // ---- main: argument handling ---------------------------------------

    @Test
    public void mainWithNoArgumentsReportsUsage() {
        assertEquals(1, CopyConvert.main(new String[0]));
    }

    @Test
    public void mainWithUnknownFlagReportsUsage() {
        assertEquals(1, CopyConvert.main(new String[] {"-h"}));
    }

    @Test
    public void mainWithWrongArgumentCountReportsUsage() {
        assertEquals(1, CopyConvert.main(new String[] {"only-input"}));
        assertEquals(1, CopyConvert.main(new String[] {"a", "b", "c"}));
    }

    @Test
    public void mainReturnsFailureCodeWhenInputCannotBeParsed(@TempDir Path dir) {
        File missingIn = new File(dir.toFile(), "does-not-exist.xml");
        File out = new File(dir.toFile(), "out.xml");
        // No usage error (two positional args) => it attempts the conversion,
        // which fails to read the input and returns the run() failure code 2.
        assertEquals(2, CopyConvert.main(new String[] {
            missingIn.getAbsolutePath(), out.getAbsolutePath()
        }));
    }
}
