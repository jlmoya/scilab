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

import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import com.jediterm.terminal.ui.JediTermWidget;
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider;

import org.scilab.modules.commons.gui.ScilabGUIUtilities;
import org.scilab.modules.gui.bridge.tab.SwingScilabDockablePanel;
import org.scilab.modules.gui.bridge.window.SwingScilabWindow;
import org.scilab.modules.gui.menubar.MenuBar;
import org.scilab.modules.gui.tab.SimpleTab;
import org.scilab.modules.gui.tabfactory.ScilabTabFactory;
import org.scilab.modules.gui.textbox.ScilabTextBox;
import org.scilab.modules.gui.textbox.TextBox;
import org.scilab.modules.gui.toolbar.ToolBar;
import org.scilab.modules.gui.utils.WindowsConfigurationManager;

/**
 * Embedded terminal tab: a JediTerm VT emulator driven by our JNA {@link Pty},
 * hosted in a Scilab dockable panel - modelled on the Command History browser.
 *
 * Run any shell command, in particular {@code claude --dangerously-skip-permissions -c},
 * exactly like the terminal embedded in JetBrains IDEs. Opened by the {@code terminal()}
 * Scilab command (see macros/terminal.sci).
 *
 * @author Jose Moya
 */
@SuppressWarnings(value = { "serial" })
public final class ScilabTerminal extends SwingScilabDockablePanel implements SimpleTab {

    public static final String TERMINALUUID = "b3a8f1c2-7d4e-4a6b-9c1f-2e5d6a7b8c90";
    public static final String TITLE = "Terminal";

    private static final int DEFAULT_COLS = 100;
    private static final int DEFAULT_ROWS = 30;

    private static SwingScilabDockablePanel terminalTab;
    private static volatile String lastError = "";
    private static boolean shutdownHookRegistered = false;

    private transient Pty pty;
    private transient PtyTtyConnector connector;
    private transient JediTermWidget widget;

    static {
        ScilabTabFactory.getInstance().addTabFactory(TerminalTabFactory.getInstance());
    }

    /**
     * Constructor - builds the JediTerm widget on a freshly spawned login shell.
     */
    private ScilabTerminal() {
        super(TITLE, TERMINALUUID);
        addInfoBar(ScilabTextBox.createTextBox());

        widget = new JediTermWidget(DEFAULT_COLS, DEFAULT_ROWS, new DefaultSettingsProvider());
        startShell();
        widget.setTtyConnector(connector);
        widget.start();

        JPanel contentPane = new JPanel(new BorderLayout());
        contentPane.add(widget, BorderLayout.CENTER);
        setContentPane(contentPane);
    }

    /**
     * Spawn the user's login+interactive shell on a fresh PTY. A login shell
     * (-l) re-sources the profile so a .app/Finder launch's minimal PATH is
     * rebuilt and {@code claude}/{@code node} resolve, just like Terminal.app.
     */
    private void startShell() {
        String shell = System.getenv("SHELL");
        if (shell == null || shell.isEmpty()) {
            shell = "/bin/bash";
        }

        Map<String, String> env = new LinkedHashMap<String, String>(System.getenv());
        env.put("TERM", "xterm-256color");
        if (env.get("LANG") == null) {
            env.put("LANG", "en_US.UTF-8");
        }
        List<String> envp = new ArrayList<String>();
        for (Map.Entry<String, String> e : env.entrySet()) {
            envp.add(e.getKey() + "=" + e.getValue());
        }

        pty = new Pty();
        try {
            pty.start(shell, new String[] {shell, "-l", "-i"},
                      envp.toArray(new String[0]), DEFAULT_ROWS, DEFAULT_COLS);
        } catch (java.io.IOException ex) {
            throw new RuntimeException("Could not start terminal shell '" + shell + "': " + ex.getMessage(), ex);
        }
        connector = new PtyTtyConnector(pty);
    }

    /**
     * Create a new terminal tab (called by the tab factory).
     * @return the corresponding tab
     */
    public static SwingScilabDockablePanel createTerminalTab() {
        ScilabTerminal tab = new ScilabTerminal();
        terminalTab = tab;
        registerShutdownHook();
        WindowsConfigurationManager.restorationFinished(tab);
        return tab;
    }

    /**
     * Backstop for a non-graceful exit: ensure the child shell is killed and the
     * PTY released even if etc/terminal.quit did not run. Registered lazily (when
     * a terminal is actually opened) and guarded so it never throws if the JVM is
     * already shutting down.
     */
    private static synchronized void registerShutdownHook() {
        if (shutdownHookRegistered) {
            return;
        }
        try {
            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                public void run() {
                    closeAllTerminals();
                }
            }, "scilab-terminal-shutdown"));
            shutdownHookRegistered = true;
        } catch (IllegalStateException alreadyShuttingDown) {
            // nothing to do - quit hook handles teardown
        }
    }

    /**
     * Open (or bring to front) the terminal tab. Entry point for the
     * {@code terminal()} macro; safe to call from the interpreter thread.
     */
    public static void openTerminal() {
        if (SwingUtilities.isEventDispatchThread()) {
            setVisibleSafe();
        } else {
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    setVisibleSafe();
                }
            });
        }
    }

    private static void setVisibleSafe() {
        try {
            lastError = "";
            setVisible();
        } catch (Throwable t) {
            lastError = String.valueOf(t);
            t.printStackTrace();
        }
    }

    /** @return true if a terminal tab is currently open (test/introspection hook). */
    public static boolean isTerminalOpen() {
        return terminalTab != null;
    }

    /** @return the last error thrown while opening the terminal, or "" if none. */
    public static String getLastError() {
        return lastError;
    }

    /**
     * Manage terminal tab visibility (mirrors CommandHistory.setVisible).
     */
    private static void setVisible() {
        if (terminalTab == null) {
            boolean success = WindowsConfigurationManager.restoreUUID(TERMINALUUID);
            if (!success) {
                TerminalTabFactory.getInstance().getTab(TERMINALUUID);
                SwingScilabWindow window = SwingScilabWindow.createWindow(true);
                window.addTab(terminalTab);
                window.setLocation(0, 0);
                window.setSize(800, 500);
                window.setVisible(true);
            }
        }
        ScilabGUIUtilities.toFront(terminalTab, TITLE);
        terminalTab.setVisible(true);
    }

    /**
     * Close the terminal tab and tear down the PTY (called when the tab is closed).
     */
    public static void closeTerminal() {
        closeAllTerminals();
    }

    /**
     * Tear down every open terminal: SIGHUP/close its PTY so the child shell dies
     * and the JediTerm reader thread unblocks. Idempotent and thread-safe; invoked
     * from etc/terminal.quit on Scilab shutdown and from the JVM shutdown hook, so
     * a live shell never keeps the JVM from exiting.
     */
    public static synchronized void closeAllTerminals() {
        if (terminalTab instanceof ScilabTerminal) {
            ((ScilabTerminal) terminalTab).disposeShell();
        }
        terminalTab = null;
    }

    private void disposeShell() {
        try {
            if (connector != null) {
                connector.close();
            }
        } catch (Throwable ignore) { }
        try {
            if (pty != null) {
                pty.terminate();
            }
        } catch (Throwable ignore) { }
    }

    /**
     * @return the terminal tab, or null if none is open
     */
    public static SwingScilabDockablePanel getTerminalTab() {
        return terminalTab;
    }

    /**
     * {@inheritDoc}
     */
    public void addInfoBar(TextBox infoBarToAdd) {
        setInfoBar(infoBarToAdd);
    }

    /**
     * {@inheritDoc}
     */
    public void addMenuBar(MenuBar menuBarToAdd) {
        setMenuBar(menuBarToAdd);
    }

    /**
     * {@inheritDoc}
     */
    public void addToolBar(ToolBar toolBarToAdd) {
        setToolBar(toolBarToAdd);
    }

    /**
     * {@inheritDoc}
     */
    public SwingScilabWindow getParentWindow() {
        return SwingScilabWindow.allScilabWindows.get(getParentWindowId());
    }

    /**
     * {@inheritDoc}
     */
    public SimpleTab getAsSimpleTab() {
        return this;
    }
}
