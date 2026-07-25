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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Hermetic unit tests for the pure helpers of {@link ScilabCommonsUtils}:
 * the MD5 digest formatter, the {@code copyFile} byte-copier, the "current thread is the
 * Scilab thread" registry, and the display-independent branches of {@code getCorrectedPath}.
 *
 * <p>The version getters are intentionally NOT exercised: they route through the native
 * {@code ScilabCommons} layer, whose functions dereference uninitialized engine state and
 * hard-crash (SIGSEGV) the test JVM when no live Scilab is attached.
 *
 * <p>{@code getCorrectedPath} is only ever called here with paths that do NOT start with the
 * {@code ~}, {@code SCI}, {@code SCIHOME} or {@code TMPDIR} tokens. Those tokens are the only
 * branches that touch {@code ScilabConstants}/{@code ScilabCommons}; by the JLS, a class is
 * initialized only when such a branch actually executes, so a non-token path returns without
 * ever loading {@code ScilabConstants} — keeping these tests hermetic and crash-free.
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

    // ----------------------------------------------------------------- copyFile

    @Test
    public void copyFileDuplicatesContentByteForByteAndReportsSuccess(@TempDir Path tmp) throws Exception {
        File in = tmp.resolve("in.txt").toFile();
        File out = tmp.resolve("out.txt").toFile();
        byte[] payload = "hello copyFile — accents: éèê, bytes 0..3".getBytes(StandardCharsets.UTF_8);
        Files.write(in.toPath(), payload);

        assertTrue(ScilabCommonsUtils.copyFile(in, out));
        assertTrue(out.exists());
        assertArrayEquals(payload, Files.readAllBytes(out.toPath()));
    }

    @Test
    public void copyFileTruncatesAndOverwritesAnExistingDestination(@TempDir Path tmp) throws Exception {
        File in = tmp.resolve("in.txt").toFile();
        File out = tmp.resolve("out.txt").toFile();
        Files.write(in.toPath(), "short".getBytes(StandardCharsets.UTF_8));
        Files.write(out.toPath(), "a much longer pre-existing content".getBytes(StandardCharsets.UTF_8));

        assertTrue(ScilabCommonsUtils.copyFile(in, out));
        assertEquals("short", new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8));
    }

    @Test
    public void copyFileHandlesAnEmptySource(@TempDir Path tmp) throws Exception {
        File in = tmp.resolve("empty.bin").toFile();
        File out = tmp.resolve("copy.bin").toFile();
        Files.write(in.toPath(), new byte[0]);

        assertTrue(ScilabCommonsUtils.copyFile(in, out));
        assertTrue(out.exists());
        assertEquals(0, out.length());
    }

    @Test
    public void copyFileReturnsFalseWhenTheSourceIsMissing(@TempDir Path tmp) {
        File missing = tmp.resolve("does-not-exist.txt").toFile();
        File out = tmp.resolve("out.txt").toFile();
        assertFalse(ScilabCommonsUtils.copyFile(missing, out));
    }

    // ----------------------------------------------------------------- getCorrectedPath (non-token paths only)

    @Test
    public void getCorrectedPathReturnsANonTokenPathUnchanged() {
        assertEquals("foo/bar/baz.xml", ScilabCommonsUtils.getCorrectedPath("foo/bar/baz.xml"));
        // "SCIL" is not the "SCI" token (no "SCI/" prefix, not equal to "SCI"), so it is untouched.
        assertEquals("SCILAB_like/name", ScilabCommonsUtils.getCorrectedPath("SCILAB_like/name"));
    }

    @Test
    public void getCorrectedPathTrimsSurroundingWhitespace() {
        assertEquals("relative/dir", ScilabCommonsUtils.getCorrectedPath("   relative/dir   "));
    }

    @Test
    public void getCorrectedPathReturnsEmptyStringForEmptyInput() {
        assertEquals("", ScilabCommonsUtils.getCorrectedPath(""));
        assertEquals("", ScilabCommonsUtils.getCorrectedPath("    "));
    }

    @Test
    public void getCorrectedPathThrowsOnNull() {
        // Characterizes current behaviour: path.trim() runs before the null-guard, so a null
        // argument dereferences immediately.
        assertThrows(NullPointerException.class, () -> ScilabCommonsUtils.getCorrectedPath(null));
    }
}
