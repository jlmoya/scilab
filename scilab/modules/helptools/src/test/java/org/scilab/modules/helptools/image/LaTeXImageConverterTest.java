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
 * Hermetic unit tests for {@link LaTeXImageConverter}'s registry contract.
 *
 * <p>The MIME type is the key under which {@code ImageConverter} registers this
 * converter, and {@code mustRegenerate()} tells the help build whether a cached
 * PNG may be reused. Both are constant, side-effect-free, and independent of the
 * (heavyweight) DocbookTagConverter collaborator — so a {@code null} collaborator
 * is enough to pin them. The actual LaTeX-to-PNG rendering is out of scope here.
 */
public class LaTeXImageConverterTest {

    @Test
    public void mimeTypeIsImageLatex() {
        assertEquals("image/latex", new LaTeXImageConverter(null).getMimeType());
    }

    @Test
    public void latexImagesAreAlwaysRegenerated() {
        // LaTeX output is not cached across builds.
        assertTrue(new LaTeXImageConverter(null).mustRegenerate());
    }
}
