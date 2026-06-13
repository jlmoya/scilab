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

import org.flexdock.docking.Dockable;
import org.flexdock.docking.DockingConstants;
import org.flexdock.docking.DockingManager;
import org.flexdock.docking.DockingPort;

import org.scilab.modules.action_binding.InterpreterManagement;
import org.scilab.modules.commons.ScilabConstants;
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
import org.scilab.modules.gui.utils.MenuBarBuilder;
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

    // Persistent ids of the default desktop tool tabs, taken verbatim from
    // modules/gui/etc/integratedConfiguration.xml. resetDesktop() re-creates the
    // classic split layout (File Browser west | Console center | Variable Browser /
    // Command History / News feed east) from these, then docks the terminal south
    // of the console.
    private static final String ID_CONSOLE      = "00000000-0000-0000-0000-000000000000";
    private static final String ID_FILEBROWSER  = "3b649047-6a71-4998-bd8e-00d367a4793d";
    private static final String ID_VARBROWSER   = "3b649047-6a71-4998-bd8e-00d367a4793c";
    private static final String ID_CMDHISTORY   = "856207f6-0a60-47a0-b9f4-232feedd4bf4";
    private static final String ID_NEWSFEED     = "DC0957B3-81DA-4E39-B0B5-E93B35412162";

    /** Fraction of the console column given to the terminal docked beneath it. */
    private static final float TERMINAL_SOUTH_RATIO = 0.30f;
    // Target an overall width split of 25% File Browser / 50% Console / 25% east
    // tool column. FlexDock ratios are relative to the region being split: the
    // File Browser takes 25% of the whole port, then the east column takes a third
    // of the remaining 75% (0.25 / 0.75) so the Console keeps the other 50%.
    /** Fraction of the whole width given to the File Browser column (west). */
    private static final float WEST_COLUMN_RATIO = 0.25f;
    /** Fraction of the post-File-Browser width given to the east tool column
     *  (Variable Browser / Command History / News feed); the Console keeps the rest. */
    private static final float EAST_COLUMN_RATIO = 0.3333f;

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
     * Build the terminal tab's menu bar. Uses the full Scilab main menu bar (the
     * same {@code main_menubar.xml} the console builds from) so that focusing the
     * terminal keeps the complete menu (File / Edit / Control / Applications / ?)
     * on the parent window and the macOS screen menu - rather than swapping in a
     * stripped-down menu that only reappears when the console regains focus.
     * Falls back to a minimal File/? menu if the XML cannot be loaded (a menu bar
     * must be non-null, else BarUpdater blanks the window menu when this tab is
     * active).
     */
    private MenuBar createMenuBar() {
        try {
            MenuBar full = MenuBarBuilder.buildMenuBar(ScilabConstants.SCI + "/modules/gui/etc/main_menubar.xml");
            if (full != null) {
                return full;
            }
        } catch (Throwable t) {
            // fall through to the minimal menu bar below
        }
        return createMinimalMenuBar();
    }

    /**
     * A minimal but non-null menu bar (File: Close; Help) - fallback used only if
     * the full main menu bar cannot be built.
     */
    private MenuBar createMinimalMenuBar() {
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
            // Dock the new terminal beneath the console. If a terminal is already
            // open, tab the new one onto it so every session shares one region
            // below the console (like the terminal pane in JetBrains IDEs). Fall
            // back to a fresh window only if the console is not available.
            SwingScilabWindow window = getMainWindow();
            if (window == null) {
                window = SwingScilabWindow.createWindow(true);
                window.setLocation(0, 0);
                window.setSize(800, 500);
                window.setVisible(true);
                window.addTab(tab);
            } else {
                dockTerminal(tab, window);
            }
            ScilabGUIUtilities.toFront(tab, TITLE);
        } catch (Throwable t) {
            lastError = String.valueOf(t);
            t.printStackTrace();
        }
    }

    /**
     * Dock {@code tab} (a terminal) into {@code window}: as a new tab on the
     * existing terminal region if one is open, otherwise as a split south of the
     * console. Called on the EDT.
     */
    private static void dockTerminal(SwingScilabDockablePanel tab, SwingScilabWindow window) {
        SwingScilabDockablePanel existing = firstOpenTerminalExcluding(tab);
        if (existing != null && DockingManager.isDocked((Dockable) existing)) {
            DockingManager.dock((Dockable) tab, (Dockable) existing, DockingConstants.CENTER_REGION);
            return;
        }
        SwingScilabDockablePanel console = findDockedPanel(ID_CONSOLE);
        if (console != null && DockingManager.isDocked((Dockable) console)) {
            DockingManager.dock((Dockable) tab, (Dockable) console, DockingConstants.SOUTH_REGION, TERMINAL_SOUTH_RATIO);
        } else {
            window.addTab(tab);
        }
    }

    /** @return the first open terminal panel other than {@code self}, or null. */
    private static SwingScilabDockablePanel firstOpenTerminalExcluding(SwingScilabDockablePanel self) {
        synchronized (INSTANCES) {
            for (ScilabTerminal t : INSTANCES.values()) {
                if (t != self) {
                    return t;
                }
            }
        }
        return null;
    }

    /**
     * Find the live dockable panel with the given persistent id by scanning every
     * Scilab window's docking port.
     * @return the panel, or null if it is not currently open
     */
    private static SwingScilabDockablePanel findDockedPanel(String persistentId) {
        for (SwingScilabWindow w : new ArrayList<SwingScilabWindow>(SwingScilabWindow.allScilabWindows.values())) {
            if (!(w instanceof SwingScilabDockingWindow)) {
                continue;
            }
            DockingPort port = ((SwingScilabDockingWindow) w).getDockingPort();
            if (port == null) {
                continue;
            }
            for (Object o : port.getDockables()) {
                if (o instanceof SwingScilabDockablePanel
                        && persistentId.equals(((SwingScilabDockablePanel) o).getPersistentId())) {
                    return (SwingScilabDockablePanel) o;
                }
            }
        }
        return null;
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
            if (main == null) {
                return;
            }
            // Close any "Empty tab" placeholders left by failed restores of a
            // stale window layout (e.g. an Xcos diagram whose file is gone), so
            // they are not pulled into the rebuilt layout.
            closeEmptyTabs();
            // Rebuild the classic split layout (File Browser west | Console center
            // | Variable Browser / Command History / News feed east), plus the
            // terminal(s) south of the console.
            rebuildDefaultLayout(main);
            // Guarantee a terminal is shown, docked beneath the console.
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
            // Close any windows left empty after re-docking everything into main.
            closeEmptyDockingWindows(main);
        } catch (Throwable t) {
            lastError = String.valueOf(t);
            t.printStackTrace();
        }
    }

    /**
     * Re-create the default desktop split layout in {@code main} from whichever
     * of the standard tool tabs are currently open, mirroring
     * {@code integratedConfiguration.xml}, then dock every open terminal south of
     * the console (tabbed together). Tabs not currently open are simply skipped;
     * graphics figures are never touched. Must run on the EDT.
     */
    private static void rebuildDefaultLayout(SwingScilabWindow main) {
        SwingScilabDockablePanel console = findDockedPanel(ID_CONSOLE);
        if (console == null) {
            return; // nothing to anchor the layout on
        }
        SwingScilabDockablePanel fileBrowser = findDockedPanel(ID_FILEBROWSER);
        SwingScilabDockablePanel varBrowser  = findDockedPanel(ID_VARBROWSER);
        SwingScilabDockablePanel cmdHistory  = findDockedPanel(ID_CMDHISTORY);
        SwingScilabDockablePanel newsFeed    = findDockedPanel(ID_NEWSFEED);
        List<ScilabTerminal> terminals;
        synchronized (INSTANCES) {
            terminals = new ArrayList<ScilabTerminal>(INSTANCES.values());
        }

        // Pull the movable tabs out so the re-dock yields clean splits rather
        // than stacking them onto whatever port they currently share.
        for (SwingScilabDockablePanel p : new SwingScilabDockablePanel[] { fileBrowser, varBrowser, cmdHistory, newsFeed }) {
            if (p != null) {
                try {
                    DockingManager.undock((Dockable) p);
                } catch (Throwable ignore) { }
            }
        }
        for (ScilabTerminal t : terminals) {
            try {
                DockingManager.undock((Dockable) t);
            } catch (Throwable ignore) { }
        }

        // Console becomes the sole root of the main docking port.
        DockingManager.dock((Dockable) console, main.getDockingPort(), DockingConstants.CENTER_REGION);

        if (fileBrowser != null) {
            DockingManager.dock((Dockable) fileBrowser, (Dockable) console, DockingConstants.WEST_REGION, WEST_COLUMN_RATIO);
        }
        SwingScilabDockablePanel eastAnchor = null;
        if (varBrowser != null) {
            DockingManager.dock((Dockable) varBrowser, (Dockable) console, DockingConstants.EAST_REGION, EAST_COLUMN_RATIO);
            eastAnchor = varBrowser;
        }
        if (cmdHistory != null) {
            if (eastAnchor != null) {
                DockingManager.dock((Dockable) cmdHistory, (Dockable) eastAnchor, DockingConstants.SOUTH_REGION, 0.70f);
            } else {
                DockingManager.dock((Dockable) cmdHistory, (Dockable) console, DockingConstants.EAST_REGION, EAST_COLUMN_RATIO);
            }
            eastAnchor = cmdHistory;
        }
        if (newsFeed != null) {
            if (eastAnchor != null) {
                DockingManager.dock((Dockable) newsFeed, (Dockable) eastAnchor, DockingConstants.SOUTH_REGION, 0.50f);
            } else {
                DockingManager.dock((Dockable) newsFeed, (Dockable) console, DockingConstants.EAST_REGION, EAST_COLUMN_RATIO);
            }
        }

        if (!terminals.isEmpty()) {
            SwingScilabDockablePanel first = terminals.get(0);
            DockingManager.dock((Dockable) first, (Dockable) console, DockingConstants.SOUTH_REGION, TERMINAL_SOUTH_RATIO);
            for (int i = 1; i < terminals.size(); i++) {
                DockingManager.dock((Dockable) terminals.get(i), (Dockable) first, DockingConstants.CENTER_REGION);
            }
        }
    }

    /** Close stray "Empty tab" restore placeholders in every window. */
    private static void closeEmptyTabs() {
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
    }

    /** Close every docking window other than {@code main} that holds no tabs. */
    private static void closeEmptyDockingWindows(SwingScilabWindow main) {
        for (SwingScilabWindow w : new ArrayList<SwingScilabWindow>(SwingScilabWindow.allScilabWindows.values())) {
            if (w == main || !(w instanceof SwingScilabDockingWindow)) {
                continue;
            }
            if (((SwingScilabDockingWindow) w).getNbDockedObjects() == 0) {
                try {
                    w.close();
                } catch (Throwable ignore) { }
            }
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
