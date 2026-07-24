/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Jose Moya
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 *
 */

package org.scilab.modules.renderer.JoGLView.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link OutOfMemoryException}, the checked
 * exception raised by sprite/texture allocation paths. Constructing it
 * touches no native code.
 */
class OutOfMemoryExceptionTest {

    @Test
    void defaultConstructorSuppliesFixedMessage() {
        OutOfMemoryException e = new OutOfMemoryException();
        assertEquals("no more memory", e.getMessage());
    }

    @Test
    void isACheckedException() {
        assertTrue(Exception.class.isAssignableFrom(OutOfMemoryException.class),
                   "OutOfMemoryException must extend Exception");
        assertFalse(RuntimeException.class.isAssignableFrom(OutOfMemoryException.class),
                    "OutOfMemoryException must be checked, not a RuntimeException");
    }

    @Test
    void canBeThrownAndCaughtCarryingItsMessage() {
        OutOfMemoryException thrown = assertThrows(OutOfMemoryException.class, () -> {
            throw new OutOfMemoryException();
        });
        assertEquals("no more memory", thrown.getMessage());
    }
}
