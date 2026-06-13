/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Jose Moya
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 *
 */

package org.scilab.modules.terminal;

import java.util.List;
import java.util.ListIterator;

import org.scilab.modules.gui.bridge.tab.SwingScilabDockablePanel;
import org.scilab.modules.gui.tabfactory.ScilabTabFactory;
import org.scilab.modules.gui.utils.ClosingOperationsManager;

/**
 * Creates a Terminal tab for a given uuid and registers its closing operation
 * (which tears down that terminal's PTY).
 *
 * @author Jose Moya
 */
public class TerminalTab {

    /**
     * @param uuid the uuid of the terminal tab to create
     * @return a new Terminal tab
     */
    public static SwingScilabDockablePanel getTerminalInstance(final String uuid) {
        final SwingScilabDockablePanel term = ScilabTerminal.createTerminalTab(uuid);
        ScilabTabFactory.getInstance().addToCache(term);

        ClosingOperationsManager.registerClosingOperation(term, new ClosingOperationsManager.ClosingOperation() {

            @Override
            public int canClose() {
                return 1;
            }

            @Override
            public void destroy() {
                ScilabTerminal.closeTerminal(uuid);
            }

            @Override
            public String askForClosing(final List<SwingScilabDockablePanel> list) {
                return null;
            }

            @Override
            public void updateDependencies(List<SwingScilabDockablePanel> list, ListIterator<SwingScilabDockablePanel> it) {
            }
        });

        ClosingOperationsManager.addDependencyWithRoot(term);

        return term;
    }
}
