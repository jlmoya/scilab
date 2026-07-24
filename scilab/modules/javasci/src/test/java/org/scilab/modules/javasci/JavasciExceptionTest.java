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

package org.scilab.modules.javasci;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.Serializable;
import java.lang.reflect.Modifier;

import org.junit.jupiter.api.Test;

import org.scilab.modules.javasci.JavasciException.AlreadyRunningException;
import org.scilab.modules.javasci.JavasciException.InitializationException;
import org.scilab.modules.javasci.JavasciException.ScilabErrorException;
import org.scilab.modules.javasci.JavasciException.ScilabInternalException;
import org.scilab.modules.javasci.JavasciException.UndefinedVariableException;
import org.scilab.modules.javasci.JavasciException.UnknownTypeException;
import org.scilab.modules.javasci.JavasciException.UnsupportedTypeException;

/**
 * Hermetic unit tests for {@link JavasciException} and its seven nested
 * exception subclasses.
 *
 * The whole exception hierarchy is pure Java (it extends {@link Exception} and
 * touches nothing else) so no Scilab engine, native library, or GUI is
 * involved. The tests exercise message propagation, cause chaining, the base
 * class contract (abstract, checked, serializable), polymorphic
 * catch-as-base behaviour, and the one non-trivial formatter
 * ({@link ScilabErrorException}, which appends the error code to the message).
 */
class JavasciExceptionTest {

    /**
     * The base class is {@code abstract}, so a concrete throwaway subclass is
     * needed to reach its three constructors directly.
     */
    private static final class ConcreteJavasciException extends JavasciException {
        ConcreteJavasciException() {
            super();
        }

        ConcreteJavasciException(String message) {
            super(message);
        }

        ConcreteJavasciException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    // ------------------------------------------------------------------
    // Base class contract
    // ------------------------------------------------------------------

    @Test
    void baseClassIsAbstract() {
        assertTrue(Modifier.isAbstract(JavasciException.class.getModifiers()),
                   "JavasciException must stay abstract so only the typed subclasses are thrown");
    }

    @Test
    void baseClassIsCheckedAndSerializable() {
        // A checked exception: Exception but NOT RuntimeException.
        assertTrue(Exception.class.isAssignableFrom(JavasciException.class));
        assertFalse(RuntimeException.class.isAssignableFrom(JavasciException.class),
                    "JavasciException must be a checked exception");
        // Exceptions must be serializable.
        assertTrue(Serializable.class.isAssignableFrom(JavasciException.class));
    }

    @Test
    void baseNoArgConstructorHasNullMessageAndCause() {
        JavasciException e = new ConcreteJavasciException();
        assertNull(e.getMessage());
        assertNull(e.getCause());
    }

    @Test
    void baseMessageConstructorStoresMessageAndLeavesCauseNull() {
        JavasciException e = new ConcreteJavasciException("boom");
        assertEquals("boom", e.getMessage());
        assertNull(e.getCause());
    }

    @Test
    void baseMessageCauseConstructorPreservesBoth() {
        Throwable cause = new IllegalStateException("root");
        JavasciException e = new ConcreteJavasciException("wrapper", cause);
        assertEquals("wrapper", e.getMessage());
        assertSame(cause, e.getCause());
    }

    // ------------------------------------------------------------------
    // InitializationException (the only subclass with a cause ctor)
    // ------------------------------------------------------------------

    @Test
    void initializationExceptionMessageOnly() {
        InitializationException e = new InitializationException("SCI empty");
        assertEquals("SCI empty", e.getMessage());
        assertNull(e.getCause());
        assertInstanceOf(JavasciException.class, e);
        assertInstanceOf(Exception.class, e);
    }

    @Test
    void initializationExceptionMessageAndCause() {
        Throwable cause = new RuntimeException("no SCI var");
        InitializationException e = new InitializationException("auto detect failed", cause);
        assertEquals("auto detect failed", e.getMessage());
        assertSame(cause, e.getCause());
        assertInstanceOf(JavasciException.class, e);
    }

    // ------------------------------------------------------------------
    // The remaining message-only subclasses
    // ------------------------------------------------------------------

    @Test
    void unsupportedTypeExceptionMessage() {
        UnsupportedTypeException e = new UnsupportedTypeException("Type not managed: sci_ints");
        assertEquals("Type not managed: sci_ints", e.getMessage());
        assertNull(e.getCause());
        assertInstanceOf(JavasciException.class, e);
    }

    @Test
    void unknownTypeExceptionMessage() {
        UnknownTypeException e = new UnknownTypeException("Type of a unknown");
        assertEquals("Type of a unknown", e.getMessage());
        assertNull(e.getCause());
        assertInstanceOf(JavasciException.class, e);
    }

    @Test
    void alreadyRunningExceptionMessage() {
        AlreadyRunningException e = new AlreadyRunningException("Javasci already running.");
        assertEquals("Javasci already running.", e.getMessage());
        assertNull(e.getCause());
        assertInstanceOf(JavasciException.class, e);
    }

    @Test
    void scilabInternalExceptionMessage() {
        ScilabInternalException e = new ScilabInternalException("Storage of the variable 'a' failed.");
        assertEquals("Storage of the variable 'a' failed.", e.getMessage());
        assertNull(e.getCause());
        assertInstanceOf(JavasciException.class, e);
    }

    @Test
    void undefinedVariableExceptionMessage() {
        UndefinedVariableException e = new UndefinedVariableException("Could not find variable 'a'");
        assertEquals("Could not find variable 'a'", e.getMessage());
        assertNull(e.getCause());
        assertInstanceOf(JavasciException.class, e);
    }

    // ------------------------------------------------------------------
    // ScilabErrorException — the one subclass that reformats its message
    // (super(message + "\nCode: " + errorCode))
    // ------------------------------------------------------------------

    @Test
    void scilabErrorExceptionAppendsCodeToMessage() {
        ScilabErrorException e = new ScilabErrorException("A Scilab error occurred", 42);
        assertEquals("A Scilab error occurred\nCode: 42", e.getMessage());
        assertNull(e.getCause());
        assertInstanceOf(JavasciException.class, e);
    }

    @Test
    void scilabErrorExceptionZeroCode() {
        ScilabErrorException e = new ScilabErrorException("ok", 0);
        assertEquals("ok\nCode: 0", e.getMessage());
    }

    @Test
    void scilabErrorExceptionNegativeCode() {
        ScilabErrorException e = new ScilabErrorException("undefined", -999);
        assertEquals("undefined\nCode: -999", e.getMessage());
        assertTrue(e.getMessage().contains("\nCode: "),
                   "the formatted message must carry the newline-delimited code marker");
    }

    /**
     * Defect-characterization: a {@code null} message is not guarded, so Java
     * string concatenation renders it as the literal {@code "null"}. This test
     * pins the current behaviour rather than endorsing it.
     */
    @Test
    void scilabErrorExceptionNullMessageBecomesLiteralNull() {
        ScilabErrorException e = new ScilabErrorException(null, 7);
        assertEquals("null\nCode: 7", e.getMessage());
    }

    // ------------------------------------------------------------------
    // Polymorphism, throwing and catching
    // ------------------------------------------------------------------

    @Test
    void subclassIsCatchableAsBase() {
        // Thrown as the concrete type, caught as the abstract base.
        JavasciException caught = assertThrows(JavasciException.class, () -> {
            throw new InitializationException("bang");
        });
        assertInstanceOf(InitializationException.class, caught);
        assertEquals("bang", caught.getMessage());
    }

    @Test
    void scilabErrorExceptionThrownAndCaughtKeepsFormattedMessage() {
        ScilabErrorException caught = assertThrows(ScilabErrorException.class, () -> {
            throw new ScilabErrorException("job failed", 2);
        });
        assertEquals("job failed\nCode: 2", caught.getMessage());
    }

    @Test
    void nestedTypesAreDistinctButShareTheBase() {
        Class<?>[] types = {
            InitializationException.class,
            UnsupportedTypeException.class,
            UnknownTypeException.class,
            AlreadyRunningException.class,
            ScilabInternalException.class,
            ScilabErrorException.class,
            UndefinedVariableException.class,
        };
        for (Class<?> t : types) {
            assertTrue(JavasciException.class.isAssignableFrom(t),
                       t.getSimpleName() + " must extend JavasciException");
        }
        // Every subclass is a genuinely different class from the others.
        for (int i = 0; i < types.length; i++) {
            for (int j = i + 1; j < types.length; j++) {
                assertNotEquals(types[i], types[j]);
            }
        }
        // ...and none of them is the base class itself.
        for (Class<?> t : types) {
            assertNotEquals(JavasciException.class, t);
        }
    }
}
