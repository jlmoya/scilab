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

import org.scilab.modules.gui.bridge.tab.SwingScilabDockablePanel;
import org.scilab.modules.gui.tabfactory.AbstractScilabTabFactory;

/**
 * The Terminal tab factory.
 * A component which needs to restore a Tab with a given uuid must register its factory.
 *
 * @author Jose Moya
 */
public class TerminalTabFactory extends AbstractScilabTabFactory {

    public static final String APPLICATION = "Terminal";
    public static final String PACKAGE = "";
    public static final String CLASS = "org.scilab.modules.terminal.TerminalTabFactory";

    private static TerminalTabFactory instance;

    /**
     * Default constructor
     */
    public TerminalTabFactory() {
        if (instance == null) {
            instance = this;
        }
    }

    /**
     * {@inheritDoc}
     */
    public SwingScilabDockablePanel getTab(String uuid) {
        return TerminalTab.getTerminalInstance(uuid);
    }

    /**
     * {@inheritDoc}
     */
    public String getPackage() {
        return PACKAGE;
    }

    /**
     * {@inheritDoc}
     */
    public String getClassName() {
        return CLASS;
    }

    /**
     * {@inheritDoc}
     */
    public String getApplication() {
        return APPLICATION;
    }

    /**
     * {@inheritDoc}
     */
    public boolean isAValidUUID(String uuid) {
        return ScilabTerminal.TERMINALUUID.equals(uuid);
    }

    /**
     * @return an instance of this factory
     */
    public static TerminalTabFactory getInstance() {
        new TerminalTabFactory();

        return instance;
    }
}
