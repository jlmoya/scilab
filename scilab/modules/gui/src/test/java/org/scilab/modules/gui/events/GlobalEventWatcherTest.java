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

package org.scilab.modules.gui.events;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.AWTEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link GlobalEventWatcher}, the JVM-level singleton
 * that (de)registers the global key/mouse AWT event listeners and holds a few
 * pieces of shared "am I watching?" state.
 *
 * <p>All of {@code GlobalEventWatcher}'s API is {@code static} and mutates
 * process-wide state, so every test brackets itself with {@link #resetState()}
 * (run both before and after) which calls {@link GlobalEventWatcher#disable()}
 * to clear the flags and unregister any test listeners, then nulls the axes id.
 * That keeps the tests order-independent and stops them leaking AWT listeners
 * into the rest of the JVM.
 *
 * <p>{@code enable(...)}/{@code disable()} do touch {@code Toolkit
 * .getDefaultToolkit()}, but under the forced {@code java.awt.headless=true}
 * test JVM that is the {@code HeadlessToolkit}, whose
 * {@code add/removeAWTEventListener} are no-throw. The only publicly observable
 * effect is {@link GlobalEventWatcher#isActivated()} (the real AWT dispatch
 * cannot be driven hermetically), so that flag is what the tests assert.
 */
public class GlobalEventWatcherTest {

    /** Minimal concrete key watcher; the filter body is irrelevant here. */
    private static final class TestKeyWatcher extends GlobalKeyEventWatcher {
        @Override
        public void keyEventFilter(KeyEvent keyEvent) {
            // no-op
        }
    }

    /** Minimal concrete mouse watcher carrying a real event mask. */
    private static final class TestMouseWatcher extends GlobalMouseEventWatcher {
        TestMouseWatcher(long eventMask) {
            super(eventMask);
        }

        @Override
        public void mouseEventFilter(MouseEvent mouseEvent, Integer axesUID, int scilabMouseAction, boolean isControlDown) {
            // no-op
        }
    }

    @BeforeEach
    @AfterEach
    public void resetState() {
        // disable() is null-safe (removeAWTEventListener(null) is a no-op) and
        // resets both the activated and catchingCallback flags.
        GlobalEventWatcher.disable();
        GlobalEventWatcher.setAxesUID(null);
    }

    // --- singleton ----------------------------------------------------------

    @Test
    public void getInstanceNeverReturnsNull() {
        assertNotNull(GlobalEventWatcher.getInstance());
    }

    @Test
    public void getInstanceReturnsAStableSingleton() {
        assertSame(GlobalEventWatcher.getInstance(), GlobalEventWatcher.getInstance());
    }

    // --- axesUID accessor ---------------------------------------------------

    @Test
    public void axesUidRoundTrips() {
        GlobalEventWatcher.setAxesUID(Integer.valueOf(123));
        assertEquals(Integer.valueOf(123), GlobalEventWatcher.getAxesUID());
    }

    @Test
    public void axesUidAcceptsNull() {
        GlobalEventWatcher.setAxesUID(Integer.valueOf(1));
        GlobalEventWatcher.setAxesUID(null);
        assertNull(GlobalEventWatcher.getAxesUID());
    }

    @Test
    public void axesUidAcceptsBoundaryValues() {
        GlobalEventWatcher.setAxesUID(Integer.valueOf(Integer.MIN_VALUE));
        assertEquals(Integer.valueOf(Integer.MIN_VALUE), GlobalEventWatcher.getAxesUID());
        GlobalEventWatcher.setAxesUID(Integer.valueOf(Integer.MAX_VALUE));
        assertEquals(Integer.valueOf(Integer.MAX_VALUE), GlobalEventWatcher.getAxesUID());
    }

    // --- catchingCallback flag ---------------------------------------------

    @Test
    public void catchingCallbackIsFalseAfterReset() {
        // resetState()/disable() must have cleared it.
        assertFalse(GlobalEventWatcher.isCatchingCallback());
    }

    @Test
    public void catchingCallbackTogglesOnAndOff() {
        GlobalEventWatcher.enableCatchingCallback();
        assertTrue(GlobalEventWatcher.isCatchingCallback());
        GlobalEventWatcher.disableCatchingCallback();
        assertFalse(GlobalEventWatcher.isCatchingCallback());
    }

    @Test
    public void catchingCallbackToggleIsIdempotent() {
        GlobalEventWatcher.enableCatchingCallback();
        GlobalEventWatcher.enableCatchingCallback();
        assertTrue(GlobalEventWatcher.isCatchingCallback());
        GlobalEventWatcher.disableCatchingCallback();
        GlobalEventWatcher.disableCatchingCallback();
        assertFalse(GlobalEventWatcher.isCatchingCallback());
    }

    // --- activation via key/mouse watchers ---------------------------------

    @Test
    public void isActivatedIsFalseAfterReset() {
        assertFalse(GlobalEventWatcher.isActivated());
    }

    @Test
    public void enablingAKeyWatcherActivatesTheWatcher() {
        GlobalEventWatcher.enable(new TestKeyWatcher());
        assertTrue(GlobalEventWatcher.isActivated());
    }

    @Test
    public void enablingAMouseWatcherActivatesTheWatcher() {
        GlobalEventWatcher.enable(new TestMouseWatcher(AWTEvent.MOUSE_EVENT_MASK));
        assertTrue(GlobalEventWatcher.isActivated());
    }

    @Test
    public void disableDeactivatesAfterAKeyWatcherWasEnabled() {
        GlobalEventWatcher.enable(new TestKeyWatcher());
        assertTrue(GlobalEventWatcher.isActivated());

        GlobalEventWatcher.disable();
        assertFalse(GlobalEventWatcher.isActivated());
    }

    @Test
    public void enablingBothWatchersKeepsTheWatcherActivated() {
        GlobalEventWatcher.enable(new TestKeyWatcher());
        GlobalEventWatcher.enable(new TestMouseWatcher(AWTEvent.MOUSE_EVENT_MASK));
        assertTrue(GlobalEventWatcher.isActivated());
    }

    // --- disable() side effects --------------------------------------------

    @Test
    public void disableAlsoClearsTheCatchingCallbackFlag() {
        GlobalEventWatcher.enable(new TestKeyWatcher());
        GlobalEventWatcher.enableCatchingCallback();
        assertTrue(GlobalEventWatcher.isCatchingCallback());

        GlobalEventWatcher.disable();

        assertFalse(GlobalEventWatcher.isActivated());
        assertFalse(GlobalEventWatcher.isCatchingCallback());
    }

    @Test
    public void disableIsSafeWhenNothingWasEverEnabled() {
        // No enable() beforehand: the internal watcher references are null and
        // removeAWTEventListener(null) is a documented no-op, so this must not
        // throw and must leave the watcher deactivated.
        assertDoesNotThrow(GlobalEventWatcher::disable);
        assertFalse(GlobalEventWatcher.isActivated());
    }

    @Test
    public void disableDoesNotResetTheAxesUid() {
        // Characterization: disable() clears the activation/catching flags but
        // deliberately leaves the axes id in place.
        GlobalEventWatcher.setAxesUID(Integer.valueOf(55));
        GlobalEventWatcher.enable(new TestKeyWatcher());

        GlobalEventWatcher.disable();

        assertEquals(Integer.valueOf(55), GlobalEventWatcher.getAxesUID());
    }

    @Test
    public void enableDoesNotDisturbAxesUidOrCatchingCallback() {
        // Characterization: enabling a watcher only flips `activated`; it leaves
        // the axes id and the catching-callback flag untouched.
        GlobalEventWatcher.setAxesUID(Integer.valueOf(9));
        GlobalEventWatcher.enableCatchingCallback();

        GlobalEventWatcher.enable(new TestMouseWatcher(AWTEvent.MOUSE_EVENT_MASK));

        assertTrue(GlobalEventWatcher.isActivated());
        assertEquals(Integer.valueOf(9), GlobalEventWatcher.getAxesUID());
        assertTrue(GlobalEventWatcher.isCatchingCallback());
    }
}
