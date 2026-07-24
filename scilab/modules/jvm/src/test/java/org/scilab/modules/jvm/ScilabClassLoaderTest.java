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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URL;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link ScilabClassLoader}.
 *
 * <p>{@code ScilabClassLoader} is a thin {@link java.net.URLClassLoader} subclass whose only
 * jobs are (a) starting empty, (b) exposing the otherwise-protected {@code addURL}, (c)
 * delegating {@code loadClass} to its parent, and (d) the package-private
 * {@code appendToClassPathForInstrumentation} JVM hook. Every one of those is exercised here
 * with a real loader instance whose parent is this test's own class loader, so nothing native
 * or Scilab-specific is required.
 */
public class ScilabClassLoaderTest {

    private ScilabClassLoader newLoader() {
        return new ScilabClassLoader(getClass().getClassLoader());
    }

    private static URL fileUrl(String path) throws Exception {
        return Paths.get(path).toUri().toURL();
    }

    @Test
    public void constructorStartsWithNoUrls() {
        assertEquals(0, newLoader().getURLs().length);
    }

    @Test
    public void constructorRetainsGivenParent() {
        ClassLoader parent = getClass().getClassLoader();
        assertSame(parent, new ScilabClassLoader(parent).getParent());
    }

    @Test
    public void addUrlBecomesVisibleViaGetUrls() throws Exception {
        ScilabClassLoader cl = newLoader();
        URL u = fileUrl("/tmp/some-lib.jar");
        cl.addURL(u);
        URL[] urls = cl.getURLs();
        assertEquals(1, urls.length);
        assertEquals(u.toExternalForm(), urls[0].toExternalForm());
    }

    @Test
    public void addUrlPreservesInsertionOrder() throws Exception {
        ScilabClassLoader cl = newLoader();
        URL u1 = fileUrl("/tmp/a.jar");
        URL u2 = fileUrl("/tmp/b.jar");
        cl.addURL(u1);
        cl.addURL(u2);
        URL[] urls = cl.getURLs();
        assertEquals(2, urls.length);
        assertEquals(u1.toExternalForm(), urls[0].toExternalForm());
        assertEquals(u2.toExternalForm(), urls[1].toExternalForm());
    }

    @Test
    public void loadClassDelegatesToParentForBootstrapClass() throws Exception {
        assertSame(String.class, newLoader().loadClass("java.lang.String"));
    }

    @Test
    public void loadClassFindsSiblingClassThroughParent() throws Exception {
        // Empty URL set + parent-first delegation => the parent resolves our own type.
        assertSame(ScilabClassLoader.class,
                   newLoader().loadClass("org.scilab.modules.jvm.ScilabClassLoader"));
    }

    @Test
    public void loadClassThrowsForUnknownClass() {
        ScilabClassLoader cl = newLoader();
        assertThrows(ClassNotFoundException.class,
                     () -> cl.loadClass("org.scilab.does.not.ExistXyz"));
    }

    @Test
    public void appendToClassPathForInstrumentationAddsDerivedUrl() throws Exception {
        ScilabClassLoader cl = newLoader();
        String jar = "/tmp/instrument-me.jar";
        // package-private JVM instrumentation hook, reachable from the same package
        cl.appendToClassPathForInstrumentation(jar);
        URL expected = Paths.get(jar).toUri().toURL();
        URL[] urls = cl.getURLs();
        assertEquals(1, urls.length);
        assertEquals(expected.toExternalForm(), urls[0].toExternalForm());
    }
}
