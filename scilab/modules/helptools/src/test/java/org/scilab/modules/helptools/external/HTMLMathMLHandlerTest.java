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

package org.scilab.modules.helptools.external;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link HTMLMathMLHandler}'s identity contract — the
 * MathML namespace it registers under, and the inherited Scilab namespace. The
 * rendering path (which needs a live converter) is out of scope.
 */
public class HTMLMathMLHandlerTest {

    @Test
    public void uriIsTheMathmlNamespace() {
        assertEquals("http://www.w3.org/1998/Math/MathML",
                     new HTMLMathMLHandler("out", "base").getURI());
    }

    @Test
    public void inheritsTheScilabNamespaceConstant() {
        assertEquals("http://www.scilab.org", new HTMLMathMLHandler("out", "base").getScilabURI());
    }

    @Test
    public void constructorDoesNotThrowForTypicalPaths() {
        assertDoesNotThrow(() -> new HTMLMathMLHandler("/tmp/out", "ja_JP"));
    }
}
