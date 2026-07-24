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

package org.scilab.modules.commons.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.event.KeyEvent;

import javax.swing.KeyStroke;

import org.junit.jupiter.api.Test;
import org.scilab.modules.commons.OS;

/**
 * Hermetic unit tests for {@link ScilabKeyStroke}.
 *
 * <p>{@code KeyStroke} is an immutable value object, so the substitution and
 * parsing logic can be exercised without any live widget. The OS-dependent meta
 * key is asserted precisely on macOS (where it is always {@code Meta}) and
 * checked for well-formedness on every other platform.
 */
public class ScilabKeyStrokeTest {

    @Test
    public void osMetaKeyIsWellFormedAndTrimmed() {
        String key = ScilabKeyStroke.getOSMetaKey();
        assertNotNull(key);
        assertEquals(key.trim(), key, "the meta key must not carry leading/trailing spaces");
        assertFalse(key.startsWith(" "), "the leading space produced while concatenating must be stripped");

        if (OS.get() == OS.MAC) {
            assertEquals("Meta", key, "the macOS menu-shortcut key is Meta (Apple/Command)");
        } else {
            assertFalse(key.isEmpty(), "every platform has at least one menu-shortcut modifier");
            assertTrue(key.matches("[A-Za-z]+( [A-Za-z]+)*"), "unexpected meta key spelling: <" + key + ">");
        }
    }

    @Test
    public void keyStrokeWithoutSubstitutionDelegatesVerbatim() {
        KeyStroke expected = KeyStroke.getKeyStroke("control A");
        KeyStroke actual = ScilabKeyStroke.getKeyStroke("control A");
        assertNotNull(actual);
        assertEquals(expected, actual);
        assertEquals(KeyEvent.VK_A, actual.getKeyCode());
    }

    @Test
    public void keyStrokeParsesFunctionKeysAndModifiers() {
        KeyStroke actual = ScilabKeyStroke.getKeyStroke("shift F1");
        assertNotNull(actual);
        assertEquals(KeyEvent.VK_F1, actual.getKeyCode());
        assertEquals(KeyStroke.getKeyStroke("shift F1"), actual);
    }

    @Test
    public void ossckeyIsReplacedByTheLowercasedOSMetaKey() {
        String meta = ScilabKeyStroke.getOSMetaKey().toLowerCase();
        KeyStroke expected = KeyStroke.getKeyStroke(meta + " A");
        KeyStroke actual = ScilabKeyStroke.getKeyStroke("OSSCKEY A");

        assertNotNull(actual, "OSSCKEY must resolve to a valid keystroke");
        assertEquals(expected, actual);
        assertEquals(KeyEvent.VK_A, actual.getKeyCode());
        assertTrue(actual.getModifiers() != 0, "the resolved keystroke must carry the OS modifier");
    }

    @Test
    public void typedCharacterKeyStrokeHasNoKeyCode() {
        KeyStroke actual = ScilabKeyStroke.getKeyStroke("typed a");
        assertNotNull(actual);
        assertEquals('a', actual.getKeyChar());
        assertEquals(KeyEvent.VK_UNDEFINED, actual.getKeyCode());
    }

    @Test
    public void unparseableDescriptionYieldsNull() {
        assertNull(ScilabKeyStroke.getKeyStroke("definitely not a keystroke"));
    }

    @Test
    public void nullDescriptionThrowsNullPointerException() {
        // Characterizes current behaviour: the internal replaceAll dereferences the argument.
        assertThrows(NullPointerException.class, () -> ScilabKeyStroke.getKeyStroke(null));
    }
}
