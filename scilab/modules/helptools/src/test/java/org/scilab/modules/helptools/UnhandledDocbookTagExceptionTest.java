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

package org.scilab.modules.helptools;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

/**
 * Hermetic unit tests for {@link UnhandledDocbookTagException} — a thin
 * {@link SAXException} that formats a fixed message from the offending tag name.
 */
public class UnhandledDocbookTagExceptionTest {

    @Test
    public void messageEmbedsTheTagName() {
        UnhandledDocbookTagException e = new UnhandledDocbookTagException("foo");
        assertEquals("The tag foo is not handled.", e.getMessage());
    }

    @Test
    public void messageEmbedsADifferentTagName() {
        UnhandledDocbookTagException e = new UnhandledDocbookTagException("refentry");
        assertEquals("The tag refentry is not handled.", e.getMessage());
    }

    @Test
    public void isASaxException() {
        UnhandledDocbookTagException e = new UnhandledDocbookTagException("x");
        assertInstanceOf(SAXException.class, e);
    }

    @Test
    public void canBeCaughtAsSaxException() {
        SAXException caught = assertThrows(SAXException.class, () -> {
            throw new UnhandledDocbookTagException("bar");
        });
        assertEquals("The tag bar is not handled.", caught.getMessage());
    }
}
