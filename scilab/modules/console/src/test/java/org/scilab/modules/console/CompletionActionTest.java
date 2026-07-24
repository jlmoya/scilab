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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link CompletionAction}. The full completion flow
 * routes into the native completion engine and a live completion window, but the
 * very first thing {@code actionPerformed} does is read the caret position and
 * bail out when it is {@code 0}. That guard is exercised here with a real
 * {@link SciInputParsingManager} backed by a {@link FakeInputCommandView}, so no
 * completion manager or window is needed. The tests also pin down that the caret
 * guard is the sole gate before the (here absent) completion manager is
 * dereferenced.
 */
public class CompletionActionTest {

    private CompletionAction wire(int caret) {
        FakeInputCommandView view = new FakeInputCommandView();
        view.caretPosition = caret;

        SciInputParsingManager ipm = new SciInputParsingManager();
        ipm.setInputCommandView(view);

        FakeConsoleConfiguration cfg = new FakeConsoleConfiguration();
        cfg.inputParsingManager = ipm;

        CompletionAction action = new CompletionAction();
        action.setConfiguration(cfg);
        return action;
    }

    @Test
    public void aCaretAtZeroShortCircuitsBeforeAnyCompletionCollaboratorIsTouched() {
        // The completion manager/window are left null in the configuration; the
        // caret==0 guard must return before they are ever dereferenced.
        CompletionAction action = wire(0);
        assertDoesNotThrow(() -> action.actionPerformed(null));
    }

    @Test
    public void aNonZeroCaretFallsThroughToTheCompletionManager() {
        // Characterization: past the caret guard the action immediately consults
        // the completion manager, which is absent here, proving the guard is the
        // only thing standing between actionPerformed and that collaborator.
        CompletionAction action = wire(3);
        assertThrows(NullPointerException.class, () -> action.actionPerformed(null));
    }
}
