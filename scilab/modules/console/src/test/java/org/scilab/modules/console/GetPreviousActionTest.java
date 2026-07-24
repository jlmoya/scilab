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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link GetPreviousAction} (browse backward in
 * history). Mirrors {@link GetNextActionTest} but drives the
 * {@code getPreviousEntry} branch of the identical state machine.
 */
public class GetPreviousActionTest {

    private GetPreviousAction action;
    private FakeHistoryManager history;
    private FakeInputCommandView view;

    private void wire() {
        history = new FakeHistoryManager();
        view = new FakeInputCommandView();
        FakeConsoleConfiguration cfg = new FakeConsoleConfiguration();
        cfg.historyManager = history;
        cfg.inputCommandView = view;
        action = new GetPreviousAction();
        action.setConfiguration(cfg);
    }

    @Test
    public void firstBrowseSavesTheTypedLineAndShowsThePreviousEntry() {
        wire();
        history.inHistory = false;
        view.text = "typed";
        history.previousEntryReturn = "cmd1";

        action.actionPerformed(null);

        assertTrue(history.inHistory);
        assertEquals("typed", history.tmpEntry);
        assertEquals("typed", history.lastPreviousArg);
        assertEquals("cmd1", view.text);
        assertEquals(1, view.resetCount);
    }

    @Test
    public void fallingOffTheStartRestoresTheTypedLineAndLeavesHistory() {
        wire();
        history.inHistory = false;
        view.text = "typed";
        history.previousEntryReturn = null;

        action.actionPerformed(null);

        assertFalse(history.inHistory);
        assertNull(history.tmpEntry);
        assertEquals("typed", view.text);
    }

    @Test
    public void whenAlreadyBrowsingItUsesTheExistingStash() {
        wire();
        history.inHistory = true;
        history.tmpEntry = "sta";
        history.previousEntryReturn = "older";

        action.actionPerformed(null);

        assertEquals("sta", history.lastPreviousArg);
        assertTrue(history.inHistory);
        assertEquals("older", view.text);
    }

    @Test
    public void aNullStashAndNoEntryLeavesTheInputUntouched() {
        wire();
        history.inHistory = true;
        history.tmpEntry = null;
        history.previousEntryReturn = null;
        view.text = "orig";

        action.actionPerformed(null);

        assertEquals("orig", view.text);
        assertEquals(0, view.resetCount);
        assertFalse(history.inHistory);
    }
}
