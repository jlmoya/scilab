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

package org.scilab.modules.gui.utils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import javax.swing.LookAndFeel;
import javax.swing.UIManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link LookAndFeelManager}.
 *
 * <p>The class talks only to {@link javax.swing.UIManager} and
 * {@link javax.swing.SwingUtilities}; it needs no Scilab native runtime and
 * works headless (setting a look-and-feel only rewrites the shared UI defaults,
 * it creates no display peers).</p>
 *
 * <p>The current look-and-feel is process-global mutable state, so every test
 * that changes it is bracketed by a save in {@link #rememberLookAndFeel()} and a
 * best-effort restore in {@link #restoreLookAndFeel()}, keeping the tests
 * order-independent.</p>
 */
class LookAndFeelManagerTest {

    /** Always installed on every standard JVM, and always settable (pure Java). */
    private static final String METAL = "javax.swing.plaf.metal.MetalLookAndFeel";

    private String originalLookAndFeel;

    @BeforeEach
    void rememberLookAndFeel() {
        originalLookAndFeel = UIManager.getLookAndFeel().getClass().getName();
    }

    @AfterEach
    void restoreLookAndFeel() {
        try {
            UIManager.setLookAndFeel(originalLookAndFeel);
        } catch (Exception ignored) {
            // Best-effort restore; must never mask the actual test outcome.
        }
    }

    // ---- Constructor ----------------------------------------------------

    @Test
    void constructorCreatesInstanceBackedByTheSharedInstalledList() {
        LookAndFeelManager mgr = new LookAndFeelManager();
        assertNotNull(mgr);
        assertTrue(mgr.numbersOfInstalledLookAndFeels() >= 1);
    }

    // ---- getInstalledLookAndFeels / numbersOfInstalledLookAndFeels ------

    @Test
    void installedListIsNonEmptyAndFullyPopulated() {
        String[] names = new LookAndFeelManager().getInstalledLookAndFeels();
        assertNotNull(names);
        assertTrue(names.length >= 1, "the JVM always ships at least one installed look-and-feel");
        for (String name : names) {
            assertNotNull(name);
            assertFalse(name.isEmpty());
        }
    }

    @Test
    void countMatchesInstalledArrayLength() {
        LookAndFeelManager mgr = new LookAndFeelManager();
        assertEquals(mgr.getInstalledLookAndFeels().length, mgr.numbersOfInstalledLookAndFeels());
        assertTrue(mgr.numbersOfInstalledLookAndFeels() >= 1);
    }

    /**
     * Each call builds a fresh array (defensive against callers mutating it) but
     * the contents are stable because they are copied from the shared static
     * {@code availableLookAndFeels}.
     */
    @Test
    void getInstalledLookAndFeelsReturnsAFreshArrayWithStableContents() {
        LookAndFeelManager mgr = new LookAndFeelManager();
        String[] a = mgr.getInstalledLookAndFeels();
        String[] b = mgr.getInstalledLookAndFeels();
        assertNotSame(a, b);
        assertArrayEquals(a, b);
    }

    @Test
    void everyInstanceReportsTheSameInstalledSet() {
        assertArrayEquals(new LookAndFeelManager().getInstalledLookAndFeels(),
                          new LookAndFeelManager().getInstalledLookAndFeels());
    }

    // ---- getCurrentLookAndFeel ------------------------------------------

    @Test
    void currentLookAndFeelIsAQualifiedClassName() {
        String current = new LookAndFeelManager().getCurrentLookAndFeel();
        assertNotNull(current);
        assertFalse(current.isEmpty());
        assertTrue(current.contains("."), "expected a fully-qualified class name, got: " + current);
    }

    @Test
    void currentLookAndFeelNamesALoadableLookAndFeelClass() throws Exception {
        Class<?> clazz = Class.forName(new LookAndFeelManager().getCurrentLookAndFeel());
        assertTrue(LookAndFeel.class.isAssignableFrom(clazz),
                   clazz + " is not a javax.swing.LookAndFeel");
    }

    // ---- isSupportedLookAndFeel -----------------------------------------

    /**
     * Ties {@code getInstalledLookAndFeels} to {@code isSupportedLookAndFeel}:
     * every name the manager lists as installed must also be reported supported.
     */
    @Test
    void everyInstalledLookAndFeelIsReportedSupported() {
        LookAndFeelManager mgr = new LookAndFeelManager();
        for (String name : mgr.getInstalledLookAndFeels()) {
            assertTrue(mgr.isSupportedLookAndFeel(name), name + " is installed yet reported unsupported");
        }
    }

    @Test
    void unknownLookAndFeelIsNotSupported() {
        assertFalse(new LookAndFeelManager().isSupportedLookAndFeel("com.example.NoSuchLookAndFeel"));
    }

    @Test
    void emptyLookAndFeelNameIsNotSupported() {
        assertFalse(new LookAndFeelManager().isSupportedLookAndFeel(""));
    }

    /**
     * The lookup compares with {@code installedName.equals(argument)}, so a null
     * argument yields {@code false} for every entry and the loop never throws.
     */
    @Test
    void nullLookAndFeelNameIsNotSupportedAndDoesNotThrow() {
        assertFalse(new LookAndFeelManager().isSupportedLookAndFeel(null));
    }

    @Test
    void supportCheckIsCaseSensitive() {
        LookAndFeelManager mgr = new LookAndFeelManager();
        String first = mgr.getInstalledLookAndFeels()[0];
        assertTrue(mgr.isSupportedLookAndFeel(first));
        // A fully-qualified class name always contains lower-case package parts,
        // so the upper-cased form can never match an installed entry.
        assertFalse(mgr.isSupportedLookAndFeel(first.toUpperCase()));
    }

    // ---- setLookAndFeel -------------------------------------------------

    @Test
    void setMetalLookAndFeelSucceedsAndBecomesCurrent() {
        LookAndFeelManager mgr = new LookAndFeelManager();
        assumeTrue(mgr.isSupportedLookAndFeel(METAL), "Metal look-and-feel not installed");
        assertTrue(mgr.setLookAndFeel(METAL));
        assertEquals(METAL, mgr.getCurrentLookAndFeel());
    }

    @Test
    void setUnknownLookAndFeelFailsAndLeavesCurrentUnchanged() {
        LookAndFeelManager mgr = new LookAndFeelManager();
        String before = mgr.getCurrentLookAndFeel();
        assertFalse(mgr.setLookAndFeel("com.example.NoSuchLookAndFeel"));
        assertEquals(before, mgr.getCurrentLookAndFeel());
    }

    /**
     * Characterization: a null class name provokes a {@link NullPointerException}
     * on the event dispatch thread, which {@code invokeAndWait} rewraps as an
     * {@code InvocationTargetException}. The manager catches that, so the call
     * returns {@code false} rather than propagating, and the current look-and-feel
     * is untouched.
     */
    @Test
    void setNullLookAndFeelIsSwallowedAndReturnsFalse() {
        LookAndFeelManager mgr = new LookAndFeelManager();
        String before = mgr.getCurrentLookAndFeel();
        assertFalse(mgr.setLookAndFeel(null));
        assertEquals(before, mgr.getCurrentLookAndFeel());
    }

    // ---- setSystemLookAndFeel -------------------------------------------

    /**
     * On a standard desktop the platform look-and-feel is part of the installed
     * set; setting it succeeds and becomes current. Where it is not installed
     * (e.g. some headless CI images) the test is skipped rather than asserted.
     */
    @Test
    void setSystemLookAndFeelInstallsTheSystemLookAndFeel() {
        LookAndFeelManager mgr = new LookAndFeelManager();
        String systemName = UIManager.getSystemLookAndFeelClassName();
        assumeTrue(mgr.isSupportedLookAndFeel(systemName),
                   "system look-and-feel " + systemName + " is not in the installed set here");
        assertTrue(mgr.setSystemLookAndFeel());
        assertEquals(systemName, mgr.getCurrentLookAndFeel());
    }
}
