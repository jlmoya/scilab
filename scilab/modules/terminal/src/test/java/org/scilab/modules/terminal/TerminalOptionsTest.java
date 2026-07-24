/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Jose Moya
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 *
 */

package org.scilab.modules.terminal;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hermetic unit tests for the two value POJOs nested in {@link TerminalOptions}:
 * {@code TerminalSettings} and {@code TerminalFont}.
 *
 * <p>The live {@code getSettings()}/{@code getFont()} readers pull in the whole
 * {@code XConfiguration} preferences/localization subsystem (SCIHOME, config files),
 * which is not hermetic - so they are not exercised here. The POJOs themselves are
 * pure: {@code TerminalSettings} is a plain field holder with documented fallback
 * defaults, and {@code TerminalFont} carries a small real algorithm that parses a
 * font-face string into a {@link java.awt.Font} style. Their no-arg constructor and
 * {@code set(...)} binder are private (only XConfiguration's reflection binder calls
 * them), so these tests reach them the same way - via reflection - which never
 * initialises {@code XConfiguration}. {@code java.awt.Font} construction and {@code
 * deriveFont} are headless-safe.
 */
public class TerminalOptionsTest {

    /* --------------------------------------------------------------- helpers */

    private static TerminalOptions.TerminalSettings newSettings() throws Exception {
        Constructor<TerminalOptions.TerminalSettings> ctor =
            TerminalOptions.TerminalSettings.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        return ctor.newInstance();
    }

    private static void bindSettings(TerminalOptions.TerminalSettings s,
                                     String shell, String startDir, int scrollback, boolean bell) throws Exception {
        Method set = TerminalOptions.TerminalSettings.class
                     .getDeclaredMethod("set", String.class, String.class, int.class, boolean.class);
        set.setAccessible(true);
        set.invoke(s, shell, startDir, scrollback, bell);
    }

    private static TerminalOptions.TerminalFont newFont() throws Exception {
        Constructor<TerminalOptions.TerminalFont> ctor =
            TerminalOptions.TerminalFont.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        return ctor.newInstance();
    }

    private static void bindFont(TerminalOptions.TerminalFont f,
                                 String fontFace, String fontName, int fontSize, boolean desktop) throws Exception {
        Method set = TerminalOptions.TerminalFont.class
                     .getDeclaredMethod("set", String.class, String.class, int.class, boolean.class);
        set.setAccessible(true);
        set.invoke(f, fontFace, fontName, fontSize, desktop);
    }

    /* ------------------------------------------------------- TerminalSettings */

    @Test
    public void settingsDefaultsMatchTheDocumentedFallbacks() throws Exception {
        TerminalOptions.TerminalSettings s = newSettings();
        assertEquals("", s.shell, "default shell is empty (falls back to $SHELL at start)");
        assertEquals("", s.startDir, "default start dir is empty (inherit cwd)");
        assertEquals(10000, s.scrollback, "default scrollback is 10000 lines");
        assertTrue(s.audibleBell, "the audible bell is on by default");
    }

    @Test
    public void settingsSetBindsEveryField() throws Exception {
        TerminalOptions.TerminalSettings s = newSettings();
        bindSettings(s, "/bin/zsh", "/work", 250, false);
        assertEquals("/bin/zsh", s.shell);
        assertEquals("/work", s.startDir);
        assertEquals(250, s.scrollback);
        assertFalse(s.audibleBell);
    }

    /* ----------------------------------------------------------- TerminalFont */

    @Test
    public void plainFontFaceProducesAPlainFont() throws Exception {
        TerminalOptions.TerminalFont f = newFont();
        bindFont(f, "plain", "Menlo", 14, false);
        assertNotNull(f.font);
        assertEquals("Menlo", f.font.getName(), "the requested logical name is preserved");
        assertEquals(14, f.font.getSize(), "the requested point size is preserved");
        assertTrue(f.font.isPlain());
        assertFalse(f.font.isBold());
        assertFalse(f.font.isItalic());
    }

    @Test
    public void boldFontFaceSetsBoldOnly() throws Exception {
        TerminalOptions.TerminalFont f = newFont();
        bindFont(f, "bold", "Monospaced", 12, true);
        assertTrue(f.font.isBold());
        assertFalse(f.font.isItalic());
        assertEquals(12, f.font.getSize(), "deriveFont(style) keeps the size");
    }

    @Test
    public void italicFontFaceSetsItalicOnly() throws Exception {
        TerminalOptions.TerminalFont f = newFont();
        bindFont(f, "italic", "Monospaced", 12, false);
        assertTrue(f.font.isItalic());
        assertFalse(f.font.isBold());
    }

    @Test
    public void boldItalicFontFaceSetsBothStyles() throws Exception {
        TerminalOptions.TerminalFont f = newFont();
        bindFont(f, "bold italic", "Monospaced", 13, false);
        assertTrue(f.font.isBold());
        assertTrue(f.font.isItalic());
    }

    @Test
    public void fontFaceMatchingIsSubstringBased() throws Exception {
        // The parser classifies with String.contains, so a compound face string
        // such as "bolditalic" (no separator) still turns on BOTH styles.
        TerminalOptions.TerminalFont f = newFont();
        bindFont(f, "bolditalic", "Monospaced", 13, false);
        assertTrue(f.font.isBold());
        assertTrue(f.font.isItalic());
    }

    @Test
    public void unrecognisedFontFaceStaysPlain() throws Exception {
        // Anything without "bold"/"italic" leaves the font PLAIN (deriveFont is skipped).
        TerminalOptions.TerminalFont f = newFont();
        bindFont(f, "regular", "Monospaced", 11, false);
        assertTrue(f.font.isPlain());
        assertEquals(11, f.font.getSize());
    }
}
