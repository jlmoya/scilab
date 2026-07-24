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

package org.scilab.modules.gui.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;

import javax.swing.SwingConstants;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link ScilabAlignment}, the pure string-to-Swing
 * alignment mapping used by Scilab uicontrols. Every method is {@code static}
 * and only reads {@link SwingConstants} integer constants, so the tests run
 * with neither a display nor the native runtime.
 */
class ScilabAlignmentTest {

    // ---- Keyword contract ------------------------------------------------

    /**
     * The keyword strings are the contract with Scilab scripts (they are the
     * literal values a user writes for the alignment property), so pin them.
     */
    @Test
    void keywordConstantsHaveTheScilabContractValues() {
        assertEquals("left", ScilabAlignment.LEFT);
        assertEquals("center", ScilabAlignment.CENTER);
        assertEquals("right", ScilabAlignment.RIGHT);
        assertEquals("top", ScilabAlignment.TOP);
        assertEquals("middle", ScilabAlignment.MIDDLE);
        assertEquals("bottom", ScilabAlignment.BOTTOM);
    }

    // ---- Recognised alignments ------------------------------------------

    @Test
    void leftMapsToSwingLeft() {
        assertEquals(SwingConstants.LEFT, ScilabAlignment.toSwingAlignment(ScilabAlignment.LEFT));
    }

    @Test
    void rightMapsToSwingRight() {
        assertEquals(SwingConstants.RIGHT, ScilabAlignment.toSwingAlignment(ScilabAlignment.RIGHT));
    }

    @Test
    void topMapsToSwingTop() {
        assertEquals(SwingConstants.TOP, ScilabAlignment.toSwingAlignment(ScilabAlignment.TOP));
    }

    @Test
    void bottomMapsToSwingBottom() {
        assertEquals(SwingConstants.BOTTOM, ScilabAlignment.toSwingAlignment(ScilabAlignment.BOTTOM));
    }

    @Test
    void centerMapsToSwingCenter() {
        assertEquals(SwingConstants.CENTER, ScilabAlignment.toSwingAlignment(ScilabAlignment.CENTER));
    }

    // ---- Characterization of the fall-through default -------------------

    /**
     * Characterization: MIDDLE is a declared keyword but {@code toSwingAlignment}
     * has no explicit branch for it, so it falls through to the CENTER default.
     * That value is still visually correct because {@link SwingConstants#CENTER}
     * doubles as the vertical-center constant.
     */
    @Test
    void middleFallsThroughToSwingCenter() {
        assertEquals(SwingConstants.CENTER, ScilabAlignment.toSwingAlignment(ScilabAlignment.MIDDLE));
    }

    /**
     * Characterization: any unrecognised string yields the CENTER default rather
     * than throwing.
     */
    @Test
    void unknownAlignmentDefaultsToSwingCenter() {
        assertEquals(SwingConstants.CENTER, ScilabAlignment.toSwingAlignment("not-an-alignment"));
        assertEquals(SwingConstants.CENTER, ScilabAlignment.toSwingAlignment(""));
    }

    /**
     * Characterization: matching is case-sensitive (String.equals), so an
     * upper/mixed-case spelling is treated as unknown and defaults to CENTER.
     */
    @Test
    void matchingIsCaseSensitive() {
        assertEquals(SwingConstants.CENTER, ScilabAlignment.toSwingAlignment("LEFT"));
        assertEquals(SwingConstants.CENTER, ScilabAlignment.toSwingAlignment("Left"));
    }

    /**
     * Characterization: a null argument is dereferenced by {@code alignment.equals(...)}
     * and throws, rather than returning the CENTER default.
     */
    @Test
    void nullAlignmentThrowsNpe() {
        assertThrows(NullPointerException.class, () -> ScilabAlignment.toSwingAlignment(null));
    }

    // ---- Utility class contract -----------------------------------------

    /**
     * {@code ScilabAlignment} is a static utility class: its sole constructor is
     * private and rejects reflective instantiation with
     * {@link UnsupportedOperationException}.
     */
    @Test
    void constructorIsPrivateAndThrows() throws Exception {
        Constructor<ScilabAlignment> ctor = ScilabAlignment.class.getDeclaredConstructor();
        assertTrue(Modifier.isPrivate(ctor.getModifiers()));
        ctor.setAccessible(true);
        InvocationTargetException ex = assertThrows(InvocationTargetException.class, ctor::newInstance);
        assertTrue(ex.getCause() instanceof UnsupportedOperationException);
    }
}
