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

package org.scilab.modules.console;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import org.scilab.modules.console.ConsoleConfiguration.Conf;

/**
 * Hermetic unit tests for {@link ConsoleConfiguration.Conf}, the pure decision
 * object that turns a set of modified XConfiguration paths into a set of "which
 * console facets changed" booleans. No {@code SciConsole} is needed: only the
 * nested {@code Conf} value class is exercised.
 */
public class ConsoleConfigurationTest {

    private static Set<String> paths(String... p) {
        return new HashSet<String>(java.util.Arrays.asList(p));
    }

    @Test
    public void allKeywordTurnsEveryFacetOn() {
        Conf conf = new Conf(paths("ALL"));
        assertTrue(conf.font);
        assertTrue(conf.color);
        assertTrue(conf.display);
        assertTrue(conf.keymap);
        assertTrue(conf.latex);
        assertTrue(conf.changed());
    }

    @Test
    public void emptyPathSetLeavesEveryFacetOffAndUnchanged() {
        Conf conf = new Conf(Collections.<String>emptySet());
        assertFalse(conf.font);
        assertFalse(conf.color);
        assertFalse(conf.display);
        assertFalse(conf.keymap);
        assertFalse(conf.latex);
        assertFalse(conf.changed());
    }

    @Test
    public void displayPathTogglesOnlyDisplay() {
        Conf conf = new Conf(paths(ConsoleOptions.DISPLAYPATH));
        assertTrue(conf.display);
        assertFalse(conf.font);
        assertFalse(conf.color);
        assertFalse(conf.keymap);
        assertFalse(conf.latex);
        assertTrue(conf.changed());
    }

    @Test
    public void colorPathTogglesOnlyColor() {
        Conf conf = new Conf(paths(ConsoleOptions.COLORSPATH));
        assertTrue(conf.color);
        assertFalse(conf.font);
        assertFalse(conf.display);
        assertFalse(conf.keymap);
        assertFalse(conf.latex);
        assertTrue(conf.changed());
    }

    @Test
    public void keymapPathTogglesOnlyKeymap() {
        Conf conf = new Conf(paths(ConsoleOptions.KEYMAPPATH));
        assertTrue(conf.keymap);
        assertFalse(conf.font);
        assertFalse(conf.color);
        assertFalse(conf.display);
        assertFalse(conf.latex);
        assertTrue(conf.changed());
    }

    @Test
    public void consoleFontPathTogglesFontButNotLatex() {
        // CONSOLEFONTPATH is distinct from LATEXPATH, so latex stays off.
        Conf conf = new Conf(paths(ConsoleOptions.CONSOLEFONTPATH));
        assertTrue(conf.font);
        assertFalse(conf.latex);
        assertTrue(conf.changed());
    }

    @Test
    public void fontPathTogglesBothFontAndLatexBecauseTheyShareOneXPath() {
        // Characterization: FONTPATH and LATEXPATH are the very same string
        // "//fonts/body/fonts", so a change to it flips BOTH font and latex.
        Conf conf = new Conf(paths(ConsoleOptions.FONTPATH));
        assertTrue(conf.font);
        assertTrue(conf.latex);
        assertFalse(conf.color);
        assertFalse(conf.display);
        assertFalse(conf.keymap);
        assertTrue(conf.changed());
    }

    @Test
    public void latexPathAlsoTogglesFontBecauseTheyShareOneXPath() {
        Conf conf = new Conf(paths(ConsoleOptions.LATEXPATH));
        assertTrue(conf.latex);
        assertTrue(conf.font);
    }

    @Test
    public void allKeywordWinsEvenWhenMixedWithOtherPaths() {
        Conf conf = new Conf(paths("ALL", ConsoleOptions.COLORSPATH));
        assertTrue(conf.font);
        assertTrue(conf.color);
        assertTrue(conf.display);
        assertTrue(conf.keymap);
        assertTrue(conf.latex);
    }

    @Test
    public void unrelatedPathLeavesEverythingUnchanged() {
        Conf conf = new Conf(paths("//some/unrelated/path"));
        assertFalse(conf.changed());
    }

    @Test
    public void multipleDistinctPathsToggleEachCorrespondingFacet() {
        Conf conf = new Conf(paths(ConsoleOptions.COLORSPATH, ConsoleOptions.DISPLAYPATH, ConsoleOptions.KEYMAPPATH));
        assertTrue(conf.color);
        assertTrue(conf.display);
        assertTrue(conf.keymap);
        assertFalse(conf.font);
        assertFalse(conf.latex);
        assertTrue(conf.changed());
    }
}
