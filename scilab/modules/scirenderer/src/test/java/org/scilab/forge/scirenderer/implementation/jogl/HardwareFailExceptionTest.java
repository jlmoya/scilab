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

package org.scilab.forge.scirenderer.implementation.jogl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hermetic unit tests for {@link HardwareFailException}. The exception itself is a plain
 * {@code java.lang.Exception} subclass, so it can be tested with no JOGL/GL context.
 */
public class HardwareFailExceptionTest {

    @Test
    public void messageConstructorStoresTheMessage() {
        HardwareFailException e = new HardwareFailException("GPU too old");
        assertEquals("GPU too old", e.getMessage());
        assertNull(e.getCause());
    }

    @Test
    public void isACheckedException() {
        assertTrue(Exception.class.isAssignableFrom(HardwareFailException.class));
        assertFalse(RuntimeException.class.isAssignableFrom(HardwareFailException.class),
                    "HardwareFailException is checked, not a RuntimeException");
    }

    @Test
    public void canBeThrownAndCaughtAsACheckedException() {
        HardwareFailException caught = assertThrows(HardwareFailException.class, () -> {
            throw new HardwareFailException("boom");
        });
        assertEquals("boom", caught.getMessage());
    }

    @Test
    public void propagatesAsItsOwnTypeThroughACatchAllHandler() {
        HardwareFailException original = new HardwareFailException("hw");
        Exception seen;
        try {
            throw original;
        } catch (Exception e) {
            seen = e;
        }
        assertSame(original, seen);
    }

    @Test
    public void nullMessageIsPreserved() {
        HardwareFailException e = new HardwareFailException(null);
        assertNull(e.getMessage());
    }
}
