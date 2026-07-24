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

package org.scilab.forge.scirenderer.tranformations;

import org.junit.jupiter.api.Test;
import org.scilab.forge.scirenderer.SciRendererException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hermetic unit tests for {@link DegenerateMatrixException}.
 */
public class DegenerateMatrixExceptionTest {

    @Test
    public void isASciRendererException() {
        DegenerateMatrixException e = new DegenerateMatrixException("singular");
        assertTrue(e instanceof SciRendererException);
    }

    @Test
    public void preservesMessage() {
        assertEquals("singular matrix", new DegenerateMatrixException("singular matrix").getMessage());
    }
}
