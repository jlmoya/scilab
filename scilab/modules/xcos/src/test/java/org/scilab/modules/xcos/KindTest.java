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
 * Hermetic unit tests for {@link Kind}.
 *
 * <p>{@code Kind} is a SWIG-generated enum that mirrors the native C++
 * {@code org_scilab_modules_xcos} kind enumeration. Its <em>ordinal</em> is the
 * integer value marshalled across the JNI boundary (e.g. to
 * {@code JavaController.createObject}), so the exact declaration order is a wire
 * contract: reordering the constants would silently corrupt every native call.
 * The ordinal assertions below therefore double as characterization tests that
 * pin the current wire values.</p>
 *
 * <p>These tests are pure Java (no {@code System.loadLibrary}) and never cross
 * the JNI boundary.</p>
 */
public class KindTest {

    @Test
    @DisplayName("exactly five kinds are declared, in the expected order")
    public void valuesContainsAllConstantsInOrder() {
        Kind[] expected = { Kind.BLOCK, Kind.DIAGRAM, Kind.LINK, Kind.ANNOTATION, Kind.PORT };
        assertArrayEquals(expected, Kind.values());
        assertEquals(5, Kind.values().length);
    }

    @Test
    @DisplayName("ordinals pin the JNI wire contract (0..4)")
    public void ordinalsMatchNativeWireValues() {
        assertEquals(0, Kind.BLOCK.ordinal());
        assertEquals(1, Kind.DIAGRAM.ordinal());
        assertEquals(2, Kind.LINK.ordinal());
        assertEquals(3, Kind.ANNOTATION.ordinal());
        assertEquals(4, Kind.PORT.ordinal());
    }

    @Test
    @DisplayName("values()[i].ordinal() == i for every constant")
    public void ordinalEqualsArrayIndex() {
        Kind[] values = Kind.values();
        for (int i = 0; i < values.length; i++) {
            assertEquals(i, values[i].ordinal(), "ordinal mismatch at index " + i);
        }
    }

    @Test
    @DisplayName("name() returns the declared identifier for each constant")
    public void nameMatchesConstant() {
        assertEquals("BLOCK", Kind.BLOCK.name());
        assertEquals("DIAGRAM", Kind.DIAGRAM.name());
        assertEquals("LINK", Kind.LINK.name());
        assertEquals("ANNOTATION", Kind.ANNOTATION.name());
        assertEquals("PORT", Kind.PORT.name());
    }

    @Test
    @DisplayName("valueOf round-trips with name() for every constant")
    public void valueOfRoundTrips() {
        for (Kind k : Kind.values()) {
            assertSame(k, Kind.valueOf(k.name()));
        }
    }

    @Test
    @DisplayName("valueOf of an unknown name throws IllegalArgumentException")
    public void valueOfUnknownThrows() {
        assertThrows(IllegalArgumentException.class, () -> Kind.valueOf("NOT_A_KIND"));
        // valueOf is case-sensitive.
        assertThrows(IllegalArgumentException.class, () -> Kind.valueOf("block"));
    }

    @Test
    @DisplayName("valueOf(null) throws NullPointerException")
    public void valueOfNullThrows() {
        assertThrows(NullPointerException.class, () -> Kind.valueOf(null));
    }

    @Test
    @DisplayName("values() hands back a fresh defensive copy each call")
    public void valuesReturnsDefensiveCopy() {
        Kind[] first = Kind.values();
        Kind[] second = Kind.values();
        assertNotSame(first, second, "values() must not leak a shared array");
        // Mutating the returned array must not corrupt the enum's own state.
        first[0] = Kind.PORT;
        assertSame(Kind.BLOCK, Kind.values()[0]);
    }

    @Test
    @DisplayName("compareTo is consistent with ordinal ordering")
    public void compareToFollowsOrdinal() {
        assertTrue(Kind.BLOCK.compareTo(Kind.DIAGRAM) < 0);
        assertTrue(Kind.PORT.compareTo(Kind.BLOCK) > 0);
        assertEquals(0, Kind.LINK.compareTo(Kind.LINK));
    }

    @Test
    @DisplayName("every constant reports Kind as its declaring class")
    public void declaringClassIsKind() {
        for (Kind k : Kind.values()) {
            assertSame(Kind.class, k.getDeclaringClass());
        }
    }
}
