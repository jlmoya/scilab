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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link SciTranslator}.
 *
 * <p>{@code SciTranslator} converts raw Java AWT event data (key codes, mouse
 * button + action, Ctrl modifier) into the integer codes Scilab callbacks
 * expect. The two conversion methods are static and pure; the click-action
 * accessors are plain state; and {@link SciTranslator#javaClick2Scilab()} is a
 * timed {@code wait(...)} that ultimately returns the current click action.
 *
 * <p>All of this runs without the native runtime, so it is exercised directly
 * here. The {@code CTRL} offset (1000) and the state constants are private /
 * documented magic numbers; the tests pin their observable effects.
 */
public class SciTranslatorTest {

    /** The private SCILAB_CTRL_OFFSET, mirrored here so the tests can assert it. */
    private static final int CTRL_OFFSET = 1000;

    // --- documented state constants ----------------------------------------

    @Test
    public void stateConstantsHaveTheirDocumentedValues() {
        assertEquals(-1, SciTranslator.PRESSED);
        assertEquals(-6, SciTranslator.RELEASED);
        assertEquals(2, SciTranslator.CLICKED);
        assertEquals(9, SciTranslator.DCLICKED);
        assertEquals(-1, SciTranslator.SCIMOVED);
        assertEquals(-1000, SciTranslator.SCICLOSE);
        assertEquals(-1000, SciTranslator.MOVED);
        assertEquals(-10000, SciTranslator.UNMANAGED);
    }

    @Test
    public void scimovedIsDeliberatelyTheSameCodeAsPressed() {
        // Characterization: SCIMOVED and PRESSED collapse to -1 on purpose
        // (see the class Javadoc: MOVE has no button, so it borrows PRESSED).
        assertEquals(SciTranslator.PRESSED, SciTranslator.SCIMOVED);
    }

    @Test
    public void scicloseAndMovedShareTheSameSentinelValue() {
        // Characterization: two semantically distinct sentinels alias to -1000.
        assertEquals(SciTranslator.SCICLOSE, SciTranslator.MOVED);
    }

    // --- constructor / click-action state ----------------------------------

    @Test
    public void freshTranslatorStartsInTheUnmanagedState() {
        SciTranslator t = new SciTranslator();
        assertEquals(SciTranslator.UNMANAGED, t.getClickAction());
    }

    @Test
    public void setClickActionIsReadBackByGetClickAction() {
        SciTranslator t = new SciTranslator();
        t.setClickAction(SciTranslator.CLICKED);
        assertEquals(SciTranslator.CLICKED, t.getClickAction());

        t.setClickAction(SciTranslator.DCLICKED);
        assertEquals(SciTranslator.DCLICKED, t.getClickAction());
    }

    @Test
    public void setClickActionAcceptsArbitraryIntegers() {
        SciTranslator t = new SciTranslator();
        t.setClickAction(Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, t.getClickAction());
        t.setClickAction(Integer.MIN_VALUE);
        assertEquals(Integer.MIN_VALUE, t.getClickAction());
    }

    @Test
    public void twoTranslatorsKeepIndependentClickState() {
        SciTranslator a = new SciTranslator();
        SciTranslator b = new SciTranslator();
        a.setClickAction(SciTranslator.CLICKED);
        // b was never touched, so it must still be UNMANAGED.
        assertEquals(SciTranslator.CLICKED, a.getClickAction());
        assertEquals(SciTranslator.UNMANAGED, b.getClickAction());
    }

    // --- javaKey2Scilab -----------------------------------------------------

    @Test
    public void javaKeyWithoutControlPassesTheKeyCodeThrough() {
        assertEquals(65, SciTranslator.javaKey2Scilab(65, false));
        assertEquals(0, SciTranslator.javaKey2Scilab(0, false));
    }

    @Test
    public void javaKeyWithControlAddsTheCtrlOffset() {
        assertEquals(65 + CTRL_OFFSET, SciTranslator.javaKey2Scilab(65, true));
        assertEquals(CTRL_OFFSET, SciTranslator.javaKey2Scilab(0, true));
    }

    @Test
    public void javaKeyControlOffsetIsExactlyOneThousand() {
        // The whole point of the Ctrl handling: two conversions of the same key
        // differ by exactly SCILAB_CTRL_OFFSET.
        int plain = SciTranslator.javaKey2Scilab(42, false);
        int ctrl = SciTranslator.javaKey2Scilab(42, true);
        assertEquals(CTRL_OFFSET, ctrl - plain);
    }

    @Test
    public void javaKeyHandlesNegativeKeyCodes() {
        assertEquals(-5, SciTranslator.javaKey2Scilab(-5, false));
        assertEquals(-5 + CTRL_OFFSET, SciTranslator.javaKey2Scilab(-5, true));
    }

    // --- javaButton2Scilab --------------------------------------------------

    @Test
    public void leftButtonPressedMatchesTheDocumentedExample() {
        // The class Javadoc states: left PRESSED = 1 + (-1) = 0.
        int code = SciTranslator.javaButton2Scilab(1, SciTranslator.PRESSED, false);
        assertEquals(0, code);
    }

    @Test
    public void buttonCodeIsButtonPlusActionWithoutControl() {
        // middle (2) + CLICKED (2) = 4
        assertEquals(4, SciTranslator.javaButton2Scilab(2, SciTranslator.CLICKED, false));
        // right (3) + RELEASED (-6) = -3
        assertEquals(-3, SciTranslator.javaButton2Scilab(3, SciTranslator.RELEASED, false));
    }

    @Test
    public void buttonCodeAddsCtrlOffsetWhenControlIsDown() {
        int plain = SciTranslator.javaButton2Scilab(1, SciTranslator.CLICKED, false);
        int ctrl = SciTranslator.javaButton2Scilab(1, SciTranslator.CLICKED, true);
        assertEquals(CTRL_OFFSET, ctrl - plain);
        assertEquals(1 + SciTranslator.CLICKED + CTRL_OFFSET, ctrl);
    }

    @Test
    public void buttonCodeIsPurelyAdditiveAndOrderIndependent() {
        // button + action is symmetric, so swapping the two arguments cannot
        // change the (no-Ctrl) result.
        assertEquals(SciTranslator.javaButton2Scilab(3, SciTranslator.DCLICKED, false),
                     SciTranslator.javaButton2Scilab(SciTranslator.DCLICKED, 3, false));
    }

    @Test
    public void distinctButtonActionCombinationsProduceDistinctCodes() {
        int leftClicked = SciTranslator.javaButton2Scilab(1, SciTranslator.CLICKED, false);
        int leftDoubleClicked = SciTranslator.javaButton2Scilab(1, SciTranslator.DCLICKED, false);
        assertNotEquals(leftClicked, leftDoubleClicked);
    }

    // --- javaClick2Scilab ---------------------------------------------------

    @Test
    public void javaClickReturnsTheCurrentClickActionAfterWaiting() {
        // javaClick2Scilab() does a bounded wait(300ms) with nobody notifying,
        // then returns getClickAction(). It must surface whatever was last set.
        // (The wait duration is not asserted: a bounded wait may return early on
        // a spurious wakeup, so only the returned value is a firm contract.)
        SciTranslator t = new SciTranslator();
        t.setClickAction(SciTranslator.DCLICKED);
        assertEquals(SciTranslator.DCLICKED, t.javaClick2Scilab());
    }

    @Test
    public void javaClickOnAFreshTranslatorReturnsUnmanaged() {
        SciTranslator t = new SciTranslator();
        assertEquals(SciTranslator.UNMANAGED, t.javaClick2Scilab());
    }
}
