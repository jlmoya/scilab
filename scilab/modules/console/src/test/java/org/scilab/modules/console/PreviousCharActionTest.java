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
 * Hermetic unit tests for {@link PreviousCharAction} — move the caret one
 * character to the left, but never before the start of the line.
 */
public class PreviousCharActionTest {

    private PreviousCharAction action;
    private FakeInputCommandView view;

    private void wire(int caret) {
        view = new FakeInputCommandView();
        view.caretPosition = caret;
        FakeConsoleConfiguration cfg = new FakeConsoleConfiguration();
        cfg.inputCommandView = view;
        action = new PreviousCharAction();
        action.setConfiguration(cfg);
    }

    @Test
    public void movesCaretBackwardWhenNotAtTheStart() {
        wire(3);
        action.actionPerformed(null);
        assertEquals(2, view.lastSetCaretPosition);
    }

    @Test
    public void doesNothingWhenCaretIsAtTheStart() {
        wire(0);
        action.actionPerformed(null);
        assertFalse(view.setCaretPositionCalled);
    }
}
