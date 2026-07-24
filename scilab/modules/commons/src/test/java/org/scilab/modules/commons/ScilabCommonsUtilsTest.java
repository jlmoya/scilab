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

package org.scilab.modules.commons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for the pure helpers of {@link ScilabCommonsUtils}:
 * the MD5 digest formatter and the "current thread is the Scilab thread" registry.
 *
 * <p>The version getters and {@code getCorrectedPath} are intentionally NOT exercised:
 * they route through the native {@code ScilabCommons}/{@code ScilabConstants} layer.
 */
public class ScilabCommonsUtilsTest {

    // Canonical RFC 1321 / well-known MD5 vectors.
    @Test
    public void md5MatchesKnownVectors() {
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", ScilabCommonsUtils.getMD5(""));
        assertEquals("900150983cd24fb0d6963f7d28e17f72", ScilabCommonsUtils.getMD5("abc"));
        assertEquals("9e107d9d372bb6826bd81d3542a419d6",
                     ScilabCommonsUtils.getMD5("The quick brown fox jumps over the lazy dog"));
    }

    @Test
    public void md5IsAlwaysThirtyTwoLowercaseHexChars() {
        for (String s : new String[] {"", "a", "abc", "Scilab", "éèê"}) {
            String digest = ScilabCommonsUtils.getMD5(s);
            assertNotNull(digest);
            assertEquals(32, digest.length(), "wrong length for input <" + s + ">");
            assertTrue(digest.matches("[0-9a-f]{32}"), "not zero-padded lowercase hex: " + digest);
        }
    }

    @Test
    public void md5IsDeterministicAndDistinguishesInputs() {
        assertEquals(ScilabCommonsUtils.getMD5("repeat"), ScilabCommonsUtils.getMD5("repeat"));
        assertNotEquals(ScilabCommonsUtils.getMD5("abc"), ScilabCommonsUtils.getMD5("abd"));
    }

    @Test
    public void registerMakesTheCallingThreadTheScilabThread() {
        ScilabCommonsUtils.registerScilabThread();
        assertTrue(ScilabCommonsUtils.isScilabThread());
    }

    @Test
    public void aDifferentThreadIsNotTheScilabThread() throws InterruptedException {
        ScilabCommonsUtils.registerScilabThread();
        final boolean[] seenFromOtherThread = {true};
        Thread t = new Thread(() -> seenFromOtherThread[0] = ScilabCommonsUtils.isScilabThread());
        t.start();
        t.join();
        assertFalse(seenFromOtherThread[0]);
        // The registering thread is still recognised.
        assertTrue(ScilabCommonsUtils.isScilabThread());
    }

    @Test
    public void registerRebindsToTheMostRecentCaller() throws InterruptedException {
        ScilabCommonsUtils.registerScilabThread();          // this thread becomes the Scilab thread
        assertTrue(ScilabCommonsUtils.isScilabThread());
        Thread t = new Thread(ScilabCommonsUtils::registerScilabThread);
        t.start();
        t.join();
        // After another thread registers, this thread no longer matches.
        assertFalse(ScilabCommonsUtils.isScilabThread());
    }
}
