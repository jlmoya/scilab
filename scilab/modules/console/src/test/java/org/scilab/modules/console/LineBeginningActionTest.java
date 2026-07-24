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
 * Hermetic unit test for {@link LineBeginningAction} — it must move the caret to
 * the beginning of the line (and not to the end).
 */
public class LineBeginningActionTest {

    @Test
    public void movesCaretToTheBeginningAndNotToTheEnd() {
        FakeInputCommandView view = new FakeInputCommandView();
        view.text = "abcdef";
        view.caretPosition = 4;
        FakeConsoleConfiguration cfg = new FakeConsoleConfiguration();
        cfg.inputCommandView = view;
        LineBeginningAction action = new LineBeginningAction();
        action.setConfiguration(cfg);

        action.actionPerformed(null);

        assertEquals(1, view.toBeginningCount);
        assertEquals(0, view.toEndCount);
        assertEquals(0, view.caretPosition);
    }
}
