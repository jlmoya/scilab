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
 * Hermetic unit test for {@link LineEndAction} — it must move the caret to the
 * end of the line (and not to the beginning).
 */
public class LineEndActionTest {

    @Test
    public void movesCaretToTheEndAndNotToTheBeginning() {
        FakeInputCommandView view = new FakeInputCommandView();
        view.text = "abcdef";
        view.caretPosition = 2;
        FakeConsoleConfiguration cfg = new FakeConsoleConfiguration();
        cfg.inputCommandView = view;
        LineEndAction action = new LineEndAction();
        action.setConfiguration(cfg);

        action.actionPerformed(null);

        assertEquals(1, view.toEndCount);
        assertEquals(0, view.toBeginningCount);
        assertEquals(view.text.length(), view.caretPosition);
    }
}
