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

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit test for {@link DelPreviousCharAction} — it must backward-delete
 * by delegating to {@code backspace()} on the input view (and nothing else).
 */
public class DelPreviousCharActionTest {

    @Test
    public void backspacesTheInputViewExactlyOnce() {
        FakeInputCommandView view = new FakeInputCommandView();
        FakeConsoleConfiguration cfg = new FakeConsoleConfiguration();
        cfg.inputCommandView = view;
        DelPreviousCharAction action = new DelPreviousCharAction();
        action.setConfiguration(cfg);

        action.actionPerformed(null);

        assertEquals(1, view.backspaceCount);
        assertEquals(0, view.resetCount);
    }
}
