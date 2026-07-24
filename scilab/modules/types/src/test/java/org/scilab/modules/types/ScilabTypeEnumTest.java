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

package org.scilab.modules.types;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for the SWIG-generated {@link ScilabTypeEnum}. The enum
 * carries explicit, non-contiguous {@code swigValue}s (e.g. sci_pointer == 128)
 * so the {@code swigValue} / {@code swigToEnum} mapping is genuine behavior
 * worth pinning.
 */
public class ScilabTypeEnumTest {

    @Test
    public void knownSwigValuesAreStable() {
        // These constants are consumed across the ABI; their integer codes must not drift.
        assertEquals(1, ScilabTypeEnum.sci_matrix.swigValue());
        assertEquals(2, ScilabTypeEnum.sci_poly.swigValue());
        assertEquals(4, ScilabTypeEnum.sci_boolean.swigValue());
        assertEquals(5, ScilabTypeEnum.sci_sparse.swigValue());
        assertEquals(6, ScilabTypeEnum.sci_boolean_sparse.swigValue());
        assertEquals(8, ScilabTypeEnum.sci_ints.swigValue());
        assertEquals(10, ScilabTypeEnum.sci_strings.swigValue());
        assertEquals(15, ScilabTypeEnum.sci_list.swigValue());
        assertEquals(16, ScilabTypeEnum.sci_tlist.swigValue());
        assertEquals(17, ScilabTypeEnum.sci_mlist.swigValue());
        assertEquals(128, ScilabTypeEnum.sci_pointer.swigValue());
        assertEquals(129, ScilabTypeEnum.sci_implicit_poly.swigValue());
        assertEquals(130, ScilabTypeEnum.sci_intrinsic_function.swigValue());
    }

    @Test
    public void swigToEnumResolvesKnownCodes() {
        assertSame(ScilabTypeEnum.sci_matrix, ScilabTypeEnum.swigToEnum(1));
        assertSame(ScilabTypeEnum.sci_boolean, ScilabTypeEnum.swigToEnum(4));
        assertSame(ScilabTypeEnum.sci_strings, ScilabTypeEnum.swigToEnum(10));
        // 128 exceeds the number of constants (21): forces the linear-scan branch.
        assertSame(ScilabTypeEnum.sci_pointer, ScilabTypeEnum.swigToEnum(128));
        assertSame(ScilabTypeEnum.sci_intrinsic_function, ScilabTypeEnum.swigToEnum(130));
    }

    @Test
    public void swigToEnumRoundTripsEveryConstant() {
        for (ScilabTypeEnum e : ScilabTypeEnum.values()) {
            assertSame(e, ScilabTypeEnum.swigToEnum(e.swigValue()),
                       "round-trip failed for " + e.name());
        }
    }

    @Test
    public void swigToEnumRejectsUnusedCode() {
        // 3 sits between sci_poly (2) and sci_boolean (4) but is not assigned.
        assertThrows(IllegalArgumentException.class, () -> ScilabTypeEnum.swigToEnum(3));
        // 7 is a gap as well (sci_boolean_sparse=6, sci_ints=8).
        assertThrows(IllegalArgumentException.class, () -> ScilabTypeEnum.swigToEnum(7));
    }

    @Test
    public void swigToEnumRejectsNegativeAndOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> ScilabTypeEnum.swigToEnum(-1));
        assertThrows(IllegalArgumentException.class, () -> ScilabTypeEnum.swigToEnum(0));
        assertThrows(IllegalArgumentException.class, () -> ScilabTypeEnum.swigToEnum(9999));
    }

    @Test
    public void valueOfAndNameAreConsistent() {
        assertSame(ScilabTypeEnum.sci_mlist, ScilabTypeEnum.valueOf("sci_mlist"));
        assertEquals("sci_mlist", ScilabTypeEnum.sci_mlist.name());
        assertNotNull(ScilabTypeEnum.values());
        assertEquals(21, ScilabTypeEnum.values().length);
        assertThrows(IllegalArgumentException.class, () -> ScilabTypeEnum.valueOf("not_a_type"));
    }
}
