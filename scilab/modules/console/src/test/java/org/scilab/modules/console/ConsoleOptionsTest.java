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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import javax.swing.UIManager;
import java.awt.Font;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link ConsoleOptions}: the XPath location constants
 * and the pure field-mapping logic of the nested value classes.
 *
 * <p>The static {@code getXxx()} accessors and {@code invalidate()} touch the
 * live {@code XConfiguration} document, so they are out of scope. The nested
 * value classes have private constructors and private {@code set(...)} methods,
 * exercised here by reflection (the only hermetic path — the production code
 * reaches them via {@code XConfiguration.get}, which needs a running Scilab).
 */
public class ConsoleOptionsTest {

    // --- XPath location constants ------------------------------------------

    @Test
    public void fontAndLatexPathAreLiterallyTheSameXPath() {
        // This coincidence is load-bearing (see ConsoleConfiguration.Conf).
        assertEquals(ConsoleOptions.FONTPATH, ConsoleOptions.LATEXPATH);
        assertEquals("//fonts/body/fonts", ConsoleOptions.FONTPATH);
    }

    @Test
    public void consoleFontPathIsAMoreSpecificChildOfTheFontsPath() {
        assertTrue(ConsoleOptions.CONSOLEFONTPATH.startsWith(ConsoleOptions.FONTPATH));
        assertNotEquals(ConsoleOptions.FONTPATH, ConsoleOptions.CONSOLEFONTPATH);
    }

    @Test
    public void everyPathConstantIsAnAbsoluteXPath() {
        for (String p : new String[] {
                    ConsoleOptions.COLORSPATH, ConsoleOptions.CONSOLEFONTPATH, ConsoleOptions.FONTPATH,
                    ConsoleOptions.LATEXPATH, ConsoleOptions.DISPLAYPATH, ConsoleOptions.KEYMAPPATH
                }) {
            assertTrue(p.startsWith("//"), "expected an absolute XPath but got: " + p);
        }
    }

    @Test
    public void theSemanticallyDistinctPathsAreAllDifferent() {
        // FONTPATH and LATEXPATH intentionally collapse; the remaining five are distinct.
        Set<String> distinct = new HashSet<String>();
        distinct.add(ConsoleOptions.COLORSPATH);
        distinct.add(ConsoleOptions.CONSOLEFONTPATH);
        distinct.add(ConsoleOptions.FONTPATH);
        distinct.add(ConsoleOptions.DISPLAYPATH);
        distinct.add(ConsoleOptions.KEYMAPPATH);
        assertEquals(5, distinct.size());
    }

    // --- ConsoleColor -------------------------------------------------------

    @Test
    public void consoleColorKeepsExplicitColorsWhenNotUsingSystemColors() throws Exception {
        ConsoleOptions.ConsoleColor cc = instantiate(ConsoleOptions.ConsoleColor.class);
        invokeSet(cc, new Class[] {Color.class, Color.class, Color.class, boolean.class},
                  Color.RED, Color.GREEN, Color.BLUE, false);
        assertSame(Color.RED, cc.background);
        assertSame(Color.GREEN, cc.cursor);
        assertSame(Color.BLUE, cc.foreground);
    }

    @Test
    public void consoleColorTakesTheLookAndFeelColorsWhenUsingSystemColors() throws Exception {
        // This method used to hardcode white-on-black under "use system color", which is
        // the opposite of what the flag says and left the console a white rectangle inside
        // a dark look and feel. It now resolves the colours from the look and feel, so the
        // expectation here is whatever UIManager currently reports -- NOT a fixed literal,
        // which is what made the old assertion wrong in the first place.
        Color expectedBg = uiColor("TextPane.background", Color.WHITE);
        Color expectedFg = uiColor("TextPane.foreground", Color.BLACK);
        Color expectedCaret = uiColor("TextPane.caretForeground", expectedFg);

        ConsoleOptions.ConsoleColor cc = instantiate(ConsoleOptions.ConsoleColor.class);
        // Explicit colours are supplied but must be ignored in favour of the L&F ones.
        invokeSet(cc, new Class[] {Color.class, Color.class, Color.class, boolean.class},
                  Color.RED, Color.GREEN, Color.BLUE, true);

        assertEquals(expectedBg, cc.background);
        assertEquals(expectedFg, cc.foreground);
        assertEquals(expectedCaret, cc.cursor);

        // and the supplied colours really were ignored
        assertNotSame(Color.RED, cc.background);
        assertNotSame(Color.GREEN, cc.cursor);
        assertNotSame(Color.BLUE, cc.foreground);
    }

    /**
     * A dark look and feel must actually produce a dark console: the whole point of the
     * change. Uses a synthetic dark palette rather than depending on FlatLaf, which is not
     * on this module's test classpath.
     */
    @Test
    public void consoleColorFollowsADarkLookAndFeel() throws Exception {
        Object savedBg = UIManager.get("TextPane.background");
        Object savedFg = UIManager.get("TextPane.foreground");
        Object savedCaret = UIManager.get("TextPane.caretForeground");
        try {
            Color darkBg = new Color(0x28, 0x28, 0x28);
            Color darkFg = new Color(0xDD, 0xDD, 0xDD);
            UIManager.put("TextPane.background", darkBg);
            UIManager.put("TextPane.foreground", darkFg);
            UIManager.put("TextPane.caretForeground", darkFg);

            ConsoleOptions.ConsoleColor cc = instantiate(ConsoleOptions.ConsoleColor.class);
            invokeSet(cc, new Class[] {Color.class, Color.class, Color.class, boolean.class},
                      Color.RED, Color.GREEN, Color.BLUE, true);

            assertEquals(darkBg, cc.background);
            assertEquals(darkFg, cc.foreground);
            assertEquals(darkFg, cc.cursor);
        } finally {
            UIManager.put("TextPane.background", savedBg);
            UIManager.put("TextPane.foreground", savedFg);
            UIManager.put("TextPane.caretForeground", savedCaret);
        }
    }

    /**
     * The historical values must survive a look and feel that defines none of these keys,
     * so nothing regresses on an exotic L&F.
     */
    @Test
    public void consoleColorFallsBackToBlackOnWhiteWhenTheLookAndFeelDefinesNothing() throws Exception {
        Object savedBg = UIManager.get("TextPane.background");
        Object savedFg = UIManager.get("TextPane.foreground");
        Object savedCaret = UIManager.get("TextPane.caretForeground");
        try {
            // NOT put(key, null): that only clears the USER defaults, and UIManager.get()
            // then falls through to the look and feel's own table, so the key still
            // resolves. Storing a non-Color value is what actually drives the
            // "absent or unusable" branch the fallback exists for.
            UIManager.put("TextPane.background", "not-a-color");
            UIManager.put("TextPane.foreground", "not-a-color");
            UIManager.put("TextPane.caretForeground", "not-a-color");

            ConsoleOptions.ConsoleColor cc = instantiate(ConsoleOptions.ConsoleColor.class);
            invokeSet(cc, new Class[] {Color.class, Color.class, Color.class, boolean.class},
                      Color.RED, Color.GREEN, Color.BLUE, true);

            assertEquals(Color.WHITE, cc.background);
            assertEquals(Color.BLACK, cc.foreground);
            assertEquals(Color.BLACK, cc.cursor);
        } finally {
            UIManager.put("TextPane.background", savedBg);
            UIManager.put("TextPane.foreground", savedFg);
            UIManager.put("TextPane.caretForeground", savedCaret);
        }
    }

    private static Color uiColor(String key, Color fallback) {
        Object c = UIManager.get(key);
        return (c instanceof Color) ? (Color) c : fallback;
    }

    // --- LaTeXFont ----------------------------------------------------------

    @Test
    public void latexFontTruncatesTheDoubleSizeTowardsZero() throws Exception {
        ConsoleOptions.LaTeXFont f = instantiate(ConsoleOptions.LaTeXFont.class);
        invokeSet(f, new Class[] {double.class}, 12.9);
        assertEquals(12, f.size);

        ConsoleOptions.LaTeXFont g = instantiate(ConsoleOptions.LaTeXFont.class);
        invokeSet(g, new Class[] {double.class}, -3.7);
        assertEquals(-3, g.size);
    }

    // --- ConsoleFont --------------------------------------------------------

    @Test
    public void consoleFontDerivesBoldFromTheFontFace() throws Exception {
        ConsoleOptions.ConsoleFont f = instantiate(ConsoleOptions.ConsoleFont.class);
        invokeSet(f, new Class[] {String.class, String.class, int.class, boolean.class},
                  "bold", "Dialog", 14, false);
        assertEquals(14, f.font.getSize());
        assertTrue(f.font.isBold());
        assertFalse(f.font.isItalic());
    }

    @Test
    public void consoleFontDerivesItalicFromTheFontFace() throws Exception {
        ConsoleOptions.ConsoleFont f = instantiate(ConsoleOptions.ConsoleFont.class);
        invokeSet(f, new Class[] {String.class, String.class, int.class, boolean.class},
                  "italic", "Serif", 10, false);
        assertEquals(10, f.font.getSize());
        assertTrue(f.font.isItalic());
        assertFalse(f.font.isBold());
    }

    @Test
    public void consoleFontStaysPlainForANonBoldNonItalicFace() throws Exception {
        ConsoleOptions.ConsoleFont f = instantiate(ConsoleOptions.ConsoleFont.class);
        invokeSet(f, new Class[] {String.class, String.class, int.class, boolean.class},
                  "regular", "Monospaced", 12, false);
        assertEquals(Font.PLAIN, f.font.getStyle());
        assertEquals(12, f.font.getSize());
    }

    @Test
    public void consoleFontBoldItalicFaceOnlyPicksUpBold() throws Exception {
        // Characterization: the else-if means "bold" wins and italic is dropped,
        // so a "bold italic" face can never yield a bold+italic font here.
        ConsoleOptions.ConsoleFont f = instantiate(ConsoleOptions.ConsoleFont.class);
        invokeSet(f, new Class[] {String.class, String.class, int.class, boolean.class},
                  "bold italic", "Dialog", 16, false);
        assertTrue(f.font.isBold());
        assertFalse(f.font.isItalic());
    }

    // --- ConsoleDisplay -----------------------------------------------------

    @Test
    public void consoleDisplayStoresEveryFieldVerbatim() throws Exception {
        ConsoleOptions.ConsoleDisplay d = instantiate(ConsoleOptions.ConsoleDisplay.class);
        invokeSet(d, new Class[] {int.class, int.class, int.class, boolean.class, boolean.class},
                  1000, 25, 80, true, false);
        assertEquals(1000, d.maxOutputLines);
        assertEquals(25, d.nbLines);
        assertEquals(80, d.nbColumns);
        assertTrue(d.adaptToDisplay);
        assertFalse(d.wrapLines);
    }

    // --- reflection helpers -------------------------------------------------

    private static <T> T instantiate(Class<T> cls) throws Exception {
        Constructor<T> c = cls.getDeclaredConstructor();
        c.setAccessible(true);
        return c.newInstance();
    }

    private static void invokeSet(Object target, Class<?>[] paramTypes, Object... args) throws Exception {
        Method m = target.getClass().getDeclaredMethod("set", paramTypes);
        m.setAccessible(true);
        m.invoke(target, args);
    }
}
