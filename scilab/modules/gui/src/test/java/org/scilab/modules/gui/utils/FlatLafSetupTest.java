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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import javax.swing.UIManager;

import org.junit.jupiter.api.Test;

/**
 * Guards the FlatLaf macOS integration.
 *
 * The failure this file exists to catch is SILENT. Scilab only applies a look and
 * feel that passes {@link LookAndFeelManager#isSupportedLookAndFeel(String)}, which
 * tests UIManager's installed list. If {@link FlatLafSetup#install()} stops
 * registering the themes, that check simply returns false, Scilab falls back to the
 * system look and feel, and nothing is logged -- the app just quietly looks the way
 * it did before. No exception, no failing assertion anywhere else.
 */
public class FlatLafSetupTest {

    private static final String LIGHT = "com.formdev.flatlaf.themes.FlatMacLightLaf";
    private static final String DARK  = "com.formdev.flatlaf.themes.FlatMacDarkLaf";

    private static boolean installed(String className) {
        for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
            if (info.getClassName().equals(className)) {
                return true;
            }
        }
        return false;
    }

    @Test
    public void installRegistersBothMacThemesWithUIManager() {
        FlatLafSetup.install();
        assertTrue(installed(LIGHT), LIGHT + " not registered — Scilab would silently keep the system L&F");
        assertTrue(installed(DARK), DARK + " not registered — dark mode could never apply");
    }

    /**
     * install() runs on every startup, so it must tolerate being called repeatedly
     * without multiplying the entries it adds.
     */
    @Test
    public void installIsIdempotent() {
        FlatLafSetup.install();
        int after1 = UIManager.getInstalledLookAndFeels().length;
        FlatLafSetup.install();
        assertEquals(after1, UIManager.getInstalledLookAndFeels().length,
                     "install() must not add duplicate entries when called again");
    }

    /**
     * The registration must be visible THROUGH LookAndFeelManager, not merely present
     * in UIManager: that manager is what Scilab actually consults, and it previously
     * cached the installed list in a static initialised at class-load time, which made
     * anything registered later invisible to it.
     */
    @Test
    public void themesAreVisibleToLookAndFeelManagerAfterInstall() {
        FlatLafSetup.install();
        LookAndFeelManager mgr = new LookAndFeelManager();
        assertTrue(mgr.isSupportedLookAndFeel(LIGHT),
                   "LookAndFeelManager cannot see a theme registered after it was loaded");
        assertTrue(mgr.isSupportedLookAndFeel(DARK),
                   "LookAndFeelManager cannot see a theme registered after it was loaded");
    }


    /**
     * The appearance probe shells out to `defaults`. Whatever it answers, it must
     * answer -- a missing command, a timeout or an interrupt must degrade to Light
     * rather than propagate out of startup over a cosmetic setting.
     */
    @Test
    public void isSystemDarkNeverThrows() {
        assertDoesNotThrow(FlatLafSetup::isSystemDark);
    }


    /**
     * Starting the watcher more than once must not spawn more than one thread.
     */
    @Test
    public void startingTheWatcherTwiceIsSafe() {
        assertDoesNotThrow(() -> {
            FlatLafSetup.startSystemAppearanceWatcher();
            FlatLafSetup.startSystemAppearanceWatcher();
        });
        long watchers = Thread.getAllStackTraces().keySet().stream()
                .filter(t -> "Scilab-macOS-appearance-watcher".equals(t.getName()))
                .count();
        assertTrue(watchers <= 1, "expected at most one watcher thread, found " + watchers);
    }

    // NOTE: appearanceMode() and preferredLookAndFeel() are deliberately NOT covered
    // here. They read the preferences through XConfiguration, which calls into
    // Scilab's JNI layer; in a hermetic test JVM that layer is absent and the process
    // aborts NATIVELY (exit 134), which no try/catch can intercept. They are exercised
    // against the running application instead.
}
