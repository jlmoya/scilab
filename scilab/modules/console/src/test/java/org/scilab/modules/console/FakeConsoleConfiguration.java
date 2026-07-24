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

import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.text.StyledDocument;

import com.artenum.rosetta.interfaces.core.CompletionManager;
import com.artenum.rosetta.interfaces.core.ConsoleConfiguration;
import com.artenum.rosetta.interfaces.core.GenericInterpreter;
import com.artenum.rosetta.interfaces.core.HistoryManager;
import com.artenum.rosetta.interfaces.core.InputParsingManager;
import com.artenum.rosetta.interfaces.ui.CompletionWindow;
import com.artenum.rosetta.interfaces.ui.InputCommandView;
import com.artenum.rosetta.interfaces.ui.OutputView;
import com.artenum.rosetta.interfaces.ui.PromptView;

/**
 * Hermetic test double for the rosetta {@link ConsoleConfiguration} interface.
 *
 * <p>{@code AbstractConsoleAction} reaches every collaborator (input view,
 * styled document, history manager, ...) through this object, which the action
 * receives via {@code setConfiguration}. Supplying the fakes a test cares about
 * — and {@code null}/zero for the rest — lets the console actions be driven
 * without any live console window.
 */
class FakeConsoleConfiguration implements ConsoleConfiguration {

    InputCommandView inputCommandView;
    StyledDocument inputCommandViewStyledDocument;
    HistoryManager historyManager;
    InputParsingManager inputParsingManager;
    PromptView promptView;
    OutputView outputView;

    public PromptView getPromptView() {
        return promptView;
    }

    public OutputView getOutputView() {
        return outputView;
    }

    public InputCommandView getInputCommandView() {
        return inputCommandView;
    }

    public StyledDocument getOutputViewStyledDocument() {
        return null;
    }

    public StyledDocument getInputCommandViewStyledDocument() {
        return inputCommandViewStyledDocument;
    }

    public InputParsingManager getInputParsingManager() {
        return inputParsingManager;
    }

    public GenericInterpreter getGenericInterpreter() {
        return null;
    }

    public CompletionManager getCompletionManager() {
        return null;
    }

    public CompletionWindow getCompletionWindow() {
        return null;
    }

    public HistoryManager getHistoryManager() {
        return historyManager;
    }

    public String getBackgroundColor() {
        return null;
    }

    public String getForegroundColor() {
        return null;
    }

    public int getScrollableUnitIncrement() {
        return 0;
    }

    public int getScrollableBlockIncrement() {
        return 0;
    }

    public boolean getHorizontalWrapAllowed() {
        return false;
    }

    public boolean getVerticalWrapAllowed() {
        return false;
    }

    public String getFontName() {
        return null;
    }

    public int getFontStyle() {
        return 0;
    }

    public int getFontSize() {
        return 0;
    }

    public String getWelcomeLine() {
        return null;
    }

    // --- Configuration ------------------------------------------------------

    public void setActiveProfile(String profile) {
    }

    public String getActiveProfile() {
        return null;
    }

    public InputMap getKeyMapping() {
        return null;
    }

    public ActionMap getActionMapping() {
        return null;
    }
}
