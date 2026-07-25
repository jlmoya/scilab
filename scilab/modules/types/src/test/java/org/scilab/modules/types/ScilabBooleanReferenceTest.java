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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.IntBuffer;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link ScilabBooleanReference}, the {@code IntBuffer}
 * backed boolean variant (0 = false, non-zero = true). Element access and the
 * dense {@code getData} reconstruction are both column-major
 * ({@code index = i + nbRows*j}) and therefore agree; equality against a plain
 * {@link ScilabBoolean} goes through the shared column-major engine.
 */
public class ScilabBooleanReferenceTest {

    // Column-major buffer for {{true,false},{false,true}}.
    private static IntBuffer boolBuffer() {
        return IntBuffer.wrap(new int[] {1, 0, 0, 1});
    }

    @Test
    public void dimensionsAndFlags() {
        ScilabBooleanReference r = new ScilabBooleanReference("v", boolBuffer(), 2, 2);
        assertFalse(r.isEmpty());
        assertEquals(2, r.getHeight());
        assertEquals(2, r.getWidth());
        assertEquals("v", r.getVarName());
        assertTrue(r.isReference());
    }

    @Test
    public void elementAccessIsColumnMajor() {
        ScilabBooleanReference r = new ScilabBooleanReference("v", boolBuffer(), 2, 2);
        assertTrue(r.getElement(0, 0));
        assertFalse(r.getElement(0, 1));
        assertFalse(r.getElement(1, 0));
        assertTrue(r.getElement(1, 1));
    }

    @Test
    public void getDataReconstructsMatrix() {
        ScilabBooleanReference r = new ScilabBooleanReference("v", boolBuffer(), 2, 2);
        boolean[][] d = r.getData();
        assertArrayEquals(new boolean[] {true, false}, d[0]);
        assertArrayEquals(new boolean[] {false, true}, d[1]);
    }

    @Test
    public void setElementWritesThrough() {
        ScilabBooleanReference r = new ScilabBooleanReference("v", boolBuffer(), 2, 2);
        r.setElement(0, 1, true);
        assertTrue(r.getElement(0, 1));
        r.setElement(0, 0, false);
        assertFalse(r.getElement(0, 0));
    }

    @Test
    public void setDataRewritesColumnMajor() {
        ScilabBooleanReference r = new ScilabBooleanReference("v", boolBuffer(), 2, 2);
        r.setData(new boolean[][] {{true, true}, {false, false}});
        assertTrue(r.getElement(0, 0));
        assertTrue(r.getElement(0, 1));
        assertFalse(r.getElement(1, 0));
        assertFalse(r.getElement(1, 1));
    }

    @Test
    public void rawDataIsTheBuffer() {
        ScilabBooleanReference r = new ScilabBooleanReference("v", boolBuffer(), 2, 2);
        assertInstanceOf(IntBuffer.class, r.getRawData());
    }

    @Test
    public void equalsPlainBooleanViaColumnMajorEngine() {
        ScilabBooleanReference ref = new ScilabBooleanReference("v", boolBuffer(), 2, 2);
        ScilabBoolean plain = new ScilabBoolean(new boolean[][] {{true, false}, {false, true}});
        assertEquals(ref, plain);
        ScilabBoolean different = new ScilabBoolean(new boolean[][] {{true, true}, {false, true}});
        assertNotEquals(ref, different);
    }

    @Test
    public void zeroRowsIsEmpty() {
        ScilabBooleanReference r = new ScilabBooleanReference("v", IntBuffer.allocate(0), 0, 0);
        assertTrue(r.isEmpty());
    }
}
