/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Scilab test coverage
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 *
 */

package org.scilab.modules.xcos.utils;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileNotFoundException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Hermetic unit tests for {@link FileUtils}.
 *
 * <p>Covered without the native runtime: the {@code STYLE_FILENAME} constant,
 * {@link FileUtils#toValidCIdentifier(String)} (pure string transformation over
 * jgraphx' {@code mxUtils.getBodyMarkup}), and the file primitives
 * {@code copy}/{@code forceCopy}/{@code delete}/{@code exists} exercised against
 * a JUnit {@link TempDir}.</p>
 *
 * <p><b>Deliberately not covered</b> (they require the Scilab native runtime and
 * would abort a hermetic JVM):</p>
 * <ul>
 *   <li>{@code decodeStyle(mxStylesheet)} dereferences {@code ScilabConstants.SCI}
 *       / {@code SCIHOME}, resolved through JNI.</li>
 *   <li>The <em>failure</em> branch of {@code delete(...)} logs
 *       {@code XcosMessages.UNABLE_TO_DELETE}, whose class initializer calls the
 *       JNI-backed {@code Messages.gettext}. Only the success path is exercised,
 *       so that class is never loaded.</li>
 * </ul>
 */
public class FileUtilsTest {

    private static File writeFile(Path dir, String name, String content) throws Exception {
        Path p = dir.resolve(name);
        Files.write(p, content.getBytes(UTF_8));
        return p.toFile();
    }

    private static String read(File f) throws Exception {
        return new String(Files.readAllBytes(f.toPath()), UTF_8);
    }

    /* ---- constant + class structure ---- */

    @Test
    public void styleFilenameConstant() {
        assertEquals("Xcos-style.xml", FileUtils.STYLE_FILENAME);
    }

    @Test
    public void classIsFinalWithPrivateConstructor() throws NoSuchMethodException {
        assertTrue(Modifier.isFinal(FileUtils.class.getModifiers()));
        Constructor<FileUtils> ctor = FileUtils.class.getDeclaredConstructor();
        assertTrue(Modifier.isPrivate(ctor.getModifiers()));
    }

    /* ---- toValidCIdentifier ---- */

    @Test
    public void identifierPassesThroughPlainAlphanumerics() {
        assertEquals("Hello", FileUtils.toValidCIdentifier("Hello"));
        assertEquals("MixedCASE123", FileUtils.toValidCIdentifier("MixedCASE123"));
        assertEquals("a1", FileUtils.toValidCIdentifier("a1"));
    }

    @Test
    public void identifierTurnsSpacesIntoUnderscores() {
        assertEquals("My_Block", FileUtils.toValidCIdentifier("My Block"));
        assertEquals("multi_space", FileUtils.toValidCIdentifier("multi   space"));
    }

    @Test
    public void identifierTurnsHyphenAndUnderscoreIntoSingleUnderscore() {
        assertEquals("a_b_c", FileUtils.toValidCIdentifier("a-b_c"));
        assertEquals("under_score", FileUtils.toValidCIdentifier("under_score"));
    }

    @Test
    public void identifierCollapsesConsecutiveSeparators() {
        assertEquals("a_b", FileUtils.toValidCIdentifier("a__b"));
        assertEquals("a_b", FileUtils.toValidCIdentifier("a-_-b"));
        assertEquals("mix_end", FileUtils.toValidCIdentifier("mix-_ end"));
    }

    @Test
    public void identifierDropsLeadingDigits() {
        assertEquals("abc", FileUtils.toValidCIdentifier("1abc"));
        assertEquals("", FileUtils.toValidCIdentifier("123"));
    }

    @Test
    public void identifierDropsLeadingSeparators() {
        // C identifiers may not start with '_' derived from a leading separator.
        assertEquals("x", FileUtils.toValidCIdentifier("_x"));
        assertEquals("start", FileUtils.toValidCIdentifier("-start"));
        assertEquals("leading", FileUtils.toValidCIdentifier(" leading"));
    }

    @Test
    public void identifierKeepsTrailingSeparatorAsUnderscore() {
        assertEquals("trailing_", FileUtils.toValidCIdentifier("trailing_"));
        assertEquals("trailing_", FileUtils.toValidCIdentifier("trailing "));
    }

    @Test
    public void identifierDropsNonAsciiAndSpecialCharacters() {
        assertEquals("caf", FileUtils.toValidCIdentifier("café"));
        assertEquals("", FileUtils.toValidCIdentifier("!@#$%"));
    }

    @Test
    public void identifierOfEmptyOrBlankIsEmpty() {
        assertEquals("", FileUtils.toValidCIdentifier(""));
        assertEquals("", FileUtils.toValidCIdentifier("   "));
    }

    @Test
    public void identifierLinefeedBecomesLiteralBr_defectCharacterization() {
        // getBodyMarkup(label, true) rewrites '\n' to the literal "<br>"; the
        // angle brackets are then stripped, leaving the letters "br" embedded in
        // the identifier. This documents current behavior, not an endorsement.
        assertEquals("line1brline2", FileUtils.toValidCIdentifier("line1\nline2"));
    }

    /* ---- copy ---- */

    @Test
    public void copyReproducesSourceContent(@TempDir Path dir) throws Exception {
        File in = writeFile(dir, "in.txt", "hello world");
        File out = dir.resolve("out.txt").toFile();

        FileUtils.copy(in, out);

        assertTrue(out.exists());
        assertEquals("hello world", read(out));
    }

    @Test
    public void copyOverwritesExistingDestination(@TempDir Path dir) throws Exception {
        File in = writeFile(dir, "in.txt", "new");
        File out = writeFile(dir, "out.txt", "old-and-longer");

        FileUtils.copy(in, out);

        assertEquals("new", read(out));
    }

    @Test
    public void copyOfEmptySourceProducesEmptyDestination(@TempDir Path dir) throws Exception {
        File in = writeFile(dir, "empty.txt", "");
        File out = dir.resolve("out.txt").toFile();

        FileUtils.copy(in, out);

        assertTrue(out.exists());
        assertEquals(0L, Files.size(out.toPath()));
    }

    @Test
    public void copyPreservesRawBytes(@TempDir Path dir) throws Exception {
        byte[] payload = {0, 1, 2, (byte) 0xFF, 10, 13, 65};
        Path inPath = dir.resolve("bin.dat");
        Files.write(inPath, payload);
        File out = dir.resolve("bin-out.dat").toFile();

        FileUtils.copy(inPath.toFile(), out);

        assertArrayEquals(payload, Files.readAllBytes(out.toPath()));
    }

    @Test
    public void copyFromMissingSourceThrows(@TempDir Path dir) {
        File missing = dir.resolve("does-not-exist.txt").toFile();
        File out = dir.resolve("out.txt").toFile();

        assertThrows(FileNotFoundException.class, () -> FileUtils.copy(missing, out));
        // The output stream is never opened, so no destination is left behind.
        assertFalse(out.exists());
    }

    /* ---- forceCopy ---- */

    @Test
    public void forceCopyCreatesMissingDestinationAndCopies(@TempDir Path dir) throws Exception {
        File in = writeFile(dir, "in.txt", "payload");
        File out = dir.resolve("created.txt").toFile();
        assertFalse(out.exists());

        FileUtils.forceCopy(in, out);

        assertTrue(out.exists());
        assertEquals("payload", read(out));
    }

    @Test
    public void forceCopyOverwritesExistingDestination(@TempDir Path dir) throws Exception {
        File in = writeFile(dir, "in.txt", "fresh");
        File out = writeFile(dir, "out.txt", "stale-and-longer");

        FileUtils.forceCopy(in, out);

        assertEquals("fresh", read(out));
    }

    @Test
    public void forceCopyFromMissingSourceIsSwallowed_defectCharacterization(@TempDir Path dir) throws Exception {
        File missing = dir.resolve("nope.txt").toFile();
        File out = dir.resolve("out.txt").toFile();

        // Unlike copy(), forceCopy() swallows the FileNotFoundException (logs a
        // warning only). It still creates the empty destination beforehand.
        FileUtils.forceCopy(missing, out);

        assertTrue(out.exists(), "forceCopy creates the destination before failing");
        assertEquals(0L, Files.size(out.toPath()), "destination is left empty");
    }

    /* ---- exists ---- */

    @Test
    public void existsReflectsThePresenceOfAFile(@TempDir Path dir) throws Exception {
        File f = writeFile(dir, "present.txt", "x");
        assertTrue(FileUtils.exists(f.getAbsolutePath()));
        assertFalse(FileUtils.exists(dir.resolve("absent.txt").toFile().getAbsolutePath()));
    }

    /* ---- delete (success path only; see class javadoc) ---- */

    @Test
    public void deleteRemovesAnExistingFileByHandle(@TempDir Path dir) throws Exception {
        File f = writeFile(dir, "victim.txt", "bye");
        assertTrue(f.exists());

        FileUtils.delete(f);

        assertFalse(f.exists());
    }

    @Test
    public void deleteRemovesAnExistingFileByPath(@TempDir Path dir) throws Exception {
        File f = writeFile(dir, "victim.txt", "bye");
        assertTrue(f.exists());

        FileUtils.delete(f.getAbsolutePath());

        assertFalse(f.exists());
    }
}
