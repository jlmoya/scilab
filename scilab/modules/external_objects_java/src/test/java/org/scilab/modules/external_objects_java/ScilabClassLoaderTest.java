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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link ScilabClassLoader#loadJavaClass}. Loading by name resolves
 * through the ordinary system class loader, so JDK classes on the test classpath load
 * without any Scilab runtime. The result is registered as a {@link ScilabJavaClass} in the
 * reference table and its integer id returned; a missing name is surfaced as a
 * {@link ScilabJavaException}.
 */
public class ScilabClassLoaderTest {

    @Test
    public void loadsAKnownJdkClassAndRegistersItAsAClassWrapper() throws ScilabJavaException {
        int id = ScilabClassLoader.loadJavaClass("java.util.StringJoiner");
        assertTrue(ScilabJavaObject.isValidJavaObject(id));
        assertEquals("java.util.StringJoiner", ScilabJavaObject.getClassName(id));
        assertTrue(ScilabJavaObject.arraySJO[id] instanceof ScilabJavaClass,
                   "a loaded class is wrapped as a ScilabJavaClass");
    }

    @Test
    public void loadWithoutReloadAlsoResolvesAFreshClass() throws ScilabJavaException {
        int id = ScilabClassLoader.loadJavaClass("java.util.StringTokenizer", false);
        assertTrue(ScilabJavaObject.isValidJavaObject(id));
        assertEquals("java.util.StringTokenizer", ScilabJavaObject.getClassName(id));
    }

    @Test
    public void missingClassIsReportedAsScilabJavaException() {
        assertThrows(ScilabJavaException.class,
                     () -> ScilabClassLoader.loadJavaClass("no.such.pkg.DefinitelyMissing12345"));
    }

    /* ============================================================ extended coverage */

    @Test
    public void loadFromAnExplicitBinPathResolvesAClassInThatDirectory() throws ScilabJavaException {
        // surefire runs with the module basedir as cwd, so target/classes holds the compiled
        // main classes. Load one of the module's own dependency-free classes from that directory.
        File classesDir = new File("target/classes");
        Assumptions.assumeTrue(
            new File(classesDir, "org/scilab/modules/external_objects_java/ScilabJavaException.class").exists(),
            "compiled main classes must be present under target/classes");

        int id = ScilabClassLoader.loadJavaClass(classesDir.getAbsolutePath(),
                 "org.scilab.modules.external_objects_java.ScilabJavaException");
        assertTrue(ScilabJavaObject.isValidJavaObject(id));
        assertEquals("org.scilab.modules.external_objects_java.ScilabJavaException",
                     ScilabJavaObject.getClassName(id));
    }

    @Test
    public void loadFromABadBinPathIsReportedAsScilabJavaException() {
        assertThrows(ScilabJavaException.class,
                     () -> ScilabClassLoader.loadJavaClass("/no/such/dir/at/all", "no.such.Class"));
    }

    @Test
    public void reloadingAClasspathClassGoesThroughTheUrlReloadBranch() throws ScilabJavaException {
        // A classpath class has a non-null CodeSource file: URL. Loading it a second time with
        // the default allowReload=true drives the URL-reload path (a fresh URLClassLoader).
        String name = "org.scilab.modules.external_objects_java.Converter";
        int first = ScilabClassLoader.loadJavaClass(name);
        assertTrue(ScilabJavaObject.isValidJavaObject(first));
        int second = ScilabClassLoader.loadJavaClass(name);
        assertTrue(ScilabJavaObject.isValidJavaObject(second));
        assertEquals(name, ScilabJavaObject.getClassName(second));
    }

    @Test
    public void removingAClassWrapperEvictsItFromTheNameMaps() throws ScilabJavaException {
        // Force the fresh-load branch by clearing any pre-existing mapping, so the reference
        // maps are guaranteed to point at the id this test allocates.
        String name = "java.util.zip.CRC32";
        ScilabClassLoader.clazz.remove(name);

        int id = ScilabClassLoader.loadJavaClass(name);
        assertEquals(Integer.valueOf(id), ScilabClassLoader.clazz.get(name));
        assertEquals(name, ScilabClassLoader.zzalc.get(id));

        // Removing a ScilabJavaClass triggers ScilabClassLoader.removeID(id).
        ScilabJavaObject.removeScilabJavaObject(id);
        assertFalse(ScilabClassLoader.clazz.containsKey(name));
        assertFalse(ScilabClassLoader.zzalc.containsKey(id));
    }
}
