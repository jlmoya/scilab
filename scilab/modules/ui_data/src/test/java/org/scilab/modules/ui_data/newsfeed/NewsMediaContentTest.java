/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Scilab
 *
 * Hermetic JUnit 6 unit tests for the ui_data module.
 * This file is hereby licensed under the terms of the GNU GPL v2.0.
 */

package org.scilab.modules.ui_data.newsfeed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * Tests {@link NewsMediaContent}: an immutable holder of a media url/width/height triple.
 * The getters are package-private, hence this test lives in the same package.
 */
public class NewsMediaContentTest {

    @Test
    public void gettersReturnConstructorArguments() {
        NewsMediaContent media = new NewsMediaContent("https://x/img.png", "80", "60");

        assertEquals("https://x/img.png", media.getURL());
        assertEquals("80", media.getWidth());
        assertEquals("60", media.getHeight());
    }

    @Test
    public void nullFieldsAreStoredAsIs() {
        NewsMediaContent media = new NewsMediaContent(null, null, null);

        assertNull(media.getURL());
        assertNull(media.getWidth());
        assertNull(media.getHeight());
    }

    @Test
    public void fieldsAreIndependent() {
        // The three arguments must map to their own accessors, not be mixed up.
        NewsMediaContent media = new NewsMediaContent("u", "w", "h");
        assertEquals("u", media.getURL());
        assertEquals("w", media.getWidth());
        assertEquals("h", media.getHeight());
    }
}
