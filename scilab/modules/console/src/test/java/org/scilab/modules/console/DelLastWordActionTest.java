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
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link DelLastWordAction} — the "delete last word"
 * key action — driven through a {@link FakeConsoleConfiguration}. Several tests
 * are deliberate <em>defect-characterization</em> tests: the trailing-space
 * handling walks back by two characters at a time and can even crash, and these
 * tests pin that current behavior.
 */
public class DelLastWordActionTest {

    private DelLastWordAction action;
    private FakeInputCommandView view;

    private void wire(String text, int caret) {
        view = new FakeInputCommandView();
        view.text = text;
        view.caretPosition = caret;
        FakeConsoleConfiguration cfg = new FakeConsoleConfiguration();
        cfg.inputCommandView = view;
        action = new DelLastWordAction();
        action.setConfiguration(cfg);
    }

    @Test
    public void deletesTheTrailingWordKeepingTheSeparatingSpace() {
        wire("foo bar", 7);
        action.actionPerformed(null);
        assertEquals("foo ", view.text);
    }

    @Test
    public void keepsEverythingUpToAndIncludingTheLastSpaceForALongerLine() {
        wire("abc def ghi", 3);
        action.actionPerformed(null);
        assertEquals("abc def ", view.text);
        // caret (3) fits inside the new text (length 8), so it is preserved.
        assertEquals(3, view.lastSetCaretPosition);
    }

    @Test
    public void clampsCaretToTheEndWhenTheOldCaretIsBeyondTheNewText() {
        wire("foo bar", 7);
        action.actionPerformed(null);
        // New text is "foo " (length 4); caret 7 is beyond it, so it is clamped.
        assertEquals(4, view.lastSetCaretPosition);
    }

    @Test
    public void deletingTheOnlyWordEmptiesTheLine() {
        wire("hello", 5);
        action.actionPerformed(null);
        assertEquals("", view.text);
        assertEquals(0, view.lastSetCaretPosition);
    }

    @Test
    public void aTrailingSpaceCollapsesTheWholeLineToEmpty() {
        // Defect characterization: the "- 2" back-step plus the final
        // lastIndexOf(' ') == -1 wipes the entire line rather than the last word.
        wire("foo ", 4);
        action.actionPerformed(null);
        assertEquals("", view.text);
    }

    @Test
    public void aSingleSpaceLineThrowsStringIndexOutOfBounds() {
        // Defect characterization: substring(0, length - 2) with length == 1
        // asks for substring(0, -1) and blows up.
        wire(" ", 1);
        assertThrows(StringIndexOutOfBoundsException.class, () -> action.actionPerformed(null));
    }
}
