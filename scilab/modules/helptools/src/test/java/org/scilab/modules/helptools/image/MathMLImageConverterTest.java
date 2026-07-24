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
 * Hermetic unit tests for {@link MathMLImageConverter}'s registry contract.
 *
 * <p>Pins the MIME key ({@code image/mathml}) and the regenerate-always policy.
 * The JEuclid rendering path is not exercised (it needs a real MathML document);
 * the constructor merely stashes its collaborator, so {@code null} suffices.
 */
public class MathMLImageConverterTest {

    @Test
    public void mimeTypeIsImageMathml() {
        assertEquals("image/mathml", new MathMLImageConverter(null).getMimeType());
    }

    @Test
    public void mathmlImagesAreAlwaysRegenerated() {
        assertTrue(new MathMLImageConverter(null).mustRegenerate());
    }
}
