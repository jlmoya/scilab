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
 * Hermetic unit tests for {@link PortKind}.
 *
 * <p>{@code PortKind} is a SWIG-generated enum mirroring the native C++ port
 * kind enumeration. As with {@link Kind}, the <em>ordinal</em> is the value
 * marshalled across JNI, so {@code PORT_UNDEF} being the zero value and the
 * remaining constants keeping their order is a wire contract. The tests below
 * pin those values.</p>
 *
 * <p>Pure Java; no native library is loaded.</p>
 */
public class PortKindTest {

    @Test
    @DisplayName("exactly five port kinds are declared, in the expected order")
    public void valuesContainsAllConstantsInOrder() {
        PortKind[] expected = {
            PortKind.PORT_UNDEF, PortKind.PORT_IN, PortKind.PORT_OUT,
            PortKind.PORT_EIN, PortKind.PORT_EOUT
        };
        assertArrayEquals(expected, PortKind.values());
        assertEquals(5, PortKind.values().length);
    }

    @Test
    @DisplayName("PORT_UNDEF is the zero / sentinel value")
    public void undefinedIsZero() {
        assertEquals(0, PortKind.PORT_UNDEF.ordinal());
        assertSame(PortKind.PORT_UNDEF, PortKind.values()[0]);
    }

    @Test
    @DisplayName("ordinals pin the JNI wire contract (0..4)")
    public void ordinalsMatchNativeWireValues() {
        assertEquals(0, PortKind.PORT_UNDEF.ordinal());
        assertEquals(1, PortKind.PORT_IN.ordinal());
        assertEquals(2, PortKind.PORT_OUT.ordinal());
        assertEquals(3, PortKind.PORT_EIN.ordinal());
        assertEquals(4, PortKind.PORT_EOUT.ordinal());
    }

    @Test
    @DisplayName("values()[i].ordinal() == i for every constant")
    public void ordinalEqualsArrayIndex() {
        PortKind[] values = PortKind.values();
        for (int i = 0; i < values.length; i++) {
            assertEquals(i, values[i].ordinal(), "ordinal mismatch at index " + i);
        }
    }

    @Test
    @DisplayName("name() returns the declared identifier for each constant")
    public void nameMatchesConstant() {
        assertEquals("PORT_UNDEF", PortKind.PORT_UNDEF.name());
        assertEquals("PORT_IN", PortKind.PORT_IN.name());
        assertEquals("PORT_OUT", PortKind.PORT_OUT.name());
        assertEquals("PORT_EIN", PortKind.PORT_EIN.name());
        assertEquals("PORT_EOUT", PortKind.PORT_EOUT.name());
    }

    @Test
    @DisplayName("valueOf round-trips with name() for every constant")
    public void valueOfRoundTrips() {
        for (PortKind k : PortKind.values()) {
            assertSame(k, PortKind.valueOf(k.name()));
        }
    }

    @Test
    @DisplayName("valueOf of an unknown name throws IllegalArgumentException")
    public void valueOfUnknownThrows() {
        assertThrows(IllegalArgumentException.class, () -> PortKind.valueOf("PORT_INOUT"));
        assertThrows(IllegalArgumentException.class, () -> PortKind.valueOf("port_in"));
    }

    @Test
    @DisplayName("valueOf(null) throws NullPointerException")
    public void valueOfNullThrows() {
        assertThrows(NullPointerException.class, () -> PortKind.valueOf(null));
    }

    @Test
    @DisplayName("values() hands back a fresh defensive copy each call")
    public void valuesReturnsDefensiveCopy() {
        PortKind[] first = PortKind.values();
        PortKind[] second = PortKind.values();
        assertNotSame(first, second, "values() must not leak a shared array");
        first[0] = PortKind.PORT_EOUT;
        assertSame(PortKind.PORT_UNDEF, PortKind.values()[0]);
    }

    @Test
    @DisplayName("input and output kinds are distinct constants")
    public void inputAndOutputAreDistinct() {
        assertNotSame(PortKind.PORT_IN, PortKind.PORT_OUT);
        assertNotSame(PortKind.PORT_EIN, PortKind.PORT_EOUT);
        // explicit vs event ports are distinct too
        assertNotSame(PortKind.PORT_IN, PortKind.PORT_EIN);
        assertNotSame(PortKind.PORT_OUT, PortKind.PORT_EOUT);
    }

    @Test
    @DisplayName("compareTo is consistent with ordinal ordering")
    public void compareToFollowsOrdinal() {
        assertTrue(PortKind.PORT_UNDEF.compareTo(PortKind.PORT_IN) < 0);
        assertTrue(PortKind.PORT_EOUT.compareTo(PortKind.PORT_UNDEF) > 0);
        assertEquals(0, PortKind.PORT_IN.compareTo(PortKind.PORT_IN));
    }
}
