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

package org.scilab.modules.jvm;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link LibraryPath}.
 *
 * <p>The observable, portable half of this class is pure string plumbing over the
 * {@code java.library.path} system property: {@code getLibraryPath()} splits it and
 * {@code addPath()} appends to it (skipping case-insensitive duplicates). Every test snapshots
 * that property in {@code @BeforeEach} and fully restores it in {@code @AfterEach}, so the
 * global property is never left mutated.
 *
 * <p>{@code addPath()} additionally best-effort patches the JDK's internal native-library
 * search array through {@code Unsafe}; under a stock (non-{@code --add-exports}) test JVM that
 * reflective step throws and is swallowed by the method's {@code catch (Throwable)} — the
 * property update, which happens first, still stands, which is exactly what these tests assert.
 */
public class LibraryPathTest {

    private static final String KEY = "java.library.path";
    private String saved;

    @BeforeEach
    public void snapshot() {
        saved = System.getProperty(KEY);
    }

    @AfterEach
    public void restore() {
        if (saved == null) {
            System.clearProperty(KEY);
        } else {
            System.setProperty(KEY, saved);
        }
    }

    @Test
    public void constructorIsForbidden() {
        // The protected no-arg constructor is a guard and always throws (utility class).
        assertThrows(UnsupportedOperationException.class, () -> new LibraryPath());
    }

    @Test
    public void getLibraryPathSplitsOnPathSeparator() {
        String p = "alpha" + File.pathSeparator + "beta" + File.pathSeparator + "gamma";
        System.setProperty(KEY, p);
        assertArrayEquals(new String[] {"alpha", "beta", "gamma"}, LibraryPath.getLibraryPath());
    }

    @Test
    public void getLibraryPathReturnsSingletonForNoSeparator() {
        System.setProperty(KEY, "solo");
        assertArrayEquals(new String[] {"solo"}, LibraryPath.getLibraryPath());
    }

    @Test
    public void addPathAppendsNewEntryAtEnd() throws Exception {
        String base = "/base/one" + File.pathSeparator + "/base/two";
        System.setProperty(KEY, base);

        LibraryPath.addPath("/base/unique-xyz");

        assertEquals(base + File.pathSeparator + "/base/unique-xyz", System.getProperty(KEY));
        String[] paths = LibraryPath.getLibraryPath();
        assertEquals("/base/unique-xyz", paths[paths.length - 1]);
    }

    @Test
    public void addPathSkipsExactDuplicate() throws Exception {
        String base = "/dup/a" + File.pathSeparator + "/dup/b";
        System.setProperty(KEY, base);

        LibraryPath.addPath("/dup/b");

        assertEquals(base, System.getProperty(KEY)); // already present -> unchanged
    }

    @Test
    public void addPathDeduplicationIsCaseInsensitive() throws Exception {
        // pathAlreadyExists() uses equalsIgnoreCase(), so a differently-cased path is a dup.
        String base = "/Case/Sensitive/Path";
        System.setProperty(KEY, base);

        LibraryPath.addPath("/case/sensitive/path");

        assertEquals(base, System.getProperty(KEY)); // treated as duplicate -> not appended
    }
}
