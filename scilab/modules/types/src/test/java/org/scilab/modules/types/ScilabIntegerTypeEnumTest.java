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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for the SWIG-generated {@link ScilabIntegerTypeEnum}. The
 * unsigned constants deliberately sit 10 above their signed counterparts
 * (sci_uint8 == 11, sci_int8 == 1), so the numeric mapping is real behavior.
 */
public class ScilabIntegerTypeEnumTest {

    @Test
    public void signedSwigValues() {
        assertEquals(1, ScilabIntegerTypeEnum.sci_int8.swigValue());
        assertEquals(2, ScilabIntegerTypeEnum.sci_int16.swigValue());
        assertEquals(4, ScilabIntegerTypeEnum.sci_int32.swigValue());
        assertEquals(8, ScilabIntegerTypeEnum.sci_int64.swigValue());
    }

    @Test
    public void unsignedSwigValues() {
        assertEquals(11, ScilabIntegerTypeEnum.sci_uint8.swigValue());
        assertEquals(12, ScilabIntegerTypeEnum.sci_uint16.swigValue());
        assertEquals(14, ScilabIntegerTypeEnum.sci_uint32.swigValue());
        assertEquals(18, ScilabIntegerTypeEnum.sci_uint64.swigValue());
    }

    @Test
    public void swigToEnumResolvesKnownCodes() {
        assertSame(ScilabIntegerTypeEnum.sci_int8, ScilabIntegerTypeEnum.swigToEnum(1));
        assertSame(ScilabIntegerTypeEnum.sci_int64, ScilabIntegerTypeEnum.swigToEnum(8));
        // 11 exceeds the number of constants (8): forces the linear-scan branch.
        assertSame(ScilabIntegerTypeEnum.sci_uint8, ScilabIntegerTypeEnum.swigToEnum(11));
        assertSame(ScilabIntegerTypeEnum.sci_uint64, ScilabIntegerTypeEnum.swigToEnum(18));
    }

    @Test
    public void swigToEnumRoundTripsEveryConstant() {
        for (ScilabIntegerTypeEnum e : ScilabIntegerTypeEnum.values()) {
            assertSame(e, ScilabIntegerTypeEnum.swigToEnum(e.swigValue()),
                       "round-trip failed for " + e.name());
        }
    }

    @Test
    public void swigToEnumRejectsUnusedAndOutOfRangeCodes() {
        // 3 is a gap (sci_int16=2, sci_int32=4); 0 and negatives are never assigned.
        assertThrows(IllegalArgumentException.class, () -> ScilabIntegerTypeEnum.swigToEnum(3));
        assertThrows(IllegalArgumentException.class, () -> ScilabIntegerTypeEnum.swigToEnum(0));
        assertThrows(IllegalArgumentException.class, () -> ScilabIntegerTypeEnum.swigToEnum(-2));
        assertThrows(IllegalArgumentException.class, () -> ScilabIntegerTypeEnum.swigToEnum(100));
    }

    @Test
    public void enumHasExactlyEightConstants() {
        assertEquals(8, ScilabIntegerTypeEnum.values().length);
        assertSame(ScilabIntegerTypeEnum.sci_uint16, ScilabIntegerTypeEnum.valueOf("sci_uint16"));
    }
}
