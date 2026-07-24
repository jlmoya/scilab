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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URL;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

/**
 * Hermetic characterization tests for {@link ClassPath}.
 *
 * <p>{@code ClassPath} dispatches on an {@code int} load-mode: {@code 0} = "load now" (casts
 * the <em>system</em> class loader to {@link ScilabClassLoader} and adds the URL), {@code 1}
 * = "background" (enqueue only), anything else = the {@code switch} default, a silent no-op.
 *
 * <p>Only a running Scilab installs a {@code ScilabClassLoader}/{@link java.net.URLClassLoader}
 * as the system class loader (via {@code -Djava.system.class.loader}). Under the stock JDK
 * app class loader of this test JVM the mode-{@code 0} cast and {@code getClassPath()}'s
 * {@code URLClassLoader} cast therefore throw {@link ClassCastException} — these tests pin that
 * environment-dependent contract deliberately, and confirm the non-casting modes stay quiet.
 */
public class ClassPathTest {

    private static URL fileUrl(String path) throws Exception {
        return Paths.get(path).toUri().toURL();
    }

    @Test
    public void addUrlStartupThrowsWithoutScilabSystemClassLoader() throws Exception {
        URL u = fileUrl("/tmp/startup.jar");
        // mode 0 casts ClassLoader.getSystemClassLoader() to ScilabClassLoader.
        assertThrows(ClassCastException.class, () -> ClassPath.addURL(u, 0));
    }

    @Test
    public void addFileStringStartupThrowsWithoutScilabSystemClassLoader() {
        // addFile(String,int) -> addFile(File,int) -> addURL(...,0): same cast, same throw.
        assertThrows(ClassCastException.class, () -> ClassPath.addFile("/tmp/startup.jar", 0));
    }

    @Test
    public void addUrlBackgroundModeJustEnqueues() throws Exception {
        URL u = fileUrl("/tmp/background.jar");
        assertDoesNotThrow(() -> ClassPath.addURL(u, 1));
    }

    @Test
    public void addFileStringBackgroundModeDoesNotThrow() {
        assertDoesNotThrow(() -> ClassPath.addFile("/tmp/background2.jar", 1));
    }

    @Test
    public void addUrlOnUseModeIsSilentNoOp() throws Exception {
        URL u = fileUrl("/tmp/onuse.jar");
        // mode 2 falls through the switch with no case -> nothing happens, no throw.
        assertDoesNotThrow(() -> ClassPath.addURL(u, 2));
    }

    @Test
    public void addUrlUnknownModeIsSilentNoOp() throws Exception {
        URL u = fileUrl("/tmp/weird.jar");
        assertDoesNotThrow(() -> ClassPath.addURL(u, 99));
    }

    @Test
    public void addFileStringOnUseModeDoesNotThrow() {
        assertDoesNotThrow(() -> ClassPath.addFile("/tmp/onuse2.jar", 2));
    }

    @Test
    public void getClassPathThrowsUnderStockAppClassLoader() {
        // getClassPath() casts the system class loader to URLClassLoader; the JDK 9+
        // app class loader is not one.
        assertThrows(ClassCastException.class, ClassPath::getClassPath);
    }

    @Test
    public void loadBackgroundClassPathReturnsQuietlyOnCallingThread() {
        // Spawns a worker whose per-URL failures are swallowed inside its own try/catch;
        // the caller must never see an exception.
        assertDoesNotThrow(ClassPath::loadBackGroundClassPath);
    }
}
