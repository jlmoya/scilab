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

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.lang.reflect.Field;

import javax.swing.JLabel;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link OneCharKeyEventListener}.
 *
 * <p>{@code keyPressed} pushes the user's answer into a live {@link SciConsole}
 * ({@code setUserInputValue}), so its effect needs a running console. Hermetic
 * and pinned here: the class is a {@link KeyListener}, the constructor stores the
 * console lazily, {@code keyReleased} / {@code keyTyped} are stub no-ops, and —
 * by contrast — {@code keyPressed} really does drive the console (it NPEs when
 * the console is {@code null}), so the answer-routing branch is reached.
 */
public class OneCharKeyEventListenerTest {

    private static KeyEvent keyEvent(char c) {
        // A KeyEvent needs a non-null Component source; a headless JLabel is fine.
        return new KeyEvent(new JLabel(), KeyEvent.KEY_PRESSED, 0L, 0, KeyEvent.VK_UNDEFINED, c);
    }

    @Test
    public void isAKeyListener() {
        assertTrue(new OneCharKeyEventListener(null) instanceof KeyListener);
    }

    @Test
    public void theConstructorStoresTheConsoleWithoutTouchingIt() throws Exception {
        OneCharKeyEventListener listener = new OneCharKeyEventListener(null);
        Field f = OneCharKeyEventListener.class.getDeclaredField("sciConsole");
        f.setAccessible(true);
        assertNull(f.get(listener));
    }

    @Test
    public void keyReleasedIsAStubNoOp() {
        OneCharKeyEventListener listener = new OneCharKeyEventListener(null);
        assertDoesNotThrow(() -> listener.keyReleased(keyEvent('n')));
    }

    @Test
    public void keyTypedIsAStubNoOp() {
        OneCharKeyEventListener listener = new OneCharKeyEventListener(null);
        assertDoesNotThrow(() -> listener.keyTyped(keyEvent('n')));
    }

    @Test
    public void keyPressedForNRoutesTheAnswerIntoTheConsoleAndSoNpEsWhenItIsAbsent() {
        // The 'n' branch calls sciConsole.setUserInputValue(...); with a null
        // console that surfaces as an NPE, proving the branch is actually taken.
        OneCharKeyEventListener listener = new OneCharKeyEventListener(null);
        assertThrows(NullPointerException.class, () -> listener.keyPressed(keyEvent('n')));
    }

    @Test
    public void keyPressedForAnyOtherKeyAlsoRoutesIntoTheConsoleAndSoNpEsWhenItIsAbsent() {
        // The else branch (setUserInputValue(1)) equally dereferences the console.
        OneCharKeyEventListener listener = new OneCharKeyEventListener(null);
        assertThrows(NullPointerException.class, () -> listener.keyPressed(keyEvent('y')));
    }
}
