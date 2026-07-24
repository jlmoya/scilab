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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Font;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link SciPromptView}. The view is a plain
 * {@link javax.swing.JPanel} wrapping a {@link javax.swing.JLabel}; constructing
 * it (never showing it) is headless-safe, so the prompt-string bookkeeping and
 * the colour/font propagation to the inner label can be exercised without a live
 * console. Only construction and getter/setter logic is touched — nothing here
 * paints or requires an event loop.
 */
public class SciPromptViewTest {

    private SciPromptView view;

    @BeforeEach
    public void setUp() {
        view = new SciPromptView();
    }

    @Test
    public void freshlyConstructedViewIsInvisibleWithTheDefaultPrompts() {
        // The constructor hides the prompt until the console shows it.
        assertFalse(view.isVisible());
        assertEquals("-->", view.getDefaultPrompt());
        // Characterization: the in-block prompt defaults to the very same "-->".
        assertEquals("-->", view.getInBlockPrompt());
        assertEquals(view.getDefaultPrompt(), view.getInBlockPrompt());
    }

    @Test
    public void theLabelStartsEmptyBecauseUpdatePromptIsNotCalledByTheConstructor() {
        // The default prompt string exists, but nothing has pushed it into the label yet.
        assertEquals("", view.getPromptUI().getText());
    }

    @Test
    public void updatePromptPushesTheDefaultPromptIntoTheLabel() {
        view.updatePrompt();
        assertEquals("-->", view.getPromptUI().getText());
    }

    @Test
    public void setDefaultPromptUpdatesBothTheFieldAndTheRenderedLabel() {
        view.setDefaultPrompt(">>");
        assertEquals(">>", view.getDefaultPrompt());
        // setDefaultPrompt calls updatePrompt internally, so the label follows immediately.
        assertEquals(">>", view.getPromptUI().getText());
    }

    @Test
    public void setInBlockPromptIsStoredButNeverRendered() {
        // Characterization of a real quirk: updatePrompt() only ever renders the
        // *default* prompt, so the in-block prompt is remembered yet the label
        // keeps showing the default.
        view.setDefaultPrompt("A");
        assertEquals("A", view.getPromptUI().getText());

        view.setInBlockPrompt("B");
        assertEquals("B", view.getInBlockPrompt());
        assertEquals("A", view.getPromptUI().getText());
    }

    @Test
    public void updatePromptIsIdempotentOnceTheLabelMatches() {
        view.setDefaultPrompt("$$");
        assertEquals("$$", view.getPromptUI().getText());
        // A second call must not corrupt the already-correct label.
        view.updatePrompt();
        assertEquals("$$", view.getPromptUI().getText());
    }

    @Test
    public void backgroundColourIsForwardedToTheInnerLabel() {
        view.setBackground(Color.RED);
        assertEquals(Color.RED, view.getBackground());
        assertEquals(Color.RED, view.getPromptUI().getBackground());
    }

    @Test
    public void foregroundColourIsForwardedToTheInnerLabel() {
        view.setForeground(Color.BLUE);
        assertEquals(Color.BLUE, view.getForeground());
        assertEquals(Color.BLUE, view.getPromptUI().getForeground());
    }

    @Test
    public void fontIsForwardedToTheInnerLabel() {
        Font font = new Font("Dialog", Font.BOLD, 17);
        view.setFont(font);
        assertSame(font, view.getFont());
        assertSame(font, view.getPromptUI().getFont());
    }

    @Test
    public void promptUiIsANonNullOpaqueLabel() {
        assertTrue(view.getPromptUI().isOpaque());
    }
}
