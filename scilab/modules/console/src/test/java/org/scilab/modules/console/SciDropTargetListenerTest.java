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

package org.scilab.modules.console;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.dnd.DropTargetListener;
import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link SciDropTargetListener}.
 *
 * <p>The {@code drop(...)} method needs a live {@link SciConsole} and a real
 * {@code DropTargetDropEvent} carrying a {@code Transferable}, so it is out of
 * scope. What is hermetic is the listener's <em>contract</em>: it is a
 * {@link DropTargetListener}, its constructor merely stores the console without
 * touching it, and the four drag-notification callbacks are documented no-ops
 * ("Nothing to do in Scilab Console"). Those are pinned here by driving them on a
 * listener built with a {@code null} console — if any of them ever started
 * dereferencing the console, the {@code null} would surface as an NPE.
 */
public class SciDropTargetListenerTest {

    @Test
    public void isADropTargetListener() {
        assertTrue(new SciDropTargetListener(null) instanceof DropTargetListener);
    }

    @Test
    public void theConstructorStoresTheConsoleWithoutTouchingIt() throws Exception {
        // Passing null proves the constructor does no eager work on the console
        // (it would NPE otherwise) and stores the reference verbatim.
        SciDropTargetListener listener = new SciDropTargetListener(null);
        Field f = SciDropTargetListener.class.getDeclaredField("associatedConsole");
        f.setAccessible(true);
        assertNull(f.get(listener));
    }

    @Test
    public void dragEnterIsANoOpThatIgnoresBothTheEventAndTheConsole() {
        SciDropTargetListener listener = new SciDropTargetListener(null);
        assertDoesNotThrow(() -> listener.dragEnter(null));
    }

    @Test
    public void dragExitIsANoOpThatIgnoresBothTheEventAndTheConsole() {
        SciDropTargetListener listener = new SciDropTargetListener(null);
        assertDoesNotThrow(() -> listener.dragExit(null));
    }

    @Test
    public void dragOverIsANoOpThatIgnoresBothTheEventAndTheConsole() {
        SciDropTargetListener listener = new SciDropTargetListener(null);
        assertDoesNotThrow(() -> listener.dragOver(null));
    }

    @Test
    public void dropActionChangedIsANoOpThatIgnoresBothTheEventAndTheConsole() {
        SciDropTargetListener listener = new SciDropTargetListener(null);
        assertDoesNotThrow(() -> listener.dropActionChanged(null));
    }
}
