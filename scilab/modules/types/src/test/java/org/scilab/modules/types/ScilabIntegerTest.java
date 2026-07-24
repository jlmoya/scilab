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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link ScilabInteger}, covering the four precisions,
 * the signed/unsigned precision tagging, long-widening via {@code getData}, the
 * legacy {@code convertOldType} mapping, element access, equality and the
 * Scilab-literal {@code toString}.
 */
public class ScilabIntegerTest {

    @Test
    public void defaultConstructorIsEmpty() {
        ScilabInteger i = new ScilabInteger();
        assertTrue(i.isEmpty());
        assertEquals(0, i.getHeight());
        assertEquals(0, i.getWidth());
        assertNull(i.getPrec());
        assertEquals("[]", i.toString());
        assertEquals(ScilabTypeEnum.sci_ints, i.getType());
    }

    @Test
    public void signedByteScalarHasInt8Precision() {
        ScilabInteger i = new ScilabInteger((byte) 5);
        assertEquals(ScilabIntegerTypeEnum.sci_int8, i.getPrec());
        assertFalse(i.isUnsigned());
        assertEquals(1, i.getHeight());
        assertEquals(1, i.getWidth());
        assertEquals(5L, i.getElement(0, 0));
        assertEquals(5L, i.getData()[0][0]);
        assertEquals("int8([5])", i.toString());
    }

    @Test
    public void unsignedFlagSelectsUnsignedPrecisionPerWidth() {
        assertEquals(ScilabIntegerTypeEnum.sci_uint8, new ScilabInteger((byte) 1, true).getPrec());
        assertEquals(ScilabIntegerTypeEnum.sci_uint16, new ScilabInteger((short) 1, true).getPrec());
        assertEquals(ScilabIntegerTypeEnum.sci_uint32, new ScilabInteger(1, true).getPrec());
        assertEquals(ScilabIntegerTypeEnum.sci_uint64, new ScilabInteger(1L, true).getPrec());

        assertTrue(new ScilabInteger(1, true).isUnsigned());
        assertFalse(new ScilabInteger(1, false).isUnsigned());
    }

    @Test
    public void shortIntLongScalarPrecisions() {
        assertEquals(ScilabIntegerTypeEnum.sci_int16, new ScilabInteger((short) 7).getPrec());
        assertEquals(ScilabIntegerTypeEnum.sci_int32, new ScilabInteger(7).getPrec());
        assertEquals(ScilabIntegerTypeEnum.sci_int64, new ScilabInteger(7L).getPrec());
    }

    @Test
    public void matrixConstructorAndGetDataWidening() {
        int[][] data = {{10, 20}, {30, 40}};
        ScilabInteger i = new ScilabInteger(data, false);
        assertEquals(ScilabIntegerTypeEnum.sci_int32, i.getPrec());
        assertEquals(2, i.getHeight());
        assertEquals(2, i.getWidth());
        assertEquals(40, i.getIntElement(1, 1));
        // getData widens every precision to long[][].
        long[][] widened = i.getData();
        assertEquals(10L, widened[0][0]);
        assertEquals(40L, widened[1][1]);
    }

    @Test
    public void setElementUpdatesUnderlyingStorage() {
        ScilabInteger i = new ScilabInteger(new int[][] {{1, 2}}, false);
        i.setElement(0, 1, 99);
        assertEquals(99L, i.getElement(0, 1));
        i.setIntElement(0, 0, 7);
        assertEquals(7, i.getIntElement(0, 0));
    }

    @Test
    public void byteMatrixElementAccessors() {
        byte[][] data = {{1, 2}, {3, 4}};
        ScilabInteger i = new ScilabInteger(data, true);
        assertEquals(ScilabIntegerTypeEnum.sci_uint8, i.getPrec());
        assertTrue(i.isUnsigned());
        assertEquals((byte) 3, i.getByteElement(1, 0));
        assertSame(data, i.getRawData());
    }

    @Test
    public void namedConstructorCarriesVarNameAndSwap() {
        ScilabInteger i = new ScilabInteger("k", new long[][] {{1L, 2L}}, false, true);
        assertEquals("k", i.getVarName());
        assertTrue(i.isSwaped());
        assertEquals(ScilabIntegerTypeEnum.sci_int64, i.getPrec());
        assertEquals(2L, i.getLongElement(0, 1));
    }

    @Test
    public void convertOldTypeMapsEveryLegacyName() {
        assertEquals(ScilabIntegerTypeEnum.sci_int8, ScilabInteger.convertOldType("type8", false));
        assertEquals(ScilabIntegerTypeEnum.sci_uint8, ScilabInteger.convertOldType("type8", true));
        assertEquals(ScilabIntegerTypeEnum.sci_int16, ScilabInteger.convertOldType("type16", false));
        assertEquals(ScilabIntegerTypeEnum.sci_uint16, ScilabInteger.convertOldType("type16", true));
        assertEquals(ScilabIntegerTypeEnum.sci_int32, ScilabInteger.convertOldType("type32", false));
        assertEquals(ScilabIntegerTypeEnum.sci_uint32, ScilabInteger.convertOldType("type32", true));
        assertEquals(ScilabIntegerTypeEnum.sci_int64, ScilabInteger.convertOldType("type64", false));
        assertEquals(ScilabIntegerTypeEnum.sci_uint64, ScilabInteger.convertOldType("type64", true));
    }

    @Test
    public void convertOldTypeReturnsNullForUnknownName() {
        assertNull(ScilabInteger.convertOldType("type128", false));
        assertNull(ScilabInteger.convertOldType("garbage", true));
    }

    @Test
    public void equalsAndHashCodeSamePrecision() {
        ScilabInteger a = new ScilabInteger(new int[][] {{1, 2}, {3, 4}}, false);
        ScilabInteger b = new ScilabInteger(new int[][] {{1, 2}, {3, 4}}, false);
        ScilabInteger c = new ScilabInteger(new int[][] {{1, 2}, {3, 5}}, false);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
        assertNotEquals(a, "not an integer");
    }

    @Test
    public void twoEmptyIntegersAreEqual() {
        assertEquals(new ScilabInteger(), new ScilabInteger());
    }

    @Test
    public void toStringForUnsignedAndWiderTypes() {
        assertEquals("uint32([7])", new ScilabInteger(7, true).toString());
        assertEquals("int16([7])", new ScilabInteger((short) 7).toString());
        assertEquals("uint64([42])", new ScilabInteger(42L, true).toString());
        assertEquals("int32([1, 2 ; 3, 4])",
                     new ScilabInteger(new int[][] {{1, 2}, {3, 4}}, false).toString());
    }

    @Test
    public void getSerializedObjectCarriesPrecisionCodeAndData() {
        ScilabInteger i = new ScilabInteger(new byte[][] {{9}}, false);
        Object[] serialized = (Object[]) i.getSerializedObject();
        assertEquals(2, serialized.length);
        int[] precCode = (int[]) serialized[0];
        assertEquals(ScilabIntegerTypeEnum.sci_int8.swigValue(), precCode[0]);
        assertSame(i.getRawData(), serialized[1]);
    }
}
