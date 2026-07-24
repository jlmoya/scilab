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

package org.scilab.modules.preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link XComponentAttributes}, the attribute-name
 * constant holder. The class carries no behaviour, so the contract under test is
 * (a) the literal constant values used as DOM attribute keys throughout the
 * module and (b) that it is a non-instantiable utility class (final, private
 * constructor).
 */
public class XComponentAttributesTest {

    @Test
    public void constantsHoldTheExpectedAttributeKeys() {
        assertEquals("background", XComponentAttributes.BACKGROUND);
        assertEquals("foreground", XComponentAttributes.FOREGROUND);
        assertEquals("tooltip", XComponentAttributes.TOOLTIP);
    }

    @Test
    public void constantsAreDistinct() {
        assertFalse(XComponentAttributes.BACKGROUND.equals(XComponentAttributes.FOREGROUND));
        assertFalse(XComponentAttributes.BACKGROUND.equals(XComponentAttributes.TOOLTIP));
        assertFalse(XComponentAttributes.FOREGROUND.equals(XComponentAttributes.TOOLTIP));
    }

    @Test
    public void classIsFinalUtilityHolder() {
        assertTrue(Modifier.isFinal(XComponentAttributes.class.getModifiers()),
                   "XComponentAttributes is declared final");
    }

    @Test
    public void soleConstructorIsPrivate() throws Exception {
        Constructor<?>[] ctors = XComponentAttributes.class.getDeclaredConstructors();
        assertEquals(1, ctors.length, "exactly one (private) constructor");
        assertTrue(Modifier.isPrivate(ctors[0].getModifiers()),
                   "the constructor is private so the holder cannot be instantiated normally");

        // Exercise it reflectively for coverage; it must not throw.
        ctors[0].setAccessible(true);
        Object instance = ctors[0].newInstance();
        assertNotNull(instance);
    }
}
