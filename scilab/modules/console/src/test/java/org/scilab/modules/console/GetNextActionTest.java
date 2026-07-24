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
 * Hermetic unit tests for {@link GetNextAction} (browse forward in history),
 * driven through a {@link FakeConsoleConfiguration} + {@link FakeHistoryManager}.
 * The whole "enter history / save the typed line / restore it when we fall off
 * the end" state machine is exercised without the native history store.
 */
public class GetNextActionTest {

    private GetNextAction action;
    private FakeHistoryManager history;
    private FakeInputCommandView view;

    private void wire() {
        history = new FakeHistoryManager();
        view = new FakeInputCommandView();
        FakeConsoleConfiguration cfg = new FakeConsoleConfiguration();
        cfg.historyManager = history;
        cfg.inputCommandView = view;
        action = new GetNextAction();
        action.setConfiguration(cfg);
    }

    @Test
    public void firstBrowseSavesTheTypedLineAndShowsTheReturnedEntry() {
        wire();
        history.inHistory = false;
        view.text = "typed";
        history.nextEntryReturn = "cmd2";

        action.actionPerformed(null);

        assertTrue(history.inHistory);
        assertEquals("typed", history.tmpEntry);   // the in-progress line was stashed
        assertEquals("typed", history.lastNextArg); // lookup used the stashed token
        assertEquals("cmd2", view.text);            // reset()+append() replaced the line
        assertEquals(1, view.resetCount);
    }

    @Test
    public void fallingOffTheEndRestoresTheTypedLineAndLeavesHistory() {
        wire();
        history.inHistory = false;
        view.text = "typed";
        history.nextEntryReturn = null;             // no further entry

        action.actionPerformed(null);

        assertFalse(history.inHistory);             // left history browsing
        assertNull(history.tmpEntry);               // stash cleared
        assertEquals("typed", view.text);           // original line restored
    }

    @Test
    public void whenAlreadyBrowsingItDoesNotReStashTheTypedLine() {
        wire();
        history.inHistory = true;
        history.tmpEntry = "x";
        view.text = "should-be-ignored-as-stash";
        history.nextEntryReturn = "y";

        action.actionPerformed(null);

        assertEquals("x", history.lastNextArg);     // used the existing stash, not the view text
        assertTrue(history.inHistory);
        assertEquals("x", history.tmpEntry);        // stash untouched
        assertEquals("y", view.text);
    }

    @Test
    public void aNullStashAndNoEntryLeavesTheInputUntouched() {
        wire();
        history.inHistory = true;
        history.tmpEntry = null;
        history.nextEntryReturn = null;
        view.text = "orig";

        action.actionPerformed(null);

        assertEquals("orig", view.text);            // nothing appended
        assertEquals(0, view.resetCount);           // and nothing reset
        assertFalse(history.inHistory);
    }
}
