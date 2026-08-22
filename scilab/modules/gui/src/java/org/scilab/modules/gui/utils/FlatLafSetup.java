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
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import org.flexdock.plaf.PlafManager;

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

    /** @return true if UIManager already lists this look and feel. */
    private static boolean isRegistered(String className) {
        for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
            if (info.getClassName().equals(className)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return the FlatLaf class name matching the current macOS appearance.
     */
    public static String preferredLookAndFeel() {
        lastKnownDark = isSystemDark();
        return lastKnownDark ? DARK : LIGHT;
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

            for (Window w : Window.getWindows()) {
                SwingUtilities.updateComponentTreeUI(w);
            }
        } catch (Exception e) {
            // A failed restyle is cosmetic. Report it and keep the previous theme
            // rather than letting it propagate into the EDT's uncaught handler.
            System.err.println("Could not switch to " + laf + ": " + e);
        }
    }
}
