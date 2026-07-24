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

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link NextCharAction} — move the caret one character
 * to the right, but never past the end of the document.
 */
public class NextCharActionTest {

    private NextCharAction action;
    private FakeInputCommandView view;

    private void wire(String docText, int caret) {
        view = new FakeInputCommandView();
        view.caretPosition = caret;
        FakeConsoleConfiguration cfg = new FakeConsoleConfiguration();
        cfg.inputCommandView = view;
        cfg.inputCommandViewStyledDocument = ConsoleTestSupport.docOf(docText);
        action = new NextCharAction();
        action.setConfiguration(cfg);
    }

    @Test
    public void movesCaretForwardWhenNotAtTheEnd() {
        wire("abcde", 2);
        action.actionPerformed(null);
        assertEquals(3, view.lastSetCaretPosition);
    }

    @Test
    public void movesToTheLastPositionFromOneBeforeTheEnd() {
        wire("abcde", 4);
        action.actionPerformed(null);
        assertEquals(5, view.lastSetCaretPosition);
    }

    @Test
    public void doesNothingWhenCaretIsAlreadyAtTheEnd() {
        wire("abcde", 5);
        action.actionPerformed(null);
        assertFalse(view.setCaretPositionCalled);
    }
}
