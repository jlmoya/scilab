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

package org.scilab.modules.external_objects_java;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Hermetic unit tests for {@link ScilabJarCreator#createJarArchive}. It is pure local-file
 * I/O (no Scilab, no native), so it runs against a JUnit {@link TempDir}. The tests pin the
 * observable contract of the packer: recursive directory expansion, common-root relativized
 * entry names, an explicit root path, a generated manifest, and the {@link ScilabJavaException}
 * wrapping of an unwritable destination.
 */
public class ScilabJarCreatorTest {

    private static Path writeFile(Path p, String content) throws Exception {
        Files.createDirectories(p.getParent());
        Files.write(p, content.getBytes(UTF_8));
        return p;
    }

    private static byte[] entryBytes(JarFile jf, String name) throws Exception {
        JarEntry e = jf.getJarEntry(name);
        assertNotNull(e, "expected jar entry: " + name);
        try (InputStream in = jf.getInputStream(e)) {
            return in.readAllBytes();
        }
    }

    @Test
    public void jarsADirectoryTreeWithRelativeEntryNames(@TempDir Path tmp) throws Exception {
        Path src = tmp.resolve("src");
        writeFile(src.resolve("a.txt"), "AAA");
        writeFile(src.resolve("b.txt"), "BBB");
        writeFile(src.resolve("sub/c.txt"), "CCC");

        Path jar = tmp.resolve("out.jar");
        int rc = ScilabJarCreator.createJarArchive(jar.toString(), new String[] {src.toString()}, "", null, false);
        assertEquals(0, rc);

        try (JarFile jf = new JarFile(jar.toFile())) {
            assertNotNull(jf.getManifest(), "a manifest is generated when none is supplied");
            assertArrayEquals("AAA".getBytes(UTF_8), entryBytes(jf, "a.txt"));
            assertArrayEquals("BBB".getBytes(UTF_8), entryBytes(jf, "b.txt"));
            // Nested files keep their path relative to the common root, with forward slashes.
            assertArrayEquals("CCC".getBytes(UTF_8), entryBytes(jf, "sub/c.txt"));
        }
    }

    @Test
    public void honorsAnExplicitRootPathForEntryNames(@TempDir Path tmp) throws Exception {
        Path root = tmp.resolve("root");
        Path a = writeFile(root.resolve("x/a.txt"), "1");
        Path b = writeFile(root.resolve("y/b.txt"), "2");

        Path jar = tmp.resolve("rooted.jar");
        int rc = ScilabJarCreator.createJarArchive(jar.toString(),
                 new String[] {a.toString(), b.toString()}, root.toString(), null, false);
        assertEquals(0, rc);

        try (JarFile jf = new JarFile(jar.toFile())) {
            assertArrayEquals("1".getBytes(UTF_8), entryBytes(jf, "x/a.txt"));
            assertArrayEquals("2".getBytes(UTF_8), entryBytes(jf, "y/b.txt"));
        }
    }

    @Test
    public void unwritableDestinationIsWrappedInScilabJavaException(@TempDir Path tmp) throws Exception {
        Path src = writeFile(tmp.resolve("src/a.txt"), "AAA");
        // Destination sits under a directory that does not exist -> FileOutputStream fails.
        Path badJar = tmp.resolve("missing-dir/out.jar");

        assertThrows(ScilabJavaException.class,
                     () -> ScilabJarCreator.createJarArchive(badJar.toString(),
                             new String[] {src.toString()}, "", null, false));
    }
}
