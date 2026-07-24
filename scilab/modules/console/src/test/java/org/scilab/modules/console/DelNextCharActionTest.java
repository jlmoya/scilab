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

import javax.swing.text.StyledDocument;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link DelNextCharAction} — forward-delete one
 * character.
 *
 * <p>The empty-document branch is intentionally not tested: it calls
 * {@code InterpreterManagement.requestScilabExec("exit")}, which reaches into
 * native Scilab and is out of scope for a hermetic test. Only the
 * non-empty-document behavior (which never touches native code) is exercised.
 */
public class DelNextCharActionTest {

    private DelNextCharAction action;
    private StyledDocument doc;

    private void wire(String docText, int caret) {
        FakeInputCommandView view = new FakeInputCommandView();
        view.caretPosition = caret;
        doc = ConsoleTestSupport.docOf(docText);
        FakeConsoleConfiguration cfg = new FakeConsoleConfiguration();
        cfg.inputCommandView = view;
        cfg.inputCommandViewStyledDocument = doc;
        action = new DelNextCharAction();
        action.setConfiguration(cfg);
    }

    @Test
    public void deletesTheCharacterUnderTheCaret() {
        wire("abc", 1);
        action.actionPerformed(null);
        assertEquals("ac", ConsoleTestSupport.textOf(doc));
    }

    @Test
    public void deletesTheFirstCharacterWhenCaretIsAtTheStart() {
        wire("abc", 0);
        action.actionPerformed(null);
        assertEquals("bc", ConsoleTestSupport.textOf(doc));
    }

    @Test
    public void deletesNothingWhenCaretIsAtTheEnd() {
        wire("abc", 3);
        action.actionPerformed(null);
        assertEquals("abc", ConsoleTestSupport.textOf(doc));
    }
}
