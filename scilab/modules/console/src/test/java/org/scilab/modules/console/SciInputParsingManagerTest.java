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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.awt.Point;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.artenum.rosetta.util.StringConstants;

/**
 * Hermetic unit tests for {@link SciInputParsingManager}. The manager is wired
 * to a {@link FakeInputCommandView} and {@link FakePromptView} so every method
 * that only touches those collaborators can be exercised without a live console.
 * Methods that route into the native completion engine ({@code getPartLevel}
 * with a non-empty line) are only exercised on their empty-line short-circuit.
 */
public class SciInputParsingManagerTest {

    private static final String NL = StringConstants.NEW_LINE;

    private SciInputParsingManager mgr;
    private FakeInputCommandView view;

    @BeforeEach
    public void setUp() {
        mgr = new SciInputParsingManager();
        view = new FakeInputCommandView();
        mgr.setInputCommandView(view);
    }

    @Test
    public void completionLevelIsAlwaysZero() {
        assertEquals(0, mgr.getCompletionLevel());
    }

    @Test
    public void blockEditingIsAlwaysFalse() {
        assertFalse(mgr.isBlockEditing());
    }

    @Test
    public void getCommandLineReflectsTheInputViewText() {
        view.text = "disp(1)";
        assertEquals("disp(1)", mgr.getCommandLine());
    }

    @Test
    public void getCaretPositionReflectsTheInputView() {
        view.caretPosition = 4;
        assertEquals(4, mgr.getCaretPosition());
    }

    @Test
    public void appendDelegatesToTheInputView() {
        mgr.append("xyz");
        assertEquals(1, view.appended.size());
        assertEquals("xyz", view.appended.get(0));
    }

    @Test
    public void resetDelegatesToTheInputView() {
        mgr.reset();
        assertEquals(1, view.resetCount);
    }

    @Test
    public void backspaceDelegatesToTheInputView() {
        mgr.backspace();
        assertEquals(1, view.backspaceCount);
    }

    @Test
    public void numberOfLinesForPlainTextWithoutNewlineIsOne() {
        view.text = "abc";
        assertEquals(1, mgr.getNumberOfLines());
    }

    @Test
    public void numberOfLinesForEmptyTextIsOne() {
        view.text = "";
        assertEquals(1, mgr.getNumberOfLines());
    }

    @Test
    public void numberOfLinesAddsAnExtraLineWheneverANewlineIsPresent() {
        // Characterization of the off-by-one: "a\nb" splits into 2 pieces but the
        // presence of a newline bumps the count to 3.
        view.text = "a" + NL + "b";
        assertEquals(3, mgr.getNumberOfLines());
    }

    @Test
    public void numberOfLinesForTrailingNewlineIsTwo() {
        view.text = "a" + NL;
        assertEquals(2, mgr.getNumberOfLines());
    }

    @Test
    public void getPartLevelReturnsEmptyWhenCaretIsAtTheStart() {
        // caret 0 => the substring(0,0) is empty, so the completion engine is never
        // consulted and the result is the empty string.
        view.caretPosition = 0;
        view.text = "sqrt";
        assertEquals("", mgr.getPartLevel(0));
    }

    @Test
    public void writeCompletionPartAppendsTheWholeResultWhenNoPartWasTyped() {
        // With the caret at 0, getPartLevel is "" so the entire completion result is appended.
        view.caretPosition = 0;
        mgr.writeCompletionPart("length");
        assertEquals(1, view.appended.size());
        assertEquals("length", view.appended.get(0));
    }

    @Test
    public void promptViewRoundTrips() {
        FakePromptView prompt = new FakePromptView();
        mgr.setPromptView(prompt);
        assertSame(prompt, mgr.getPromptView());
    }

    @Test
    public void windowCompletionLocationIsTheCaretShiftedLeftByThePromptWidth() {
        FakePromptView prompt = new FakePromptView();
        prompt.setSize(30, 10);
        mgr.setPromptView(prompt);
        view.caretLocation = new Point(100, 50);

        Point loc = mgr.getWindowCompletionLocation();
        assertEquals(70, loc.x);
        assertEquals(50, loc.y);
    }
}
