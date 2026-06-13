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
import java.awt.Font;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import com.jediterm.terminal.TerminalExecutorServiceManager;
import com.jediterm.terminal.ui.JediTermWidget;
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider;
import com.jediterm.terminal.ui.settings.SettingsProvider;

import org.flexdock.docking.DockingPort;

import org.scilab.modules.action_binding.InterpreterManagement;
import org.scilab.modules.commons.gui.ScilabGUIUtilities;
import org.scilab.modules.gui.bridge.console.SwingScilabConsole;
import org.scilab.modules.gui.bridge.tab.SwingScilabDockablePanel;
import org.scilab.modules.gui.bridge.window.SwingScilabDockingWindow;
import org.scilab.modules.gui.bridge.window.SwingScilabWindow;
import org.scilab.modules.gui.console.ScilabConsole;
import org.scilab.modules.gui.events.callback.CommonCallBack;
import org.scilab.modules.gui.menu.Menu;
import org.scilab.modules.gui.menu.ScilabMenu;
import org.scilab.modules.gui.menubar.MenuBar;
import org.scilab.modules.gui.menubar.ScilabMenuBar;
import org.scilab.modules.gui.menuitem.MenuItem;
import org.scilab.modules.gui.menuitem.ScilabMenuItem;
import org.scilab.modules.gui.tab.SimpleTab;
import org.scilab.modules.gui.tabfactory.ScilabTabFactory;
import org.scilab.modules.gui.textbox.ScilabTextBox;
import org.scilab.modules.gui.textbox.TextBox;
import org.scilab.modules.gui.toolbar.ScilabToolBar;
import org.scilab.modules.gui.toolbar.ToolBar;
import org.scilab.modules.gui.utils.ClosingOperationsManager;
import org.scilab.modules.gui.utils.WindowsConfigurationManager;
import org.scilab.modules.localization.Messages;

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
        setAssociatedXMLIDForHelp("terminal");
        // A tab MUST supply non-null menu/tool bars: when it becomes the active
        // dockable, BarUpdater installs them on the parent window (the macOS screen
        // menu bar). Without them the application's menus blank out.
        addMenuBar(createMenuBar());
        addToolBar(ScilabToolBar.createToolBar());
        addInfoBar(ScilabTextBox.createTextBox());

        TerminalOptions.TerminalSettings settings = TerminalOptions.getSettings();
        widget = new DaemonJediTermWidget(DEFAULT_COLS, DEFAULT_ROWS,
                                          new TerminalSettingsProvider(TerminalOptions.getFont(),
                                                  settings.scrollback, settings.audibleBell));
        startShell(settings);
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
    private void startShell(TerminalOptions.TerminalSettings settings) {
        String shell = settings.shell;
        if (shell == null || shell.isEmpty()) {
            shell = System.getenv("SHELL");
        }
        if (shell == null || shell.isEmpty()) {
            shell = "/bin/bash";
        }

        Map<String, String> env = new LinkedHashMap<String, String>(System.getenv());
        env.put("TERM", "xterm-256color");
        if (env.get("LANG") == null) {
            env.put("LANG", "en_US.UTF-8");
        }
        // Scilab sets these so its own embedded JVM is not headless; do not leak
        // them to the shell or every java/sdkman/jenv invocation in the terminal
        // prints "Picked up _JAVA_OPTIONS ...".
        env.remove("_JAVA_OPTIONS");
        env.remove("JAVA_TOOL_OPTIONS");
        List<String> envp = new ArrayList<String>();
        for (Map.Entry<String, String> e : env.entrySet()) {
            envp.add(e.getKey() + "=" + e.getValue());
        }

        pty = new Pty();
        try {
            pty.start(shell, new String[] {shell, "-l", "-i"},
                      envp.toArray(new String[0]), DEFAULT_ROWS, DEFAULT_COLS, settings.startDir);
        } catch (java.io.IOException ex) {
            throw new RuntimeException("Could not start terminal shell '" + shell + "': " + ex.getMessage(), ex);
        }
        connector = new PtyTtyConnector(pty);
    }

    /**
     * A minimal but non-null menu bar (File: Close; Help) - required so the tab,
     * when active, does not blank the parent window's / macOS screen menu bar.
     */
    private MenuBar createMenuBar() {
        MenuBar menuBar = ScilabMenuBar.createMenuBar();

        Menu fileMenu = ScilabMenu.createMenu();
        fileMenu.setText(Messages.gettext("File"));
        fileMenu.setMnemonic('F');
        MenuItem closeItem = ScilabMenuItem.createMenuItem();
        closeItem.setText(Messages.gettext("Close"));
        closeItem.setCallback(new CommonCallBack("") {
            private static final long serialVersionUID = 1L;
            @Override
            public void callBack() {
                ClosingOperationsManager.startClosingOperation(ScilabTerminal.this);
            }
        });
        fileMenu.add(closeItem);
        menuBar.add(fileMenu);

        Menu helpMenu = ScilabMenu.createMenu();
        helpMenu.setText(Messages.gettext("?"));
        helpMenu.setMnemonic('?');
        MenuItem helpItem = ScilabMenuItem.createMenuItem();
        helpItem.setText(Messages.gettext("Terminal"));
        helpItem.setCallback(new CommonCallBack("") {
            private static final long serialVersionUID = 1L;
            @Override
            public void callBack() {
                InterpreterManagement.requestScilabExec("help terminal");
            }
        });
        helpMenu.add(helpItem);
        menuBar.add(helpMenu);

        return menuBar;
    }

    /**
     * JediTerm settings backed by the Terminal preferences pane: configured font,
     * scrollback (buffer max lines) and audible-bell.
     */
    private static final class TerminalSettingsProvider extends DefaultSettingsProvider {

        private static final int FALLBACK_SCROLLBACK = 5000;

        private final Font font;
        private final int scrollback;
        private final boolean bell;

        TerminalSettingsProvider(Font font, int scrollback, boolean bell) {
            this.font = font;
            this.scrollback = scrollback > 0 ? scrollback : FALLBACK_SCROLLBACK;
            this.bell = bell;
        }

        @Override
        public Font getTerminalFont() {
            return font;
        }

        @Override
        public float getTerminalFontSize() {
            return font.getSize2D();
        }

        @Override
        public int getBufferMaxLinesCount() {
            return scrollback;
        }

        @Override
        public boolean audibleBell() {
            return bell;
        }
    }

    /**
     * JediTerm widget whose per-widget executor uses DAEMON threads. The embedded
     * Scilab JVM exits via DestroyJavaVM, which waits for non-daemon threads; with
     * daemon threads a live terminal never blocks Scilab from quitting (and when
     * the process exits the PTY master closes, so the child shell gets SIGHUP and
     * dies - no orphan), independent of whether the quit/close teardown ran.
     */
    private static final class DaemonJediTermWidget extends JediTermWidget {

        DaemonJediTermWidget(int cols, int rows, SettingsProvider settings) {
            super(cols, rows, settings);
        }

        @Override
        protected TerminalExecutorServiceManager createExecutorServiceManager() {
            return new DaemonExecutorServiceManager();
        }
    }

    private static final class DaemonExecutorServiceManager implements TerminalExecutorServiceManager {

        private final ScheduledExecutorService scheduled =
            Executors.newSingleThreadScheduledExecutor(daemonFactory("Scilab-TerminalEmulator"));
        private final ExecutorService unbounded =
            Executors.newCachedThreadPool(daemonFactory("Scilab-JediTerm-job"));

        @Override
        public ScheduledExecutorService getSingleThreadScheduledExecutor() {
            return scheduled;
        }

        @Override
        public ExecutorService getUnboundedExecutorService() {
            return unbounded;
        }

        @Override
        public void shutdownWhenAllExecuted() {
            scheduled.shutdown();
            unbounded.shutdown();
        }

        private static ThreadFactory daemonFactory(final String prefix) {
            return new ThreadFactory() {
                private int n = 0;
                public synchronized Thread newThread(Runnable r) {
                    Thread t = new Thread(r, prefix + "-" + (n++));
                    t.setDaemon(true);
                    return t;
                }
            };
        }
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
            // Open docked into the main (console) window so it appears as a tab next
            // to the console - the console tab (and its full menu bar) stays one click
            // away. Fall back to a fresh window if the console is not available.
            SwingScilabWindow window = getMainWindow();
            if (window == null) {
                window = SwingScilabWindow.createWindow(true);
                window.setLocation(0, 0);
                window.setSize(800, 500);
                window.setVisible(true);
            }
            window.addTab(tab);
            ScilabGUIUtilities.toFront(tab, TITLE);
        } catch (Throwable t) {
            lastError = String.valueOf(t);
            t.printStackTrace();
        }
    }

    /**
     * @return the main Scilab window (the one hosting the console), or null if the
     * console is not up (e.g. -nw mode).
     */
    private static SwingScilabWindow getMainWindow() {
        try {
            SwingScilabConsole console = (SwingScilabConsole) ScilabConsole.getConsole().getAsSimpleConsole();
            SwingScilabDockablePanel consoleTab = (SwingScilabDockablePanel) console.getParent();
            return SwingScilabWindow.allScilabWindows.get(consoleTab.getParentWindowId());
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Re-dock every tool tab (Variable Browser, File Browser, Command History,
     * SciNotes, Terminal, ...) into the main window and make sure a terminal is
     * shown there, so the user can recover from a scattered/undockable desktop.
     * Graphics figures are left untouched. Entry point for the resetdesktop()
     * command and the Applications -&gt; Reset Desktop menu item.
     */
    public static void resetDesktop() {
        if (SwingUtilities.isEventDispatchThread()) {
            resetDesktopSafe();
        } else {
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    resetDesktopSafe();
                }
            });
        }
    }

    private static void resetDesktopSafe() {
        try {
            lastError = "";
            SwingScilabWindow main = getMainWindow();
            if (main != null) {
                for (SwingScilabWindow w : new ArrayList<SwingScilabWindow>(SwingScilabWindow.allScilabWindows.values())) {
                    if (w == main || !(w instanceof SwingScilabDockingWindow)) {
                        continue;
                    }
                    DockingPort port = ((SwingScilabDockingWindow) w).getDockingPort();
                    if (port == null) {
                        continue;
                    }
                    for (Object o : new ArrayList<Object>(port.getDockables())) {
                        if (o instanceof SwingScilabDockablePanel && !isGraphicsTab((SwingScilabDockablePanel) o)) {
                            try {
                                main.addTab((SwingScilabDockablePanel) o);
                            } catch (Throwable ignore) { }
                        }
                    }
                    if (((SwingScilabDockingWindow) w).getNbDockedObjects() == 0) {
                        try {
                            w.close();
                        } catch (Throwable ignore) { }
                    }
                }
            }
            // Close any "Empty tab" placeholders left by failed restores of a
            // stale window layout (e.g. an Xcos diagram whose file is gone).
            String emptyName = Messages.gettext("Empty tab");
            for (SwingScilabWindow w : new ArrayList<SwingScilabWindow>(SwingScilabWindow.allScilabWindows.values())) {
                if (!(w instanceof SwingScilabDockingWindow)) {
                    continue;
                }
                DockingPort p = ((SwingScilabDockingWindow) w).getDockingPort();
                if (p == null) {
                    continue;
                }
                for (Object o : new ArrayList<Object>(p.getDockables())) {
                    if (o instanceof SwingScilabDockablePanel
                            && emptyName.equals(((SwingScilabDockablePanel) o).getName())) {
                        try {
                            ClosingOperationsManager.startClosingOperationWithoutSave((SwingScilabDockablePanel) o);
                        } catch (Throwable ignore) { }
                    }
                }
            }

            // Make sure a terminal is shown, docked in the main window.
            ScilabTerminal term = null;
            synchronized (INSTANCES) {
                for (ScilabTerminal t : INSTANCES.values()) {
                    term = t;
                    break;
                }
            }
            if (term == null) {
                newTerminalSafe();
            } else {
                ScilabGUIUtilities.toFront(term, TITLE);
            }
        } catch (Throwable t) {
            lastError = String.valueOf(t);
            t.printStackTrace();
        }
    }

    private static boolean isGraphicsTab(SwingScilabDockablePanel tab) {
        return tab.getClass().getName().contains("SwingScilabAxes");
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
        // 3. stop the JediTerm session and shut down its per-widget executor.
        try {
            if (widget != null) {
                try {
                    widget.getTerminalStarter().requestEmulatorStop();
                } catch (Throwable ignore) { }
                widget.stop();
                widget.close();
                widget.getExecutorServiceManager().getSingleThreadScheduledExecutor().shutdownNow();
                widget.getExecutorServiceManager().getUnboundedExecutorService().shutdownNow();
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
