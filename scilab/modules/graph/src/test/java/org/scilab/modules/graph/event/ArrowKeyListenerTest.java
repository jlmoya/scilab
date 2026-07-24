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

package org.scilab.modules.graph.event;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.event.KeyListener;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for the configuration surface of {@link ArrowKeyListener}.
 *
 * Constructing the listener only creates a stopped javax.swing.Timer (no
 * display required). The key-handling methods that need a live mxGraphComponent
 * are out of scope; getDelay/setDelay and the no-op keyTyped are pure.
 */
public class ArrowKeyListenerTest {

    @Test
    public void isAKeyListener() {
        assertTrue(new ArrowKeyListener() instanceof KeyListener);
    }

    @Test
    public void defaultDelayIs800Milliseconds() {
        assertEquals(800, new ArrowKeyListener().getDelay());
    }

    @Test
    public void setDelayIsReflectedByGetDelay() {
        ArrowKeyListener listener = new ArrowKeyListener();
        listener.setDelay(250);
        assertEquals(250, listener.getDelay());

        listener.setDelay(0);
        assertEquals(0, listener.getDelay());
    }

    @Test
    public void keyTypedIsANoOpAndNeverDereferencesTheEvent() {
        // keyTyped has an empty body, so passing null must not throw.
        assertDoesNotThrow(() -> new ArrowKeyListener().keyTyped(null));
    }
}
