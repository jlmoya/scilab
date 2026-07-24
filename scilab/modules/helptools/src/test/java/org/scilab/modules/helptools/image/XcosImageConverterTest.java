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
 * Hermetic unit tests for {@link XcosImageConverter}'s registry contract.
 *
 * <p>Pins the MIME key ({@code image/scilab-xcos}) and the cacheable policy
 * ({@code mustRegenerate() == false}). The actual export delegates to xcos via
 * reflection ("to avoid a static dependency") and is not exercised here — the
 * constructor only stores its collaborator, so {@code null} is sufficient.
 */
public class XcosImageConverterTest {

    @Test
    public void mimeTypeIsImageScilabXcos() {
        assertEquals("image/scilab-xcos", new XcosImageConverter(null).getMimeType());
    }

    @Test
    public void xcosImagesAreCacheableAcrossBuilds() {
        assertFalse(new XcosImageConverter(null).mustRegenerate());
    }
}
