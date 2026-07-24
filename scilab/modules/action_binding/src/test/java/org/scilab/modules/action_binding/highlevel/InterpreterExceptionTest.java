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

package org.scilab.modules.action_binding.highlevel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.scilab.modules.action_binding.highlevel.ScilabInterpreterManagement.InterpreterException;

/**
 * Hermetic unit tests for {@link ScilabInterpreterManagement.InterpreterException},
 * the checked exception raised when a command cannot be handed to the
 * interpreter. Constructing it touches no native code.
 */
class InterpreterExceptionTest {

    @AfterAll
    static void shutdownExecutor() {
        // Referencing the nested class may initialise the enclosing class,
        // which creates a non-daemon executor; shut it down for JVM hygiene.
        ScilabInterpreterManagement.forceClose();
    }

    @Test
    void constructorPreservesMessage() {
        InterpreterException e = new InterpreterException("boom");
        assertEquals("boom", e.getMessage());
    }

    @Test
    void isACheckedException() {
        assertTrue(Exception.class.isAssignableFrom(InterpreterException.class),
            "InterpreterException must be an Exception");
        assertFalse(RuntimeException.class.isAssignableFrom(InterpreterException.class),
            "InterpreterException must be checked, not a RuntimeException");
    }

    @Test
    void canBeThrownAndCaughtWithItsMessage() {
        InterpreterException thrown = assertThrows(InterpreterException.class, () -> {
            throw new InterpreterException("interpreter unreachable");
        });
        assertEquals("interpreter unreachable", thrown.getMessage());
        assertTrue(thrown instanceof Exception);
    }

    @Test
    void nullMessageIsAllowed() {
        InterpreterException e = new InterpreterException(null);
        assertNull(e.getMessage());
    }

    @Test
    void hasNoCauseByDefault() {
        InterpreterException e = new InterpreterException("x");
        assertNull(e.getCause());
        // initCause is still available from Throwable
        Throwable cause = new IllegalStateException("root");
        assertSame(e, e.initCause(cause));
        assertSame(cause, e.getCause());
    }
}
