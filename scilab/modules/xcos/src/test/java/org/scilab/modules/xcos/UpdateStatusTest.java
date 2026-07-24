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
package org.scilab.modules.xcos;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link UpdateStatus}.
 *
 * <p>{@code UpdateStatus} is a SWIG-generated enum returned by the native
 * incremental-update machinery. Its <em>ordinal</em> is the value crossing the
 * JNI boundary, so {@code SUCCESS == 0}, {@code NO_CHANGES == 1} and
 * {@code FAIL == 2} form a wire contract that these tests pin.</p>
 *
 * <p>Pure Java; no native library is loaded.</p>
 */
public class UpdateStatusTest {

    @Test
    @DisplayName("exactly three statuses are declared, in the expected order")
    public void valuesContainsAllConstantsInOrder() {
        UpdateStatus[] expected = { UpdateStatus.SUCCESS, UpdateStatus.NO_CHANGES, UpdateStatus.FAIL };
        assertArrayEquals(expected, UpdateStatus.values());
        assertEquals(3, UpdateStatus.values().length);
    }

    @Test
    @DisplayName("ordinals pin the JNI wire contract (0..2)")
    public void ordinalsMatchNativeWireValues() {
        assertEquals(0, UpdateStatus.SUCCESS.ordinal());
        assertEquals(1, UpdateStatus.NO_CHANGES.ordinal());
        assertEquals(2, UpdateStatus.FAIL.ordinal());
    }

    @Test
    @DisplayName("values()[i].ordinal() == i for every constant")
    public void ordinalEqualsArrayIndex() {
        UpdateStatus[] values = UpdateStatus.values();
        for (int i = 0; i < values.length; i++) {
            assertEquals(i, values[i].ordinal(), "ordinal mismatch at index " + i);
        }
    }

    @Test
    @DisplayName("name() returns the declared identifier for each constant")
    public void nameMatchesConstant() {
        assertEquals("SUCCESS", UpdateStatus.SUCCESS.name());
        assertEquals("NO_CHANGES", UpdateStatus.NO_CHANGES.name());
        assertEquals("FAIL", UpdateStatus.FAIL.name());
    }

    @Test
    @DisplayName("valueOf round-trips with name() for every constant")
    public void valueOfRoundTrips() {
        for (UpdateStatus s : UpdateStatus.values()) {
            assertSame(s, UpdateStatus.valueOf(s.name()));
        }
    }

    @Test
    @DisplayName("valueOf of an unknown name throws IllegalArgumentException")
    public void valueOfUnknownThrows() {
        assertThrows(IllegalArgumentException.class, () -> UpdateStatus.valueOf("SUCCEEDED"));
        assertThrows(IllegalArgumentException.class, () -> UpdateStatus.valueOf("success"));
    }

    @Test
    @DisplayName("valueOf(null) throws NullPointerException")
    public void valueOfNullThrows() {
        assertThrows(NullPointerException.class, () -> UpdateStatus.valueOf(null));
    }

    @Test
    @DisplayName("the three statuses are mutually distinct")
    public void statusesAreDistinct() {
        assertNotSame(UpdateStatus.SUCCESS, UpdateStatus.NO_CHANGES);
        assertNotSame(UpdateStatus.SUCCESS, UpdateStatus.FAIL);
        assertNotSame(UpdateStatus.NO_CHANGES, UpdateStatus.FAIL);
    }

    @Test
    @DisplayName("values() hands back a fresh defensive copy each call")
    public void valuesReturnsDefensiveCopy() {
        UpdateStatus[] first = UpdateStatus.values();
        assertNotSame(first, UpdateStatus.values(), "values() must not leak a shared array");
        first[0] = UpdateStatus.FAIL;
        assertSame(UpdateStatus.SUCCESS, UpdateStatus.values()[0]);
    }

    @Test
    @DisplayName("compareTo is consistent with ordinal ordering")
    public void compareToFollowsOrdinal() {
        assertTrue(UpdateStatus.SUCCESS.compareTo(UpdateStatus.FAIL) < 0);
        assertTrue(UpdateStatus.FAIL.compareTo(UpdateStatus.SUCCESS) > 0);
        assertEquals(0, UpdateStatus.NO_CHANGES.compareTo(UpdateStatus.NO_CHANGES));
    }
}
