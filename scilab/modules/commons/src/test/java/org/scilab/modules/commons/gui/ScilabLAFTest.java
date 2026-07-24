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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JToggleButton;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link ScilabLAF#setDefaultProperties}.
 *
 * <p>Swing {@link javax.swing.AbstractButton}s are merely <em>constructed</em>
 * (never shown), which is valid in headless environments, so these assertions on
 * the button's model properties are display-independent.
 */
public class ScilabLAFTest {

    @Test
    public void setsTheThreeExpectedPropertiesOnAButton() {
        JButton button = new JButton("ok");
        // Sanity: the JDK defaults are the opposite of what setDefaultProperties enforces.
        assertTrue(button.isFocusable());
        assertTrue(button.isOpaque());

        ScilabLAF.setDefaultProperties(button);

        assertFalse(button.isFocusable(), "buttons must be made non-focusable");
        assertTrue(button.isContentAreaFilled(), "content area must stay filled");
        assertFalse(button.isOpaque(), "buttons must be made non-opaque");
    }

    @Test
    public void appliesToAnyAbstractButtonSubclass() {
        JToggleButton toggle = new JToggleButton();
        JCheckBox check = new JCheckBox();

        ScilabLAF.setDefaultProperties(toggle);
        ScilabLAF.setDefaultProperties(check);

        for (javax.swing.AbstractButton b : new javax.swing.AbstractButton[] {toggle, check}) {
            assertFalse(b.isFocusable());
            assertTrue(b.isContentAreaFilled());
            assertFalse(b.isOpaque());
        }
    }

    @Test
    public void nullButtonIsSilentlyIgnored() {
        assertDoesNotThrow(() -> ScilabLAF.setDefaultProperties(null));
    }

    @Test
    public void isIdempotent() {
        JButton button = new JButton();
        ScilabLAF.setDefaultProperties(button);
        ScilabLAF.setDefaultProperties(button);
        assertFalse(button.isFocusable());
        assertTrue(button.isContentAreaFilled());
        assertFalse(button.isOpaque());
    }
}
