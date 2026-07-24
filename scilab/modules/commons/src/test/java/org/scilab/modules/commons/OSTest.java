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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for the {@link OS} enum.
 *
 * <p>{@code OS.get()} and the constant-specific {@code getVersion()} bodies read the
 * {@code os.name} / {@code os.version} system properties, so the dispatch tests override
 * those properties around a snapshot that is fully restored after every test.
 */
public class OSTest {

    private String savedOsName;
    private String savedOsVersion;

    @BeforeEach
    public void snapshotProperties() {
        savedOsName = System.getProperty("os.name");
        savedOsVersion = System.getProperty("os.version");
    }

    @AfterEach
    public void restoreProperties() {
        restore("os.name", savedOsName);
        restore("os.version", savedOsVersion);
    }

    private static void restore(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    @Test
    public void valuesHoldsTheThreeDeclaredConstants() {
        OS[] all = OS.values();
        assertEquals(3, all.length);
        assertSame(OS.WINDOWS, OS.valueOf("WINDOWS"));
        assertSame(OS.MAC, OS.valueOf("MAC"));
        assertSame(OS.UNIX, OS.valueOf("UNIX"));
    }

    @Test
    public void valueOfUnknownNameThrows() {
        assertThrows(IllegalArgumentException.class, () -> OS.valueOf("SOLARIS"));
    }

    @Test
    public void getReturnsOneOfTheKnownConstants() {
        OS current = OS.get();
        assertNotNull(current);
        assertTrue(current == OS.WINDOWS || current == OS.MAC || current == OS.UNIX);
    }

    @Test
    public void getVersionNameIsConsistentWithGet() {
        String name = OS.getVersionName();
        switch (OS.get()) {
            case WINDOWS:
                assertEquals("Windows", name);
                break;
            case MAC:
                assertEquals("Mac", name);
                break;
            default:
                assertEquals("Linux", name);
                break;
        }
    }

    @Test
    public void getDispatchesOnOsNamePropertyCaseInsensitively() {
        System.setProperty("os.name", "Windows 11");
        assertSame(OS.WINDOWS, OS.get());
        assertEquals("Windows", OS.getVersionName());

        System.setProperty("os.name", "Mac OS X");
        assertSame(OS.MAC, OS.get());
        assertEquals("Mac", OS.getVersionName());

        System.setProperty("os.name", "Linux");
        assertSame(OS.UNIX, OS.get());
        assertEquals("Linux", OS.getVersionName());

        // Anything that is neither "windows" nor "mac" falls through to UNIX.
        System.setProperty("os.name", "FreeBSD");
        assertSame(OS.UNIX, OS.get());
        assertEquals("Linux", OS.getVersionName());
    }

    @Test
    public void unixVersionIsNullFromTheDefaultMethod() {
        // UNIX does not override getVersion(); the default body returns null.
        assertNull(OS.UNIX.getVersion());
    }

    @Test
    public void macVersionParsesDottedOsVersionIntoAnIntArray() {
        System.setProperty("os.version", "13.4.1");
        Object v = OS.MAC.getVersion();
        assertInstanceOf(int[].class, v);
        assertArrayEquals(new int[] {13, 4, 1}, (int[]) v);

        System.setProperty("os.version", "14");
        assertArrayEquals(new int[] {14}, (int[]) OS.MAC.getVersion());
    }

    @Test
    public void macVersionThrowsOnANonNumericComponent() {
        System.setProperty("os.version", "not.a.number");
        assertThrows(NumberFormatException.class, () -> OS.MAC.getVersion());
    }

    @Test
    public void windowsVersionParsesOsVersionAsADouble() {
        System.setProperty("os.version", "10.0");
        Object v = OS.WINDOWS.getVersion();
        assertInstanceOf(Double.class, v);
        assertEquals(10.0, (Double) v, 0.0);
    }

    @Test
    public void windowsVersionThrowsOnANonNumericValue() {
        System.setProperty("os.version", "vista");
        assertThrows(NumberFormatException.class, () -> OS.WINDOWS.getVersion());
    }
}
