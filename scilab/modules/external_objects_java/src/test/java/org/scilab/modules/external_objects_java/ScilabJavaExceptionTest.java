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

package org.scilab.modules.external_objects_java;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link ScilabJavaException}, the module's single
 * checked-exception type. It is a thin wrapper over {@link Exception} whose only
 * real behavior is carrying the message it is constructed with, so these tests
 * pin that message plumbing and its place in the exception hierarchy.
 */
public class ScilabJavaExceptionTest {

    @Test
    public void carriesTheConstructionMessage() {
        ScilabJavaException e = new ScilabJavaException("boom");
        assertEquals("boom", e.getMessage());
        // getLocalizedMessage defaults to getMessage when not overridden.
        assertEquals("boom", e.getLocalizedMessage());
    }

    @Test
    public void isACheckedException() {
        ScilabJavaException e = new ScilabJavaException("x");
        assertTrue(e instanceof Exception, "must extend Exception (checked)");
        // Not a RuntimeException: callers are forced to declare/handle it.
        assertFalse(RuntimeException.class.isAssignableFrom(ScilabJavaException.class),
                    "must be checked, not a RuntimeException");
    }

    @Test
    public void hasNoCauseAndAnEmptyMessageIsPreserved() {
        ScilabJavaException e = new ScilabJavaException("");
        assertEquals("", e.getMessage());
        assertNull(e.getCause(), "constructor sets no cause");
    }

    @Test
    public void nullMessageStaysNull() {
        ScilabJavaException e = new ScilabJavaException(null);
        assertNull(e.getMessage());
    }

    @Test
    public void canBeThrownAndCaughtAsScilabJavaException() {
        ScilabJavaException thrown = assertThrows(ScilabJavaException.class, () -> {
            throw new ScilabJavaException("thrown");
        });
        assertEquals("thrown", thrown.getMessage());
    }

    @Test
    public void isCatchableAsPlainException() throws Exception {
        // Catching as the supertype must work (it is what the module's public API declares).
        ScilabJavaException original = new ScilabJavaException("super");
        try {
            throw original;
        } catch (Exception caught) {
            assertSame(original, caught);
        }
    }
}
