/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Scilab
 *
 * Hermetic JUnit 6 unit tests for the ui_data module.
 * This file is hereby licensed under the terms of the GNU GPL v2.0.
 */

package org.scilab.modules.ui_data.filebrowser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests the pure filesystem helpers of {@link FileUtils}: extension extraction and
 * binary-vs-text detection. Real files are created under a JUnit {@code @TempDir}, so the
 * tests touch no Scilab runtime and no network. (FileUtils' static initializer resolves
 * icon paths against the {@code SCI} tree, which the surefire config supplies.)
 */
public class FileUtilsTest {

    @TempDir
    File tempDir;

    private File writeBytes(String name, byte[] bytes) throws IOException {
        File f = new File(tempDir, name);
        Files.write(f.toPath(), bytes);
        return f;
    }

    private File writeText(String name, String text) throws IOException {
        return writeBytes(name, text.getBytes(StandardCharsets.UTF_8));
    }

    // ---- getFileExtension ----

    @Test
    public void extensionIsTheTextAfterTheLastDot() throws IOException {
        assertEquals("csv", FileUtils.getFileExtension(writeText("data.csv", "x")));
        assertEquals("gz", FileUtils.getFileExtension(writeText("archive.tar.gz", "x")));
    }

    @Test
    public void noDotMeansNoExtension() throws IOException {
        assertEquals("", FileUtils.getFileExtension(writeText("README", "x")));
    }

    @Test
    public void trailingDotYieldsEmptyExtension() throws IOException {
        assertEquals("", FileUtils.getFileExtension(writeText("weird.", "x")));
    }

    @Test
    public void leadingDotIsTreatedAsExtension() throws IOException {
        // lastIndexOf('.') == 0, so substring(1) returns the remainder.
        assertEquals("hidden", FileUtils.getFileExtension(writeText(".hidden", "x")));
    }

    @Test
    public void directoryHasNoExtensionBecauseItIsNotAFile() {
        File dir = new File(tempDir, "sub.dir");
        assertTrue(dir.mkdir());
        assertEquals("", FileUtils.getFileExtension(dir));
    }

    @Test
    public void nonExistentFileHasNoExtension() {
        // getFileExtension guards on isFile(), which is false for a missing path.
        assertEquals("", FileUtils.getFileExtension(new File(tempDir, "ghost.txt")));
    }

    // ---- isBinaryFile ----

    @Test
    public void plainTextFileIsNotBinary() throws IOException {
        assertFalse(FileUtils.isBinaryFile(writeText("notes.txt", "hello world\nsecond line")));
    }

    @Test
    public void fileContainingANulByteIsBinary() throws IOException {
        assertTrue(FileUtils.isBinaryFile(writeBytes("blob.bin", new byte[] {'a', 'b', 0, 'c'})));
    }

    @Test
    public void emptyFileIsNotBinary() throws IOException {
        assertFalse(FileUtils.isBinaryFile(writeBytes("empty", new byte[0])));
    }

    @Test
    public void directoryIsNotBinary() {
        File dir = new File(tempDir, "aDir");
        assertTrue(dir.mkdir());
        assertFalse(FileUtils.isBinaryFile(dir));
    }
}
