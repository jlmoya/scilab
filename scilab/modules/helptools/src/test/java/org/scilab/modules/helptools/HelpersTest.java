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

package org.scilab.modules.helptools;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Hermetic unit tests for {@link Helpers}, the module's static utility grab-bag.
 *
 * <p>Only the pure-Java surface is exercised — string/path helpers, {@code split},
 * XML escaping and file round-tripping through a JUnit {@code @TempDir}. The
 * process-spawning helpers ({@code shellStart}/{@code shellExec}/{@code findInPath})
 * are deliberately out of scope: they depend on the host OS and environment.
 */
public class HelpersTest {

    // ---- reason ---------------------------------------------------------

    @Test
    public void reasonReturnsMessageWhenPresent() {
        assertEquals("boom", Helpers.reason(new IllegalStateException("boom")));
    }

    @Test
    public void reasonFallsBackToClassNameWhenMessageNull() {
        // A Throwable with no message => the fully-qualified class name is used.
        assertEquals("java.lang.IllegalStateException",
                     Helpers.reason(new IllegalStateException()));
    }

    // ---- escapeXML ------------------------------------------------------

    @Test
    public void escapeXmlEscapesAllFiveEntitiesWithNamedForms() {
        StringWriter sw = new StringWriter();
        PrintWriter out = new PrintWriter(sw);
        Helpers.escapeXML("<a href='x'>&\"b", out);
        out.flush();
        // Helpers uses NAMED entities (contrast the code handlers, which use numeric).
        assertEquals("&lt;a href=&apos;x&apos;&gt;&amp;&quot;b", sw.toString());
    }

    @Test
    public void escapeXmlLeavesOrdinaryCharactersUntouched() {
        StringWriter sw = new StringWriter();
        PrintWriter out = new PrintWriter(sw);
        Helpers.escapeXML("hello world 123", out);
        out.flush();
        assertEquals("hello world 123", sw.toString());
    }

    @Test
    public void escapeXmlCharArrayHonoursOffsetAndLength() {
        char[] chars = "ab<cd".toCharArray();
        StringWriter sw = new StringWriter();
        PrintWriter out = new PrintWriter(sw);
        // Only the single '<' at index 2 is in range.
        Helpers.escapeXML(chars, 2, 1, out);
        out.flush();
        assertEquals("&lt;", sw.toString());
    }

    @Test
    public void escapeXmlEmptyStringProducesNothing() {
        StringWriter sw = new StringWriter();
        PrintWriter out = new PrintWriter(sw);
        Helpers.escapeXML("", out);
        out.flush();
        assertEquals("", sw.toString());
    }

    // ---- getBaseName / getTemporaryNameFo -------------------------------

    @Test
    public void getBaseNameWrapsLanguage() {
        assertEquals("scilab_fr_FR_help", Helpers.getBaseName("fr_FR"));
        assertEquals("scilab_en_US_help", Helpers.getBaseName("en_US"));
    }

    @Test
    public void getTemporaryNameFoAppendsDocFo() {
        assertEquals("/base/__doc.fo", Helpers.getTemporaryNameFo("/base"));
    }

    // ---- getFileExtension ----------------------------------------------

    @Test
    public void getFileExtensionSimple() {
        assertEquals("txt", Helpers.getFileExtension("file.txt"));
    }

    @Test
    public void getFileExtensionCompoundReturnsFromFirstDot() {
        // Characterizes indexOfDot: it anchors on the FIRST dot of the base name,
        // so a compound suffix is returned whole (not just the last segment).
        assertEquals("tar.gz", Helpers.getFileExtension("archive.tar.gz"));
    }

    @Test
    public void getFileExtensionNoDotIsNull() {
        assertNull(Helpers.getFileExtension("README"));
    }

    @Test
    public void getFileExtensionLeadingDotIsNull() {
        // A leading dot (dot-file) is not treated as an extension separator.
        assertNull(Helpers.getFileExtension(".bashrc"));
    }

    @Test
    public void getFileExtensionIgnoresDotInDirectorySegment() {
        String path = "a.b" + File.separator + "file";
        assertNull(Helpers.getFileExtension(path));
    }

    @Test
    public void getFileExtensionFileOverloadUsesPath() {
        File f = new File("dir" + File.separator + "note.md");
        assertEquals("md", Helpers.getFileExtension(f));
    }

    // ---- setFileExtension ----------------------------------------------

    @Test
    public void setFileExtensionReplacesExisting() {
        assertEquals("file.png", Helpers.setFileExtension("file.txt", "png"));
    }

    @Test
    public void setFileExtensionCompoundReplacesFromFirstDot() {
        assertEquals("archive.zip", Helpers.setFileExtension("archive.tar.gz", "zip"));
    }

    @Test
    public void setFileExtensionAddsWhenNoDot() {
        assertEquals("noext.png", Helpers.setFileExtension("noext", "png"));
    }

    @Test
    public void setFileExtensionNullExtensionStripsExisting() {
        assertEquals("file", Helpers.setFileExtension("file.txt", null));
    }

    @Test
    public void setFileExtensionNullExtensionNoDotUnchanged() {
        assertEquals("noext", Helpers.setFileExtension("noext", null));
    }

    @Test
    public void setFileExtensionTrailingSeparatorUnchanged() {
        String dir = "somedir" + File.separator;
        assertEquals(dir, Helpers.setFileExtension(dir, "png"));
    }

    @Test
    public void setFileExtensionFileOverloadReturnsFile() {
        File out = Helpers.setFileExtension(new File("a" + File.separator + "b.txt"), "css");
        assertEquals("b.css", out.getName());
    }

    // ---- split ----------------------------------------------------------

    @Test
    public void splitBasic() {
        assertArrayEquals(new String[] {"a", "b", "c"}, Helpers.split("a,b,c", ','));
    }

    @Test
    public void splitPreservesEmptyInnerFields() {
        assertArrayEquals(new String[] {"a", "", "c"}, Helpers.split("a,,c", ','));
    }

    @Test
    public void splitPreservesLeadingEmptyField() {
        assertArrayEquals(new String[] {"", "a"}, Helpers.split(",a", ','));
    }

    @Test
    public void splitPreservesTrailingEmptyField() {
        assertArrayEquals(new String[] {"a", ""}, Helpers.split("a,", ','));
    }

    @Test
    public void splitWithoutSeparatorReturnsSingleton() {
        assertArrayEquals(new String[] {"abc"}, Helpers.split("abc", ','));
    }

    @Test
    public void splitEmptyStringReturnsSingleEmpty() {
        assertArrayEquals(new String[] {""}, Helpers.split("", ','));
    }

    // ---- IS_WINDOWS -----------------------------------------------------

    @Test
    public void isWindowsTracksPathSeparator() {
        // The flag is defined purely from the platform path separator char.
        assertEquals(File.pathSeparatorChar == ';', Helpers.IS_WINDOWS);
    }

    // ---- file round-trips (hermetic via @TempDir) -----------------------

    @Test
    public void saveAndLoadStringRoundTripsUtf8ByDefault(@TempDir Path tmp) throws Exception {
        File f = tmp.resolve("round.txt").toFile();
        String content = "héllo • world"; // includes multi-byte UTF-8
        Helpers.saveString(content, f, null);
        assertTrue(f.isFile());
        assertEquals(content, Helpers.loadString(f, null));
    }

    @Test
    public void saveAndLoadStringRoundTripsWithExplicitCharset(@TempDir Path tmp) throws Exception {
        File f = tmp.resolve("round2.txt").toFile();
        String content = "explicit charset";
        Helpers.saveString(content, f, "UTF-8");
        assertEquals(content, Helpers.loadString(f, "UTF-8"));
    }

    @Test
    public void copyFileReplicatesContent(@TempDir Path tmp) throws Exception {
        File src = tmp.resolve("src.txt").toFile();
        File dst = tmp.resolve("dst.txt").toFile();
        Helpers.saveString("payload", src, null);
        Helpers.copyFile(src, dst);
        assertTrue(dst.isFile());
        assertEquals("payload", Helpers.loadString(dst, null));
    }

    @Test
    public void deleteDirectoryRemovesNestedTree(@TempDir Path tmp) throws Exception {
        File root = tmp.resolve("tree").toFile();
        File sub = new File(root, "sub");
        assertTrue(sub.mkdirs());
        Helpers.saveString("x", new File(root, "a.txt"), null);
        Helpers.saveString("y", new File(sub, "b.txt"), null);

        Helpers.deleteDirectory(root);

        assertFalse(root.exists(), "deleteDirectory must recursively remove the whole tree");
    }

    @Test
    public void deleteDirectoryOnMissingPathDoesNotThrow(@TempDir Path tmp) {
        File missing = tmp.resolve("does-not-exist").toFile();
        // list() returns null for a non-directory; the helper must tolerate it.
        assertDoesNotThrow(() -> Helpers.deleteDirectory(missing));
    }
}
