/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Scilab Enterprises
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 *
 */

package org.scilab.modules.graphic_objects.xmlloader;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Hermetic unit tests for {@link CSSParserException}, a thin checked-exception
 * wrapper whose only behaviour is carrying a message up to {@link Exception}.
 */
public class CSSParserExceptionTest {

    @Test
    public void carriesTheMessage() {
        CSSParserException e = new CSSParserException("bad selector");
        assertEquals("bad selector", e.getMessage());
    }

    @Test
    public void isACheckedException() {
        CSSParserException e = new CSSParserException("x");
        assertTrue(e instanceof Exception);
        assertTrue(e instanceof Throwable);
        // It must NOT be an unchecked exception. CSSParserException extends
        // Exception directly, so it is a sibling of RuntimeException; an
        // `e instanceof RuntimeException` on the CSSParserException static type
        // is a compile-time error (JLS 15.20.2), hence the reflective form.
        assertFalse(RuntimeException.class.isInstance(e));
    }

    @Test
    public void hasNoCauseByDefault() {
        CSSParserException e = new CSSParserException("x");
        assertNull(e.getCause());
    }

    @Test
    public void acceptsNullMessage() {
        CSSParserException e = new CSSParserException(null);
        assertNull(e.getMessage());
    }

    @Test
    public void canBeThrownAndCaught() {
        CSSParserException thrown = assertThrows(CSSParserException.class, () -> {
            throw new CSSParserException("boom");
        });
        assertEquals("boom", thrown.getMessage());
    }
}
