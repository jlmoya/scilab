/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Scilab
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 *
 */

package org.scilab.modules.history_manager;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import org.scilab.modules.commons.xml.XConfigurationEvent;
import org.scilab.modules.commons.xml.XConfigurationListener;

/**
 * Hermetic unit tests for {@link HistoryPrefs}.
 *
 * <p>{@code HistoryPrefs} is the singleton {@link XConfigurationListener} that reacts to
 * preference changes. Its {@code configurationChanged} body only reaches into
 * {@code XConfiguration} and the native {@code HistoryManagement} facade when the incoming
 * event carries the watched history XPath (or the wildcard {@code "ALL"}); every other event
 * is a pure no-op that returns before any native code could run. These tests exercise the
 * singleton contract and that no-op guard branch, so they need no {@code scihistory_manager}
 * shared library and no running Scilab. They deliberately never feed the watched path, because
 * doing so would cross into JNI and stop being hermetic.
 */
public class HistoryPrefsTest {

    /**
     * Mirror of the private {@code HISTORY_PATH} constant the listener watches.
     * {@link #watchedPathConstantIsTheHistorySettingsXPath()} asserts the real field still
     * equals this, so the "unrelated path" tests below stay honest about what does NOT trigger.
     */
    private static final String WATCHED_PATH = "//command-history/body/history-settings";

    private static Set<String> pathsOf(String... paths) {
        Set<String> set = new HashSet<>();
        Collections.addAll(set, paths);
        return set;
    }

    @Test
    public void getInstanceNeverReturnsNull() {
        assertNotNull(HistoryPrefs.getInstance());
    }

    @Test
    public void getInstanceIsAStableSingleton() {
        HistoryPrefs first = HistoryPrefs.getInstance();
        HistoryPrefs second = HistoryPrefs.getInstance();
        assertSame(first, second, "getInstance() must hand back the one shared instance");
    }

    @Test
    public void theInstanceIsAnXConfigurationListener() {
        assertInstanceOf(XConfigurationListener.class, HistoryPrefs.getInstance());
    }

    @Test
    public void watchedPathConstantIsTheHistorySettingsXPath() throws Exception {
        Field f = HistoryPrefs.class.getDeclaredField("HISTORY_PATH");
        f.setAccessible(true);
        assertEquals(WATCHED_PATH, f.get(null),
                     "the listener must watch the command-history settings XPath");
    }

    @Test
    public void unrelatedPathsAreAPureNoOp() {
        XConfigurationEvent event =
            new XConfigurationEvent(pathsOf("//something/else", "//scinotes/body"));
        assertDoesNotThrow(() -> HistoryPrefs.getInstance().configurationChanged(event));
    }

    @Test
    public void emptyPathSetIsAPureNoOp() {
        XConfigurationEvent event = new XConfigurationEvent(Collections.<String>emptySet());
        assertDoesNotThrow(() -> HistoryPrefs.getInstance().configurationChanged(event));
    }

    @Test
    public void aStrictPrefixOfTheWatchedPathDoesNotTrigger() {
        // The guard uses exact Set membership, not substring / startsWith matching: a partial
        // path must NOT be mistaken for the full history-settings path (which would cross into
        // XConfiguration + native HistoryManagement).
        XConfigurationEvent event =
            new XConfigurationEvent(pathsOf("//command-history", "//command-history/body"));
        assertDoesNotThrow(() -> HistoryPrefs.getInstance().configurationChanged(event));
    }

    @Test
    public void theAllWildcardIsCaseSensitiveSoLowercaseDoesNotTrigger() {
        // Only the exact wildcard token "ALL" is honored; "all" / "All" must be inert.
        XConfigurationEvent event = new XConfigurationEvent(pathsOf("all", "All"));
        assertDoesNotThrow(() -> HistoryPrefs.getInstance().configurationChanged(event));
    }

    @Test
    public void nullModifiedPathSetThrowsNpeDefectCharacterization() {
        // Defect characterization: configurationChanged does e.getModifiedPaths().contains(...)
        // with no null guard, so an event built from a null set NPEs before the branch decision
        // is reached. Pinned so a future null-hardening change is a conscious one.
        XConfigurationEvent event = new XConfigurationEvent(null);
        assertThrows(NullPointerException.class,
                     () -> HistoryPrefs.getInstance().configurationChanged(event));
    }

    @Test
    public void nullEventThrowsNpeDefectCharacterization() {
        // Defect characterization: a null event is dereferenced immediately, with no guard.
        assertThrows(NullPointerException.class,
                     () -> HistoryPrefs.getInstance().configurationChanged(null));
    }
}
