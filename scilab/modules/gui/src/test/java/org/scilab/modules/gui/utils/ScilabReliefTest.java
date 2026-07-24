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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;

import javax.swing.BorderFactory;
import javax.swing.border.BevelBorder;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.EtchedBorder;
import javax.swing.border.LineBorder;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link ScilabRelief}, the keyword-to-{@link Border}
 * lookup for Scilab widget reliefs. The borders are built by
 * {@link BorderFactory} at class-load time and never require a display peer, so
 * the tests run headless and without the native runtime.
 */
class ScilabReliefTest {

    /**
     * A distinctive border passed as the {@code defaultBorder} argument so tests
     * can prove whether or not it is the value returned.
     */
    private static final Border SENTINEL = BorderFactory.createLineBorder(Color.RED, 7);

    // ---- Keyword contract ------------------------------------------------

    /**
     * The keyword strings are the contract with Scilab scripts (the literal
     * values a user writes for the relief property), so pin them.
     */
    @Test
    void keywordConstantsHaveTheScilabContractValues() {
        assertEquals("flat", ScilabRelief.FLAT);
        assertEquals("groove", ScilabRelief.GROOVE);
        assertEquals("raised", ScilabRelief.RAISED);
        assertEquals("ridge", ScilabRelief.RIDGE);
        assertEquals("solid", ScilabRelief.SOLID);
        assertEquals("sunken", ScilabRelief.SUNKEN);
        assertEquals("default", ScilabRelief.DEFAULT);
    }

    // ---- The pre-built border instances ---------------------------------

    @Test
    void flatBorderIsAnEmptyBorder() {
        assertNotNull(ScilabRelief.FLAT_BORDER);
        assertTrue(ScilabRelief.FLAT_BORDER instanceof EmptyBorder);
    }

    @Test
    void grooveBorderIsALoweredEtchedBorder() {
        assertTrue(ScilabRelief.GROOVE_BORDER instanceof EtchedBorder);
        assertEquals(EtchedBorder.LOWERED, ((EtchedBorder) ScilabRelief.GROOVE_BORDER).getEtchType());
    }

    @Test
    void ridgeBorderIsARaisedEtchedBorder() {
        assertTrue(ScilabRelief.RIDGE_BORDER instanceof EtchedBorder);
        assertEquals(EtchedBorder.RAISED, ((EtchedBorder) ScilabRelief.RIDGE_BORDER).getEtchType());
    }

    @Test
    void raisedBorderIsARaisedBevelBorder() {
        assertTrue(ScilabRelief.RAISED_BORDER instanceof BevelBorder);
        assertEquals(BevelBorder.RAISED, ((BevelBorder) ScilabRelief.RAISED_BORDER).getBevelType());
    }

    @Test
    void sunkenBorderIsALoweredBevelBorder() {
        assertTrue(ScilabRelief.SUNKEN_BORDER instanceof BevelBorder);
        assertEquals(BevelBorder.LOWERED, ((BevelBorder) ScilabRelief.SUNKEN_BORDER).getBevelType());
    }

    @Test
    void solidBorderIsABlackOnePixelLineBorder() {
        assertTrue(ScilabRelief.SOLID_BORDER instanceof LineBorder);
        LineBorder line = (LineBorder) ScilabRelief.SOLID_BORDER;
        assertEquals(Color.BLACK, line.getLineColor());
        assertEquals(1, line.getThickness());
    }

    // ---- getBorderFromRelief: recognised reliefs ------------------------

    @Test
    void flatReliefReturnsTheFlatBorderInstance() {
        assertSame(ScilabRelief.FLAT_BORDER, ScilabRelief.getBorderFromRelief(ScilabRelief.FLAT, SENTINEL));
    }

    @Test
    void grooveReliefReturnsTheGrooveBorderInstance() {
        assertSame(ScilabRelief.GROOVE_BORDER, ScilabRelief.getBorderFromRelief(ScilabRelief.GROOVE, SENTINEL));
    }

    @Test
    void raisedReliefReturnsTheRaisedBorderInstance() {
        assertSame(ScilabRelief.RAISED_BORDER, ScilabRelief.getBorderFromRelief(ScilabRelief.RAISED, SENTINEL));
    }

    @Test
    void ridgeReliefReturnsTheRidgeBorderInstance() {
        assertSame(ScilabRelief.RIDGE_BORDER, ScilabRelief.getBorderFromRelief(ScilabRelief.RIDGE, SENTINEL));
    }

    @Test
    void solidReliefReturnsTheSolidBorderInstance() {
        assertSame(ScilabRelief.SOLID_BORDER, ScilabRelief.getBorderFromRelief(ScilabRelief.SOLID, SENTINEL));
    }

    // ---- getBorderFromRelief: the "default" keyword ---------------------

    /**
     * The {@code "default"} keyword returns whatever {@code defaultBorder} the
     * caller supplied (the look-and-feel border), not one of the pre-built
     * static borders.
     */
    @Test
    void defaultReliefReturnsTheSuppliedDefaultBorder() {
        assertSame(SENTINEL, ScilabRelief.getBorderFromRelief(ScilabRelief.DEFAULT, SENTINEL));
    }

    /**
     * Characterization: {@code "default"} passes the supplied border straight
     * through even when it is {@code null}.
     */
    @Test
    void defaultReliefWithNullDefaultBorderReturnsNull() {
        assertNull(ScilabRelief.getBorderFromRelief(ScilabRelief.DEFAULT, null));
    }

    // ---- getBorderFromRelief: the fall-through default ------------------

    /**
     * Characterization: there is no explicit branch for the {@code "sunken"}
     * keyword; it lands on the method's fall-through default, which happens to
     * be {@code SUNKEN_BORDER}, so the mapping is still correct.
     */
    @Test
    void sunkenReliefFallsThroughToTheSunkenBorder() {
        assertSame(ScilabRelief.SUNKEN_BORDER, ScilabRelief.getBorderFromRelief(ScilabRelief.SUNKEN, SENTINEL));
    }

    /**
     * Characterization: any unrecognised relief yields the SUNKEN_BORDER default
     * rather than throwing or returning the supplied default border.
     */
    @Test
    void unknownReliefReturnsTheSunkenBorderDefault() {
        Border result = ScilabRelief.getBorderFromRelief("no-such-relief", SENTINEL);
        assertSame(ScilabRelief.SUNKEN_BORDER, result);
        assertNotSame(SENTINEL, result);
    }

    /**
     * The supplied {@code defaultBorder} must be ignored for every relief except
     * {@code "default"} — proven here for a recognised ("flat") and an
     * unrecognised relief.
     */
    @Test
    void suppliedDefaultBorderIsIgnoredForNonDefaultReliefs() {
        assertNotSame(SENTINEL, ScilabRelief.getBorderFromRelief(ScilabRelief.FLAT, SENTINEL));
        assertNotSame(SENTINEL, ScilabRelief.getBorderFromRelief("bogus", SENTINEL));
    }

    /**
     * Characterization: a null relief keyword is dereferenced by
     * {@code reliefType.equals(...)} and throws.
     */
    @Test
    void nullReliefThrowsNpe() {
        assertThrows(NullPointerException.class, () -> ScilabRelief.getBorderFromRelief(null, SENTINEL));
    }

    // ---- Utility class contract -----------------------------------------

    /**
     * {@code ScilabRelief} is a static utility class: its sole constructor is
     * private and rejects reflective instantiation with
     * {@link UnsupportedOperationException}.
     */
    @Test
    void constructorIsPrivateAndThrows() throws Exception {
        Constructor<ScilabRelief> ctor = ScilabRelief.class.getDeclaredConstructor();
        assertTrue(Modifier.isPrivate(ctor.getModifiers()));
        ctor.setAccessible(true);
        InvocationTargetException ex = assertThrows(InvocationTargetException.class, ctor::newInstance);
        assertTrue(ex.getCause() instanceof UnsupportedOperationException);
    }
}
