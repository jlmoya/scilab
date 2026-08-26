/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 *
 */

package org.scilab.modules.gui.utils;

import java.awt.Window;
import javax.swing.JSplitPane;
import java.util.Map;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.Collections;
import java.util.WeakHashMap;
import java.util.HashMap;
import java.awt.Rectangle;
import java.awt.Container;
import java.awt.Component;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;

import org.flexdock.plaf.PlafManager;

import java.util.Set;
import org.scilab.modules.commons.xml.XConfigurationEvent;
import org.scilab.modules.commons.xml.XConfigurationListener;
import org.scilab.modules.commons.xml.XConfiguration;
import org.scilab.modules.console.ConsoleOptions;

import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import com.formdev.flatlaf.themes.FlatMacLightLaf;

/**
 * macOS FlatLaf integration: pick the macOS-styled FlatLaf theme that matches the
 * system Light/Dark appearance, and keep following it while Scilab runs.
 *
 * WHY THE APPEARANCE IS READ FROM A SUBPROCESS. The JDK exposes no macOS
 * appearance information to Java: on JDK 25 the desktop properties
 * {@code apple.awt.application.appearance} and {@code awt.os.theme.isDark} are both
 * null, with AWT fully initialised, a real window on screen, and
 * {@code -Dapple.awt.application.appearance=system} set. FlatLaf's own native macOS
 * library does not help either -- it covers window decorations
 * (setWindowRoundedBorder / setWindowButtonsSpacing / setWindowAppearance), not
 * reading the system setting. So {@code defaults read -g AppleInterfaceStyle} is the
 * available signal: it prints "Dark" and exits 0 in Dark mode, and exits non-zero
 * with the key absent in Light mode.
 *
 * That command is cheap but it is still a process, so it is never run on the Event
 * Dispatch Thread and never more often than {@link #POLL_SECONDS}.
 *
 * WHY POLLING RATHER THAN A NOTIFICATION. macOS broadcasts appearance changes
 * through NSDistributedNotificationCenter, which is not reachable from Java without
 * native code. Polling every few seconds is the honest trade: a change applies
 * within a few seconds instead of instantly, for no native dependency.
 */
public final class FlatLafSetup {

    /** How often the watcher re-reads the system appearance. */
    private static final int POLL_SECONDS = 4;

    private static final String LIGHT = "com.formdev.flatlaf.themes.FlatMacLightLaf";
    private static final String DARK  = "com.formdev.flatlaf.themes.FlatMacDarkLaf";

    private static volatile boolean watcherStarted = false;
    private static volatile boolean listenerInstalled = false;
    private static volatile boolean lastKnownDark  = false;

    private FlatLafSetup() { }

    /** @return true when running on macOS. */
    public static boolean isMacOS() {
        return System.getProperty("os.name", "").toLowerCase().startsWith("mac");
    }

    /**
     * Register the macOS FlatLaf themes with UIManager.
     *
     * This must happen BEFORE anything consults
     * {@code UIManager.getInstalledLookAndFeels()}, because that is what
     * {@link LookAndFeelManager#isSupportedLookAndFeel(String)} tests against.
     * Without it, asking for a FlatLaf class name fails that test and Scilab
     * silently falls back to the system look and feel -- the theme would simply
     * never appear, with nothing logged.
     */
    public static void install() {
        // installLafInfo() APPENDS unconditionally -- calling it twice puts the same
        // theme in UIManager's list twice, which would surface as duplicated entries
        // in any look-and-feel chooser. Register each theme only if it is not there
        // already, so repeated calls are harmless.
        if (!isRegistered(LIGHT)) {
            FlatMacLightLaf.installLafInfo();
        }
        if (!isRegistered(DARK)) {
            FlatMacDarkLaf.installLafInfo();
        }
    }

    /**
     * Re-apply the theme as soon as the Appearance setting is changed in
     * Preferences, rather than at the next start. Registered once.
     *
     * Deliberately NOT called from install(): touching XConfiguration loads a class
     * whose static initialiser reaches Scilab's JNI layer, and where that layer is
     * absent the process aborts natively rather than throwing. install() must stay
     * usable without a live Scilab, so this is called separately from startup, next
     * to the appearance watcher.
     */
    public static void installPreferenceListener() {
        if (listenerInstalled) {
            return;
        }
        listenerInstalled = true;
        SwingUtilities.invokeLater(FlatLafSetup::trackWindowSizes);
        XConfiguration.addXConfigurationListener(new XConfigurationListener() {
            @Override
            public void configurationChanged(XConfigurationEvent e) {
                Set<String> paths = e.getModifiedPaths();
                if (paths == null) {
                    return;
                }
                for (String path : paths) {
                    // The console announces this same path when IT refreshes colours,
                    // and applyLookAndFeel announces it in turn. Comparing the
                    // resolved theme against the one already in force keeps that from
                    // becoming a loop: a repeat notification resolves to the current
                    // look and feel and is dropped here.
                    if (path != null && path.contains(APPEARANCE_PATH)) {
                        String wanted = preferredLookAndFeel();
                        if (!wanted.equals(UIManager.getLookAndFeel().getClass().getName())) {
                            applyAppearancePreference();
                        }
                        return;
                    }
                }
            }
        });
    }

    /** @return true if UIManager already lists this look and feel. */
    private static boolean isRegistered(String className) {
        for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
            if (info.getClassName().equals(className)) {
                return true;
            }
        }
        return false;
    }

    /** Preferences path holding the appearance choice. */
    private static final String APPEARANCE_PATH = "//colors/body/desktop-colors";

    /**
     * The user's Appearance setting: "light", "dark" or "auto".
     *
     * Defaults to auto, which is also what a configuration saved BEFORE this setting
     * existed yields: XConfiguration hands back an empty string for an absent
     * attribute rather than failing, so old preference files keep working untouched.
     * That mattered here -- the alternative, declaring the attribute properly and
     * bumping the configuration version, makes the loader delete the user's whole
     * preferences file.
     */
    public static String appearanceMode() {
        try {
            Document doc = XConfiguration.getXConfigurationDocument();
            if (doc != null) {
                Node node = XPathFactory.newInstance().newXPath()
                            .evaluateExpression(APPEARANCE_PATH, doc, Node.class);
                if (node != null && node.getAttributes() != null) {
                    Node attr = node.getAttributes().getNamedItem("appearance");
                    if (attr != null) {
                        String v = attr.getNodeValue();
                        if ("light".equalsIgnoreCase(v) || "dark".equalsIgnoreCase(v)) {
                            return v.toLowerCase();
                        }
                    }
                }
            }
        } catch (Throwable t) {
            // A preferences problem must never stop Scilab starting over a theme.
        }
        return "auto";
    }

    /**
     * @return the FlatLaf class name for the current Appearance setting: the user's
     *         explicit choice, or the system appearance when set to auto.
     */
    public static String preferredLookAndFeel() {
        String mode = appearanceMode();
        if ("light".equals(mode)) {
            lastKnownDark = false;
        } else if ("dark".equals(mode)) {
            lastKnownDark = true;
        } else {
            lastKnownDark = isSystemDark();
        }
        return lastKnownDark ? DARK : LIGHT;
    }

    /**
     * Apply the Appearance setting now, e.g. after the user changed it in
     * Preferences. Safe to call on any thread.
     */
    public static void applyAppearancePreference() {
        final String laf = preferredLookAndFeel();
        if (SwingUtilities.isEventDispatchThread()) {
            applyLookAndFeel(laf);
        } else {
            SwingUtilities.invokeLater(() -> applyLookAndFeel(laf));
        }
    }

    /**
     * Read the macOS appearance. Returns false (Light) for every failure mode --
     * command missing, timeout, interruption -- because a wrong-but-usable light
     * theme beats failing startup over a cosmetic setting.
     */
    public static boolean isSystemDark() {
        if (!isMacOS()) {
            return false;
        }
        Process p = null;
        try {
            p = new ProcessBuilder("defaults", "read", "-g", "AppleInterfaceStyle")
                    .redirectErrorStream(true)
                    .start();
            String out;
            try (BufferedReader r = new BufferedReader(
                     new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                out = r.readLine();
            }
            if (!p.waitFor(3, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return false;
            }
            // Light mode: the key does not exist, so defaults exits non-zero and
            // prints a "does not exist" diagnostic -- which is why the exit code is
            // checked and the text is only trusted when the command succeeded.
            return p.exitValue() == 0 && out != null && out.trim().equalsIgnoreCase("Dark");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            return false;
        } finally {
            if (p != null) {
                p.destroy();
            }
        }
    }

    /**
     * Follow later system Light/Dark changes. Safe to call more than once; only the
     * first call starts the thread. Does nothing off macOS.
     */
    public static void startSystemAppearanceWatcher() {
        if (!isMacOS() || watcherStarted) {
            return;
        }
        watcherStarted = true;

        Thread t = new Thread(() -> {
            while (true) {
                try {
                    TimeUnit.SECONDS.sleep(POLL_SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                if (!"auto".equals(appearanceMode())) {
                    continue;
                }
                // Keep the size record current, including for windows opened after
                // startup. Cheap: it only adds a listener the first time it sees one.
                SwingUtilities.invokeLater(FlatLafSetup::trackWindowSizes);

                // An explicit Light or Dark choice is not a preference to be
                // overridden by the machine: only follow the system in auto mode.
                boolean darkNow = isSystemDark();
                if (darkNow == lastKnownDark) {
                    continue;
                }
                lastKnownDark = darkNow;
                final String laf = darkNow ? DARK : LIGHT;
                SwingUtilities.invokeLater(() -> applyLookAndFeel(laf));
            }
        }, "Scilab-macOS-appearance-watcher");
        // daemon: a cosmetic watcher must never be the reason the JVM stays alive
        t.setDaemon(true);
        t.start();
    }

    /**
     * Swap the look and feel and restyle every existing window. Must run on the EDT.
     */

    private static void applyLookAndFeel(String laf) {
        // Raise the guard BEFORE anything touches the UI. It used to go up only just
        // before the component-tree update, which left everything above it -- notably
        // the FlexDock view refresh -- free to resize windows while the size tracker
        // was still listening. Those resizes were then recorded as the USER's chosen
        // size, so the restore below dutifully put the window back to the collapsed
        // geometry it had just been given. The guard has to cover the whole switch.
        restoring = true;
        try {
            UIManager.setLookAndFeel(laf);

            // Re-resolve FlexDock's theme BEFORE restyling the trees. FlexDock maps
            // the look and feel to a docking theme in modules/gui/etc/flexdock-themes.xml
            // (light and dark map to different titlebar colours), and it resolves that
            // mapping once, at install time. Without this the panels would keep the
            // previous theme's titlebars: switching to dark would leave near-white
            // titlebars on a dark window.
            try {
                PlafManager.installSystemTheme();
                // installSystemTheme() changes which theme is CURRENT but does not
                // rebuild the titlebar UIs already installed on existing views, so the
                // docked panels keep the previous theme's titlebars. Titlebar.updateUI()
                // re-fetches from PlafManager, so ask each view for it explicitly.
                for (Window w : Window.getWindows()) {
                    refreshDockViews(w);
                }
            } catch (Throwable t) {
                System.err.println("Could not re-apply the docking theme: " + t);
            }

            // The console does not take its colours from the look and feel directly:
            // it caches them in ConsoleOptions and only re-reads on a configuration
            // event. Without this the console would keep the previous theme's colours
            // after a live switch -- a white console inside a dark window, which is the
            // single most jarring part of getting this wrong.
            //
            // Only the colours path is announced, so the console re-applies exactly
            // foreground/background/caret and nothing else (fonts and display settings
            // are untouched).
            try {
                XConfiguration.addModifiedPath(ConsoleOptions.COLORSPATH);
                XConfiguration.fireXConfigurationEvent();
            } catch (Throwable t) {
                System.err.println("Could not refresh the console colours: " + t);
            }

            // updateComponentTreeUI() rebuilds every UI delegate, which recomputes
            // preferred sizes. In a docked layout that is destructive: the split panes
            // redistribute and the window collapses, losing both its size and the
            // proportions the user arranged. Capture the geometry first and put it
            // back afterwards, so a theme change is only a theme change.
            // Take the geometry from the CONTINUOUSLY TRACKED record, not from the
            // windows as they are right now.
            //
            // Measured: by the time this method runs, the main window has already been
            // shrunk to SwingScilabWindow's 500x500 construction default by something
            // earlier in the Apply chain -- before any of this executes. Snapshotting
            // here therefore recorded the ALREADY BROKEN size and faithfully restored
            // it, which is why an earlier attempt at this had no visible effect.
            //
            // trackWindow() keeps the last size the user themselves set, so the size
            // restored below is the one they chose rather than whatever the preference
            // machinery left behind.
            Map<Window, Rectangle> bounds = new HashMap<Window, Rectangle>();
            Map<JSplitPane, Integer> dividers = new HashMap<JSplitPane, Integer>();
            for (Window w : Window.getWindows()) {
                Rectangle tracked = userBounds.get(w);
                bounds.put(w, tracked != null ? tracked : w.getBounds());
                collectDividers(w, dividers);
            }

            for (Window w : Window.getWindows()) {
                SwingUtilities.updateComponentTreeUI(w);
            }

            for (Map.Entry<Window, Rectangle> e : bounds.entrySet()) {
                if (!e.getKey().getBounds().equals(e.getValue())) {
                    e.getKey().setBounds(e.getValue());
                }
            }
            // Dividers are restored after the bounds, and again on a later event, because
            // the layout pass triggered by setBounds can move them a second time.
            restoreDividers(dividers);
            SwingUtilities.invokeLater(() -> {
                for (Map.Entry<Window, Rectangle> e : bounds.entrySet()) {
                    if (!e.getKey().getBounds().equals(e.getValue())) {
                        e.getKey().setBounds(e.getValue());
                    }
                }
                restoreDividers(dividers);
                for (Window w : bounds.keySet()) {
                    w.validate();
                }
                restoring = false;
            });
        } catch (Exception e) {
            // A failed restyle is cosmetic. Report it and keep the previous theme
            // rather than letting it propagate into the EDT's uncaught handler.
            restoring = false;
            System.err.println("Could not switch to " + laf + ": " + e);
        }
    }

    /** Record every split-pane divider position under this container. */
    private static void collectDividers(Container root, Map<JSplitPane, Integer> out) {
        for (Component c : root.getComponents()) {
            if (c instanceof JSplitPane) {
                JSplitPane sp = (JSplitPane) c;
                out.put(sp, sp.getDividerLocation());
            }
            if (c instanceof Container) {
                collectDividers((Container) c, out);
            }
        }
    }

    private static void restoreDividers(Map<JSplitPane, Integer> dividers) {
        for (Map.Entry<JSplitPane, Integer> e : dividers.entrySet()) {
            JSplitPane sp = e.getKey();
            if (sp.isShowing() && sp.getDividerLocation() != e.getValue()) {
                sp.setDividerLocation(e.getValue());
            }
        }
    }




    /** Last size the USER gave each window, used to undo an unwanted resize. */
    private static final Map<Window, Rectangle> userBounds =
        Collections.synchronizedMap(new WeakHashMap<Window, Rectangle>());

    /** How long a size must hold before it counts as deliberate. */
    private static final int SETTLE_MS = 900;

    /** True while a theme switch is restoring geometry, so it is not recorded as user intent. */
    private static volatile boolean restoring = false;

    /**
     * Start remembering the size of every current window, and keep it up to date.
     *
     * Resizes made WHILE a theme switch is in progress are ignored: those are the ones
     * being corrected, and recording them would make the bad size the new baseline.
     */
    public static void trackWindowSizes() {
        for (final Window w : Window.getWindows()) {
            if (userBounds.containsKey(w)) {
                continue;
            }
            userBounds.put(w, w.getBounds());
            // Commit the size only once it has SETTLED.
            //
            // A guard flag alone cannot work here: the window is already collapsed
            // before applyLookAndFeel is entered -- something earlier in the Apply
            // chain does it, and this listener is notified LAST (listeners fire in
            // reverse registration order), so any flag raised in it is raised too
            // late and the collapsed size gets recorded as the user's choice.
            //
            // A transient size is not a choice. The commit is therefore deferred, and
            // it re-reads the window when it fires rather than trusting the event: a
            // programmatic collapse that is corrected milliseconds later never
            // survives to be recorded, while a size the user actually dragged does.
            final Timer commit = new Timer(SETTLE_MS, null);
            commit.setRepeats(false);
            commit.addActionListener(ev -> {
                if (!restoring && w.isShowing()) {
                    userBounds.put(w, w.getBounds());
                }
            });
            ComponentAdapter adapter = new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    commit.restart();
                }

                @Override
                public void componentMoved(ComponentEvent e) {
                    commit.restart();
                }
            };
            w.addComponentListener(adapter);
        }
    }


    /** Re-fetch the titlebar UI of every FlexDock view under this container. */
    private static void refreshDockViews(Container root) {
        for (Component c : root.getComponents()) {
            if (c instanceof org.flexdock.view.View) {
                org.flexdock.view.View v = (org.flexdock.view.View) c;
                // Deliberately NOT v.updateUI(). Measured: calling it collapses the
                // whole window to SwingScilabWindow's 500x500 construction default,
                // because rebuilding the view's UI re-runs the docking layout. The
                // geometry timeline showed the window going 1366x768 -> 500x500
                // between entering the switch and finishing this loop, and back only
                // once the restore ran. The titlebar is the only part that needs the
                // new theme, so refresh just that and leave the layout alone.
                if (v.getTitlebar() != null) {
                    v.getTitlebar().updateUI();
                    v.getTitlebar().revalidate();
                    v.getTitlebar().repaint();
                }
                // Each dockable owns a menubar/toolbar/infobar that Scilab swaps in and
                // out as tabs are activated. A bar that is DETACHED at this moment is
                // not part of any window's component tree, so updateComponentTreeUI
                // never sees it and it keeps the previous theme until it is next shown
                // -- which is why the toolbar came back light on a dark window. Restyle
                // them from the dockable that holds them, attached or not.
                if (c instanceof org.scilab.modules.gui.bridge.tab.SwingScilabDockablePanel) {
                    org.scilab.modules.gui.bridge.tab.SwingScilabDockablePanel p =
                        (org.scilab.modules.gui.bridge.tab.SwingScilabDockablePanel) c;
                    for (Object bar : new Object[] {p.getMenuBar(), p.getToolBar(), p.getInfoBar()}) {
                        updateDetachedBar(bar);
                    }
                }
            }
            if (c instanceof Container) {
                refreshDockViews((Container) c);
            }
        }
    }


    /**
     * Restyle a menubar/toolbar/infobar that may not currently be in a window tree.
     *
     * These arrive as Scilab wrapper objects rather than Swing components, so the
     * underlying JComponent is reached through the wrapper's own accessor when it has
     * one; anything that does not yield a JComponent is skipped rather than guessed at.
     */
    private static void updateDetachedBar(Object bar) {
        if (bar == null) {
            return;
        }
        java.awt.Component comp = null;
        if (bar instanceof java.awt.Component) {
            comp = (java.awt.Component) bar;
        } else {
            for (String getter : new String[] {"getAsSimpleMenuBar", "getAsSimpleToolBar",
                                               "getAsSimpleTextBox", "getSwingComponent"}) {
                try {
                    Object o = bar.getClass().getMethod(getter).invoke(bar);
                    if (o instanceof java.awt.Component) {
                        comp = (java.awt.Component) o;
                        break;
                    }
                } catch (Exception ignored) {
                    // try the next accessor
                }
            }
        }
        if (comp != null) {
            SwingUtilities.updateComponentTreeUI(comp);
            comp.repaint();
        }
    }

}
