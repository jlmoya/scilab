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
 * Hermetic unit tests for {@link HTMLSVGHandler}'s identity contract.
 *
 * <p>{@link ExternalXMLHandler#getURI()} is the key a {@code DocbookTagConverter}
 * registers each external handler under, so it must be exactly the SVG namespace.
 * The constructor only concatenates path strings (no I/O), so it is safe to build
 * here; the image-generation path needs a live converter and is out of scope.
 */
public class HTMLSVGHandlerTest {

    @Test
    public void uriIsTheSvgNamespace() {
        assertEquals("http://www.w3.org/2000/svg", new HTMLSVGHandler("out", "base").getURI());
    }

    @Test
    public void inheritsTheScilabNamespaceConstant() {
        assertEquals("http://www.scilab.org", new HTMLSVGHandler("out", "base").getScilabURI());
    }

    @Test
    public void constructorDoesNotThrowForTypicalPaths() {
        assertDoesNotThrow(() -> new HTMLSVGHandler("/tmp/out", "fr_FR"));
    }
}
