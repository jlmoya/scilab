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
 * Hermetic unit tests for {@link DelEndOfLineAction} — delete everything from
 * the caret to the end of the line — exercised against a real
 * {@link StyledDocument}.
 */
public class DelEndOfLineActionTest {

    private DelEndOfLineAction action;
    private StyledDocument doc;

    private void wire(String docText, int caret) {
        FakeInputCommandView view = new FakeInputCommandView();
        view.caretPosition = caret;
        doc = ConsoleTestSupport.docOf(docText);
        FakeConsoleConfiguration cfg = new FakeConsoleConfiguration();
        cfg.inputCommandView = view;
        cfg.inputCommandViewStyledDocument = doc;
        action = new DelEndOfLineAction();
        action.setConfiguration(cfg);
    }

    @Test
    public void removesTheTailAfterTheCaret() {
        wire("hello world", 5);
        action.actionPerformed(null);
        assertEquals("hello", ConsoleTestSupport.textOf(doc));
    }

    @Test
    public void removesTheWholeLineWhenCaretIsAtTheStart() {
        wire("hello world", 0);
        action.actionPerformed(null);
        assertEquals("", ConsoleTestSupport.textOf(doc));
    }

    @Test
    public void removesNothingWhenCaretIsAtTheEnd() {
        wire("hello world", 11);
        action.actionPerformed(null);
        assertEquals("hello world", ConsoleTestSupport.textOf(doc));
    }
}
