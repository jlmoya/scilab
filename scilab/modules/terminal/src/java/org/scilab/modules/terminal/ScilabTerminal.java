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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
 * Multiple independent sessions are supported: each {@code terminal()} call opens
 * a new tab with its own PTY/shell and a unique uuid, tracked in {@link #INSTANCES}.
 * Terminals are ephemeral (a restored terminal would hold a dead shell), so the
 * factory only recognises currently-open uuids - they are not restored across
 * Scilab restarts.
 *
 * Run any shell command, in particular {@code claude --dangerously-skip-permissions -c},
 * exactly like the terminal embedded in JetBrains IDEs.
 *
 * @author Jose Moya
 */
@SuppressWarnings(value = { "serial" })
public final class ScilabTerminal extends SwingScilabDockablePanel implements SimpleTab {

    public static final String TITLE = "Terminal";

    private static final int DEFAULT_COLS = 100;
    private static final int DEFAULT_ROWS = 30;

    /** All currently-open terminals, keyed by tab uuid (insertion-ordered). */
    private static final Map<String, ScilabTerminal> INSTANCES =
        Collections.synchronizedMap(new LinkedHashMap<String, ScilabTerminal>());
    private static volatile String lastError = "";
    private static boolean shutdownHookRegistered = false;

    private final String uuid;
    private transient Pty pty;
    private transient PtyTtyConnector connector;
    private transient JediTermWidget widget;

    static {
        ScilabTabFactory.getInstance().addTabFactory(TerminalTabFactory.getInstance());
    }

    /**
     * Constructor - builds the JediTerm widget on a freshly spawned login shell.
     */
    private ScilabTerminal(String uuid) {
        super(TITLE, uuid);
        this.uuid = uuid;
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
     * Create a terminal tab bound to {@code uuid} and register it (called by the
     * tab factory / the open path).
     * @return the corresponding tab
     */
    public static SwingScilabDockablePanel createTerminalTab(String uuid) {
        ScilabTerminal tab = new ScilabTerminal(uuid);
        INSTANCES.put(uuid, tab);
        registerShutdownHook();
        WindowsConfigurationManager.restorationFinished(tab);
        return tab;
    }

    /**
     * Open a NEW terminal session in its own dockable window. Entry point for the
     * {@code terminal()} macro; safe to call from the interpreter thread.
     */
    public static void openTerminal() {
        if (SwingUtilities.isEventDispatchThread()) {
            newTerminalSafe();
        } else {
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    newTerminalSafe();
                }
            });
        }
    }

    private static void newTerminalSafe() {
        try {
            lastError = "";
            String id = UUID.randomUUID().toString();
            SwingScilabDockablePanel tab = TerminalTab.getTerminalInstance(id);
            SwingScilabWindow window = SwingScilabWindow.createWindow(true);
            window.addTab(tab);
            window.setLocation(0, 0);
            window.setSize(800, 500);
            window.setVisible(true);
            ScilabGUIUtilities.toFront(tab, TITLE);
        } catch (Throwable t) {
            lastError = String.valueOf(t);
            t.printStackTrace();
        }
    }

    /**
     * @param uuid a tab uuid
     * @return the open terminal for that uuid, or null (used by the tab factory)
     */
    public static ScilabTerminal getTerminal(String uuid) {
        return INSTANCES.get(uuid);
    }

    /**
     * @param uuid a tab uuid
     * @return true if a terminal with that uuid is currently open
     */
    public static boolean isValidUUID(String uuid) {
        return INSTANCES.containsKey(uuid);
    }

    /**
     * Close one terminal tab and tear down its PTY (called when the tab is closed).
     * @param uuid the tab uuid
     */
    public static void closeTerminal(String uuid) {
        ScilabTerminal term = INSTANCES.remove(uuid);
        if (term != null) {
            term.disposeShell();
        }
    }

    /**
     * Tear down every open terminal: SIGHUP/close each PTY so the child shells die
     * and the JediTerm reader threads unblock. Idempotent and thread-safe; invoked
     * from etc/terminal.quit on Scilab shutdown and from the JVM shutdown hook, so
     * live shells never keep the JVM from exiting.
     */
    public static void closeAllTerminals() {
        List<ScilabTerminal> all;
        synchronized (INSTANCES) {
            all = new ArrayList<ScilabTerminal>(INSTANCES.values());
            INSTANCES.clear();
        }
        for (ScilabTerminal term : all) {
            term.disposeShell();
        }
    }

    private void disposeShell() {
        // 1. kill the child shell so the master read returns EOF and JediTerm's
        //    emulator read-loop ends.
        try {
            if (pty != null) {
                pty.terminate();
            }
        } catch (Throwable ignore) { }
        // 2. mark the connector disconnected.
        try {
            if (connector != null) {
                connector.close();
            }
        } catch (Throwable ignore) { }
        // 3. stop the JediTerm session and shut down its per-widget executor;
        //    otherwise its non-daemon pool threads keep the JVM from exiting.
        try {
            if (widget != null) {
                widget.stop();
                widget.close();
                widget.getExecutorServiceManager().shutdownWhenAllExecuted();
            }
        } catch (Throwable ignore) { }
    }

    /**
     * Backstop for a non-graceful exit: ensure child shells are killed and the
     * PTYs released even if etc/terminal.quit did not run. Registered lazily (when
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

    /** @return true if at least one terminal is open (test/introspection hook). */
    public static boolean isTerminalOpen() {
        return !INSTANCES.isEmpty();
    }

    /** @return the number of currently-open terminals (test/introspection hook). */
    public static int terminalCount() {
        return INSTANCES.size();
    }

    /** @return the last error thrown while opening a terminal, or "" if none. */
    public static String getLastError() {
        return lastError;
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
