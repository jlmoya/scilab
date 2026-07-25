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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.event.MouseListener;
import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link FocusMouseListener}.
 *
 * <p>The two active callbacks ({@code mouseClicked} / {@code mouseOver}) forward
 * focus through a live {@link SciConsole} and its configuration, so their happy
 * path needs a running console. What is hermetic and pinned here: the class is a
 * {@link MouseListener}, the constructor stores the console lazily, the four
 * inherited callbacks ({@code mouseEntered/Exited/Pressed/Released}) are true
 * no-ops that touch neither the event nor the console, and — by contrast — the
 * two active callbacks really do dereference the console (they NPE when it is
 * {@code null}), which documents which methods are live versus inert.
 */
public class FocusMouseListenerTest {

    @Test
    public void isAMouseListener() {
        assertTrue(new FocusMouseListener(null) instanceof MouseListener);
    }

    @Test
    public void theConstructorStoresTheConsoleWithoutTouchingIt() throws Exception {
        FocusMouseListener listener = new FocusMouseListener(null);
        Field f = FocusMouseListener.class.getDeclaredField("c");
        f.setAccessible(true);
        assertNull(f.get(listener));
    }

    @Test
    public void theInheritedCallbacksAreNoOps() {
        FocusMouseListener listener = new FocusMouseListener(null);
        assertDoesNotThrow(() -> listener.mouseEntered(null));
        assertDoesNotThrow(() -> listener.mouseExited(null));
        assertDoesNotThrow(() -> listener.mousePressed(null));
        assertDoesNotThrow(() -> listener.mouseReleased(null));
    }

    @Test
    public void mouseClickedActivelyUsesTheConsoleAndSoNpEsWhenItIsAbsent() {
        // Characterization: unlike the inherited no-ops, mouseClicked reaches
        // c.getConfiguration()..., so a null console surfaces as an NPE.
        FocusMouseListener listener = new FocusMouseListener(null);
        assertThrows(NullPointerException.class, () -> listener.mouseClicked(null));
    }

    @Test
    public void mouseOverActivelyUsesTheConsoleAndSoNpEsWhenItIsAbsent() {
        FocusMouseListener listener = new FocusMouseListener(null);
        assertThrows(NullPointerException.class, () -> listener.mouseOver(null));
    }
}
