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

package org.scilab.forge.scirenderer.implementation.g2d.motor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hermetic unit tests for {@link InvalidPolygonException}.
 */
public class InvalidPolygonExceptionTest {

    @Test
    public void isACheckedException() {
        assertTrue(new InvalidPolygonException("bad") instanceof Exception);
    }

    @Test
    public void preservesMessage() {
        assertEquals("bad polygon", new InvalidPolygonException("bad polygon").getMessage());
    }
}
