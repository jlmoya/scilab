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

package org.scilab.forge.scirenderer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hermetic unit tests for {@link SciRendererException}, the checked base exception.
 */
public class SciRendererExceptionTest {

    @Test
    public void isACheckedException() {
        assertTrue(Exception.class.isAssignableFrom(SciRendererException.class));
        assertTrue(new SciRendererException() instanceof Exception);
    }

    @Test
    public void noArgConstructorHasNoMessageOrCause() {
        SciRendererException e = new SciRendererException();
        assertNull(e.getMessage());
        assertNull(e.getCause());
    }

    @Test
    public void messageConstructorStoresMessage() {
        SciRendererException e = new SciRendererException("boom");
        assertEquals("boom", e.getMessage());
        assertNull(e.getCause());
    }

    @Test
    public void messageAndCauseConstructor() {
        Throwable cause = new IllegalStateException("root");
        SciRendererException e = new SciRendererException("wrapper", cause);
        assertEquals("wrapper", e.getMessage());
        assertSame(cause, e.getCause());
    }

    @Test
    public void causeConstructorDerivesMessageFromCause() {
        Throwable cause = new IllegalStateException("root");
        SciRendererException e = new SciRendererException(cause);
        assertSame(cause, e.getCause());
        assertEquals(cause.toString(), e.getMessage());
    }
}
