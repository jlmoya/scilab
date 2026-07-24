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

package org.scilab.modules.helptools.image;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link ScilabImageConverter}'s registry contract and
 * its empty-buffer guard.
 *
 * <p>Pins the MIME key ({@code image/scilab}) and the cacheable policy
 * ({@code mustRegenerate() == false}). {@code getFileWithScilabCode()} is tested
 * in its no-work state: a freshly built converter has an empty code buffer, so it
 * returns {@code null} without ever creating a temp file (and therefore without
 * touching the Scilab runtime). Actually running Scilab code is out of scope.
 */
public class ScilabImageConverterTest {

    @Test
    public void mimeTypeIsImageScilab() {
        assertEquals("image/scilab", new ScilabImageConverter(null).getMimeType());
    }

    @Test
    public void scilabImagesAreCacheableAcrossBuilds() {
        assertFalse(new ScilabImageConverter(null).mustRegenerate());
    }

    @Test
    public void getFileWithScilabCodeReturnsNullWhenNoCodeAccumulated() {
        // Empty buffer => short-circuits to null before any temp file / engine work.
        assertNull(new ScilabImageConverter(null).getFileWithScilabCode());
    }
}
