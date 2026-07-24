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

import javax.swing.JPanel;

import com.artenum.rosetta.interfaces.core.InputParsingManager;
import com.artenum.rosetta.interfaces.ui.PromptView;

/**
 * Hermetic test double for the rosetta {@link PromptView} interface.
 *
 * <p>Extends {@link JPanel} because production code (e.g.
 * {@code SciInputParsingManager.getWindowCompletionLocation}) casts the prompt
 * view to a {@code JPanel} to read its width. The {@code GuiComponent} methods
 * ({@code setBackground}/{@code setForeground}/{@code setVisible}/{@code setFont})
 * are inherited from Swing. Constructing a {@code JPanel} is headless-safe.
 */
class FakePromptView extends JPanel implements PromptView {

    private static final long serialVersionUID = 1L;

    private String defaultPrompt = "-->";
    private String inBlockPrompt = "  >";
    int updatePromptCount = 0;

    public void setDefaultPrompt(String prompt) {
        defaultPrompt = prompt;
    }

    public void setInBlockPrompt(String prompt) {
        inBlockPrompt = prompt;
    }

    public String getDefaultPrompt() {
        return defaultPrompt;
    }

    public String getInBlockPrompt() {
        return inBlockPrompt;
    }

    public void updatePrompt() {
        updatePromptCount++;
    }

    public void setInputParsingManager(InputParsingManager ipm) {
    }
}
