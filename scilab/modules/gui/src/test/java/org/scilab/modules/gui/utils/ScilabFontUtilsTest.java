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

package org.scilab.modules.gui.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.HashSet;
import java.util.Set;

/**
 * Hermetic unit tests for {@link ScilabFontUtils}.
 *
 * <p>The class classifies installed font families as monospaced / non-monospaced
 * (and, separately, "all styles share the same per-glyph widths") purely from
 * {@link java.awt.FontMetrics} advances measured against an off-screen
 * {@link java.awt.image.BufferedImage} graphics context created in its static
 * initialiser. No display is realised, so these tests run identically on a
 * headless JVM and need no Scilab native runtime.</p>
 *
 * <p>Because the exact set of installed fonts and their metrics is
 * environment-dependent, most assertions target <em>structural invariants</em>
 * (the partition is total and disjoint; the collection methods agree with the
 * predicate methods; results are deterministic) rather than specific font
 * pixel widths. The two value-level assertions rely only on Java's guaranteed
 * <em>logical</em> fonts: {@code Monospaced} is monospaced and {@code Serif} is
 * proportional.</p>
 */
public class ScilabFontUtilsTest {

    /** The class's own default, restored around every test since the field is static. */
    private static final int DEFAULT_SIZE = 14;

    private static String[] availableFamilies() {
        return GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
    }

    @BeforeEach
    public void resetDefaultFontSize() {
        ScilabFontUtils.setDefaultFontSize(DEFAULT_SIZE);
    }

    @AfterEach
    public void restoreDefaultFontSize() {
        ScilabFontUtils.setDefaultFontSize(DEFAULT_SIZE);
    }

    // ------------------------------------------------------------------
    // getMonospacedFontsFamillyName - structure & partition invariants
    // ------------------------------------------------------------------

    @Test
    public void monospacedResultIsTwoNonNullBuckets() {
        String[][] r = ScilabFontUtils.getMonospacedFontsFamillyName();
        assertNotNull(r);
        assertEquals(2, r.length);
        assertNotNull(r[0], "monospaced bucket must not be null");
        assertNotNull(r[1], "non-monospaced bucket must not be null");
    }

    @Test
    public void monospacedPartitionCoversEveryFamilyExactlyOnce() {
        String[] all = availableFamilies();
        String[][] r = ScilabFontUtils.getMonospacedFontsFamillyName();

        // Total count is preserved: nothing lost, nothing duplicated.
        assertEquals(all.length, r[0].length + r[1].length);

        Set<String> mono = new HashSet<>();
        for (String s : r[0]) {
            mono.add(s);
        }
        Set<String> notMono = new HashSet<>();
        for (String s : r[1]) {
            notMono.add(s);
        }

        // Disjoint: no family classified as both.
        for (String s : notMono) {
            assertFalse(mono.contains(s), "family appears in both buckets: " + s);
        }
        // Complete: every available family lands in one of the two buckets.
        for (String name : all) {
            assertTrue(mono.contains(name) || notMono.contains(name),
                       "family missing from the partition: " + name);
        }
    }

    @Test
    public void monospacedBucketsAgreeWithTheIsMonospacedPredicate() {
        String[][] r = ScilabFontUtils.getMonospacedFontsFamillyName();
        for (String name : r[0]) {
            assertTrue(ScilabFontUtils.isMonospaced(name),
                       "classified monospaced but predicate disagrees: " + name);
        }
        for (String name : r[1]) {
            assertFalse(ScilabFontUtils.isMonospaced(name),
                        "classified non-monospaced but predicate disagrees: " + name);
        }
    }

    @Test
    public void monospacedClassificationIsDeterministic() {
        String[][] a = ScilabFontUtils.getMonospacedFontsFamillyName();
        String[][] b = ScilabFontUtils.getMonospacedFontsFamillyName();
        assertArrayEquals(a[0], b[0]);
        assertArrayEquals(a[1], b[1]);
    }

    // ------------------------------------------------------------------
    // getAllStylesSameWidthsFontsFamillyName - same invariants
    // ------------------------------------------------------------------

    @Test
    public void allStylesResultIsTwoNonNullBucketsCoveringEveryFamily() {
        String[] all = availableFamilies();
        String[][] r = ScilabFontUtils.getAllStylesSameWidthsFontsFamillyName();
        assertNotNull(r);
        assertEquals(2, r.length);
        assertNotNull(r[0]);
        assertNotNull(r[1]);
        assertEquals(all.length, r[0].length + r[1].length);
    }

    @Test
    public void allStylesBucketsAgreeWithTheIsAllStylesSameWidthsPredicate() {
        String[][] r = ScilabFontUtils.getAllStylesSameWidthsFontsFamillyName();
        for (String name : r[0]) {
            assertTrue(ScilabFontUtils.isAllStylesSameWidths(name),
                       "classified same-widths but predicate disagrees: " + name);
        }
        for (String name : r[1]) {
            assertFalse(ScilabFontUtils.isAllStylesSameWidths(name),
                        "classified not-same-widths but predicate disagrees: " + name);
        }
    }

    // ------------------------------------------------------------------
    // Value-level behaviour on Java's guaranteed logical fonts
    // ------------------------------------------------------------------

    @Test
    public void logicalMonospacedFontIsReportedMonospaced() {
        // The "Monospaced" logical family maps to a physical monospaced face on
        // every standard JVM; every printable-ASCII advance is therefore equal.
        assertTrue(ScilabFontUtils.isMonospaced(new Font(Font.MONOSPACED, Font.PLAIN, DEFAULT_SIZE)));
    }

    @Test
    public void logicalSerifFontIsReportedProportional() {
        // "Serif" is a proportional family, so at least one advance differs from 'a'.
        assertFalse(ScilabFontUtils.isMonospaced(new Font(Font.SERIF, Font.PLAIN, DEFAULT_SIZE)));
    }

    // ------------------------------------------------------------------
    // String overloads delegate to the Font overloads at defaultFontSize
    // ------------------------------------------------------------------

    @Test
    public void stringAndFontMonospacedOverloadsAgreeAtDefaultSize() {
        for (String name : logicalFamilies()) {
            boolean viaFont = ScilabFontUtils.isMonospaced(new Font(name, Font.PLAIN, DEFAULT_SIZE));
            boolean viaName = ScilabFontUtils.isMonospaced(name);
            assertEquals(viaFont, viaName, "overloads disagree for: " + name);
        }
    }

    @Test
    public void stringAndFontAllStylesOverloadsAgreeAtDefaultSize() {
        for (String name : logicalFamilies()) {
            boolean viaFont = ScilabFontUtils.isAllStylesSameWidths(new Font(name, Font.PLAIN, DEFAULT_SIZE));
            boolean viaName = ScilabFontUtils.isAllStylesSameWidths(name);
            assertEquals(viaFont, viaName, "overloads disagree for: " + name);
        }
    }

    @Test
    public void setDefaultFontSizeFeedsTheStringMonospacedOverload() {
        // After changing the default size, the String overload must build the
        // font at that size: it stays consistent with an explicit Font of the
        // same size. (Were the setter a no-op, a font whose monospaced-ness is
        // size-sensitive would break this agreement.)
        int probeSize = 30;
        ScilabFontUtils.setDefaultFontSize(probeSize);
        for (String name : logicalFamilies()) {
            boolean viaFont = ScilabFontUtils.isMonospaced(new Font(name, Font.PLAIN, probeSize));
            boolean viaName = ScilabFontUtils.isMonospaced(name);
            assertEquals(viaFont, viaName, "size not threaded through for: " + name);
        }
    }

    // ------------------------------------------------------------------
    // Edge cases
    // ------------------------------------------------------------------

    @Test
    public void unknownFontNameIsSubstitutedRatherThanThrowing() {
        // java.awt.Font silently substitutes a default face for an unknown name,
        // so the predicate returns a stable value instead of throwing.
        String bogus = "This Font Really Does Not Exist 12345";
        boolean first = ScilabFontUtils.isMonospaced(bogus);
        boolean second = ScilabFontUtils.isMonospaced(bogus);
        assertEquals(first, second);
    }

    @Test
    public void isAllStylesSameWidthsIsDeterministicForAFont() {
        Font f = new Font(Font.MONOSPACED, Font.PLAIN, DEFAULT_SIZE);
        assertEquals(ScilabFontUtils.isAllStylesSameWidths(f),
                     ScilabFontUtils.isAllStylesSameWidths(f));
    }

    private static String[] logicalFamilies() {
        return new String[] {
            Font.MONOSPACED, Font.SERIF, Font.SANS_SERIF, Font.DIALOG, Font.DIALOG_INPUT
        };
    }
}
