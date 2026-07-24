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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hermetic unit tests for {@link SciNotesConfiguration.Conf}, the pure value object
 * that classifies a set of changed preference XML-paths into the individual
 * configuration sections that were touched. The path constants it compares against
 * are compile-time {@code String} literals, so exercising this logic needs no live
 * configuration document, GUI or engine.
 */
public class SciNotesConfigurationTest {

    private static Set<String> paths(String... p) {
        Set<String> s = new HashSet<String>();
        Collections.addAll(s, p);
        return s;
    }

    @Test
    public void emptySetTouchesNothing() {
        SciNotesConfiguration.Conf conf = new SciNotesConfiguration.Conf(paths());
        assertFalse(conf.preferences);
        assertFalse(conf.display);
        assertFalse(conf.autosave);
        assertFalse(conf.colors);
        assertFalse(conf.systemfont);
        assertFalse(conf.font);
        assertFalse(conf.keymap);
        assertFalse(conf.header);
        assertFalse(conf.changed(), "nothing changed => changed() is false");
    }

    @Test
    public void allKeywordTouchesEverything() {
        SciNotesConfiguration.Conf conf = new SciNotesConfiguration.Conf(paths("ALL"));
        assertTrue(conf.preferences);
        assertTrue(conf.display);
        assertTrue(conf.autosave);
        assertTrue(conf.colors);
        assertTrue(conf.systemfont);
        assertTrue(conf.font);
        assertTrue(conf.keymap);
        assertTrue(conf.header);
        assertTrue(conf.changed());
    }

    @Test
    public void preferencesPathTouchesOnlyPreferences() {
        SciNotesConfiguration.Conf conf =
            new SciNotesConfiguration.Conf(paths(SciNotesOptions.PREFERENCESPATH));
        assertTrue(conf.preferences);
        assertTrue(conf.changed());
        assertFalse(conf.display);
        assertFalse(conf.autosave);
        assertFalse(conf.header);
    }

    @Test
    public void displayPathTouchesOnlyDisplay() {
        SciNotesConfiguration.Conf conf =
            new SciNotesConfiguration.Conf(paths(SciNotesOptions.DISPLAYPATH));
        assertTrue(conf.display);
        assertFalse(conf.preferences);
        assertTrue(conf.changed());
    }

    @Test
    public void autosavePathTouchesOnlyAutosave() {
        SciNotesConfiguration.Conf conf =
            new SciNotesConfiguration.Conf(paths(SciNotesOptions.AUTOSAVEPATH));
        assertTrue(conf.autosave);
        assertFalse(conf.display);
        assertTrue(conf.changed());
    }

    @Test
    public void keymapPathTouchesOnlyKeymap() {
        SciNotesConfiguration.Conf conf =
            new SciNotesConfiguration.Conf(paths(SciNotesOptions.KEYMAPPATH));
        assertTrue(conf.keymap);
        assertFalse(conf.preferences);
        assertTrue(conf.changed());
    }

    @Test
    public void headerPathTouchesOnlyHeader() {
        SciNotesConfiguration.Conf conf =
            new SciNotesConfiguration.Conf(paths(SciNotesOptions.HEADERPATH));
        assertTrue(conf.header);
        assertFalse(conf.display);
        assertTrue(conf.changed());
    }

    @Test
    public void colorsPathTouchesOnlyColors() {
        SciNotesConfiguration.Conf conf =
            new SciNotesConfiguration.Conf(paths(ScilabContext.COLORSPATH));
        assertTrue(conf.colors);
        assertFalse(conf.systemfont);
        assertFalse(conf.font);
        assertTrue(conf.changed());
    }

    @Test
    public void systemFontPathDoesNotImplyTheScinotesFont() {
        // SYSTEMFONTPATH and XCONFFONTPATH are distinct XPaths: the system-font change
        // must not switch on the scinotes-specific font flag.
        SciNotesConfiguration.Conf conf =
            new SciNotesConfiguration.Conf(paths(ScilabContext.SYSTEMFONTPATH));
        assertTrue(conf.systemfont);
        assertFalse(conf.font);
        assertFalse(conf.colors);
        assertTrue(conf.changed());
    }

    @Test
    public void scinotesFontPathDoesNotImplyTheSystemFont() {
        SciNotesConfiguration.Conf conf =
            new SciNotesConfiguration.Conf(paths(ScilabContext.XCONFFONTPATH));
        assertTrue(conf.font);
        assertFalse(conf.systemfont);
        assertTrue(conf.changed());
    }

    @Test
    public void unrelatedPathTouchesNothing() {
        SciNotesConfiguration.Conf conf =
            new SciNotesConfiguration.Conf(paths("//something/entirely/unrelated"));
        assertFalse(conf.changed());
    }

    @Test
    public void multiplePathsAccumulate() {
        SciNotesConfiguration.Conf conf = new SciNotesConfiguration.Conf(
            paths(SciNotesOptions.PREFERENCESPATH, ScilabContext.COLORSPATH, SciNotesOptions.HEADERPATH));
        assertTrue(conf.preferences);
        assertTrue(conf.colors);
        assertTrue(conf.header);
        assertFalse(conf.display);
        assertFalse(conf.autosave);
        assertTrue(conf.changed());
    }

    @Test
    public void outerListenerCanBeConstructed() {
        SciNotesConfiguration listener = new SciNotesConfiguration();
        assertTrue(listener instanceof org.scilab.modules.commons.xml.XConfigurationListener,
                   "SciNotesConfiguration is registered as an XConfiguration listener");
    }
}
