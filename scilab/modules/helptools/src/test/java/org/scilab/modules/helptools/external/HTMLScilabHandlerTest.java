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
 * Hermetic unit tests for {@link HTMLScilabHandler}'s identity contract.
 *
 * <p>Unlike the SVG/MathML handlers, the Scilab handler registers under the very
 * same namespace it inherits as {@code getScilabURI()} — that coincidence is
 * pinned here so a future change to either side cannot silently diverge. The
 * image-generation path needs a live converter and is out of scope.
 */
public class HTMLScilabHandlerTest {

    @Test
    public void uriIsTheScilabNamespace() {
        assertEquals("http://www.scilab.org", new HTMLScilabHandler("out", "base").getURI());
    }

    @Test
    public void uriMatchesTheInheritedScilabNamespaceConstant() {
        HTMLScilabHandler h = new HTMLScilabHandler("out", "base");
        assertEquals(h.getScilabURI(), h.getURI());
    }

    @Test
    public void constructorDoesNotThrowForTypicalPaths() {
        assertDoesNotThrow(() -> new HTMLScilabHandler("/tmp/out", "en_US"));
    }
}
