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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link ScilabVariables}, the C/C++ -&gt; Java bridge.
 * The {@code send*} entry points are driven with an empty index array so they
 * route straight to a capturing {@link ScilabVariablesHandler}, letting us assert
 * on the exact {@link ScilabType} that gets built for every scalar/matrix/buffer
 * flavour, plus the list-building protocol ({@code sendData(..char..)} +
 * {@code addElement} via non-empty indexes + {@code closeList}).
 */
public class ScilabVariablesTest {

    private static final double EPS = 0.0;
    private static final int[] TOP = new int[0];

    /** Records the last variable handed to it. */
    private static final class Capture implements ScilabVariablesHandler {
        ScilabType captured;
        @Override
        public void handle(ScilabType var) {
            captured = var;
        }
    }

    private static int register(Capture c) {
        return ScilabVariables.addScilabVariablesHandler(c);
    }

    @Test
    public void registeringSameHandlerTwiceReturnsSameId() {
        Capture c = new Capture();
        int id1 = register(c);
        int id2 = register(c);
        assertEquals(id1, id2);
    }

    @Test
    public void distinctHandlersGetDistinctIdsAndRoutingIsIsolated() {
        Capture a = new Capture();
        Capture b = new Capture();
        int idA = register(a);
        int idB = register(b);
        assertNotEquals(idA, idB);

        ScilabVariables.sendData("x", TOP, new double[][] {{1.0}}, false, idA);
        assertInstanceOf(ScilabDouble.class, a.captured);
        assertNull(b.captured); // b must not have been touched
    }

    @Test
    public void removingHandlerNullsTheSlot() {
        Capture c = new Capture();
        int id = register(c);
        ScilabVariables.removeScilabVariablesHandler(id);
        // The slot is now null, so a send to that id dereferences null.
        assertThrows(NullPointerException.class, () ->
                     ScilabVariables.sendData("x", TOP, new double[][] {{1.0}}, false, id));
    }

    @Test
    public void removingHandlerByReferenceAlsoNullsTheSlot() {
        Capture c = new Capture();
        int id = register(c);
        ScilabVariables.removeScilabVariablesHandler(c);
        assertThrows(NullPointerException.class, () ->
                     ScilabVariables.sendData("x", TOP, new double[][] {{1.0}}, false, id));
    }

    @Test
    public void sendRealDoubleMatrix() {
        Capture c = new Capture();
        ScilabVariables.sendData("m", TOP, new double[][] {{1.0, 2.0}, {3.0, 4.0}}, false, register(c));
        ScilabDouble d = assertInstanceOf(ScilabDouble.class, c.captured);
        assertEquals("m", d.getVarName());
        assertEquals(ScilabTypeEnum.sci_matrix, d.getType());
        assertTrue(d.isReal());
        assertEquals(4.0, d.getRealElement(1, 1), EPS);
    }

    @Test
    public void sendComplexDoubleMatrix() {
        Capture c = new Capture();
        ScilabVariables.sendData("z", TOP, new double[][] {{1.0}}, new double[][] {{2.0}}, false, register(c));
        ScilabDouble d = assertInstanceOf(ScilabDouble.class, c.captured);
        assertFalse(d.isReal());
        assertEquals(2.0, d.getImaginaryElement(0, 0), EPS);
    }

    @Test
    public void sendDoubleBufferBecomesReference() {
        Capture c = new Capture();
        ScilabVariables.sendDataAsBuffer("b", TOP, DoubleBuffer.wrap(new double[] {1, 2, 3, 4}), 2, 2, register(c));
        ScilabDoubleReference d = assertInstanceOf(ScilabDoubleReference.class, c.captured);
        assertTrue(d.isReference());
        assertTrue(d.isReal());
        assertEquals(4.0, d.getRealElement(1, 1), EPS);
    }

    @Test
    public void sendComplexDoubleBufferBecomesReference() {
        Capture c = new Capture();
        ScilabVariables.sendDataAsBuffer("b", TOP, DoubleBuffer.wrap(new double[] {1, 2, 3, 4}),
                                         DoubleBuffer.wrap(new double[] {5, 6, 7, 8}), 2, 2, register(c));
        ScilabDoubleReference d = assertInstanceOf(ScilabDoubleReference.class, c.captured);
        assertFalse(d.isReal());
        assertEquals(8.0, d.getImaginaryElement(1, 1), EPS);
    }

    @Test
    public void sendIntegerMatricesCoverAllPrecisions() {
        Capture c = new Capture();
        int id = register(c);

        ScilabVariables.sendData("i32", TOP, new int[][] {{7}}, false, id);
        assertEquals(ScilabIntegerTypeEnum.sci_int32, ((ScilabInteger) c.captured).getPrec());

        ScilabVariables.sendUnsignedData("u32", TOP, new int[][] {{7}}, false, id);
        assertEquals(ScilabIntegerTypeEnum.sci_uint32, ((ScilabInteger) c.captured).getPrec());

        ScilabVariables.sendData("i16", TOP, new short[][] {{7}}, false, id);
        assertEquals(ScilabIntegerTypeEnum.sci_int16, ((ScilabInteger) c.captured).getPrec());

        ScilabVariables.sendUnsignedData("u16", TOP, new short[][] {{7}}, false, id);
        assertEquals(ScilabIntegerTypeEnum.sci_uint16, ((ScilabInteger) c.captured).getPrec());

        ScilabVariables.sendData("i8", TOP, new byte[][] {{7}}, false, id);
        assertEquals(ScilabIntegerTypeEnum.sci_int8, ((ScilabInteger) c.captured).getPrec());

        ScilabVariables.sendUnsignedData("u8", TOP, new byte[][] {{7}}, false, id);
        assertEquals(ScilabIntegerTypeEnum.sci_uint8, ((ScilabInteger) c.captured).getPrec());

        ScilabVariables.sendData("i64", TOP, new long[][] {{7L}}, false, id);
        assertEquals(ScilabIntegerTypeEnum.sci_int64, ((ScilabInteger) c.captured).getPrec());
    }

    @Test
    public void sendIntegerBuffersBecomeReferences() {
        Capture c = new Capture();
        int id = register(c);

        ScilabVariables.sendDataAsBuffer("i8", TOP, ByteBuffer.wrap(new byte[] {1, 2, 3, 4}), 2, 2, id);
        assertEquals(ScilabIntegerTypeEnum.sci_int8, ((ScilabIntegerReference) c.captured).getPrec());

        ScilabVariables.sendUnsignedDataAsBuffer("u8", TOP, ByteBuffer.wrap(new byte[] {1, 2, 3, 4}), 2, 2, id);
        assertEquals(ScilabIntegerTypeEnum.sci_uint8, ((ScilabIntegerReference) c.captured).getPrec());

        ScilabVariables.sendDataAsBuffer("i16", TOP, ShortBuffer.wrap(new short[] {1, 2, 3, 4}), 2, 2, id);
        assertEquals(ScilabIntegerTypeEnum.sci_int16, ((ScilabIntegerReference) c.captured).getPrec());

        ScilabVariables.sendUnsignedDataAsBuffer("u16", TOP, ShortBuffer.wrap(new short[] {1, 2, 3, 4}), 2, 2, id);
        assertEquals(ScilabIntegerTypeEnum.sci_uint16, ((ScilabIntegerReference) c.captured).getPrec());

        ScilabVariables.sendDataAsBuffer("i32", TOP, IntBuffer.wrap(new int[] {1, 2, 3, 4}), 2, 2, id);
        assertEquals(ScilabIntegerTypeEnum.sci_int32, ((ScilabIntegerReference) c.captured).getPrec());

        ScilabVariables.sendUnsignedDataAsBuffer("u32", TOP, IntBuffer.wrap(new int[] {1, 2, 3, 4}), 2, 2, id);
        assertEquals(ScilabIntegerTypeEnum.sci_uint32, ((ScilabIntegerReference) c.captured).getPrec());

        ScilabVariables.sendDataAsBuffer("i64", TOP, LongBuffer.wrap(new long[] {1, 2, 3, 4}), 2, 2, id);
        assertEquals(ScilabIntegerTypeEnum.sci_int64, ((ScilabIntegerReference) c.captured).getPrec());
    }

    @Test
    public void sendBooleanMatrixAndBuffer() {
        Capture c = new Capture();
        int id = register(c);

        ScilabVariables.sendData("b", TOP, new boolean[][] {{true, false}}, false, id);
        ScilabBoolean b = assertInstanceOf(ScilabBoolean.class, c.captured);
        assertEquals(ScilabTypeEnum.sci_boolean, b.getType());

        ScilabVariables.sendBooleanDataAsBuffer("br", TOP, IntBuffer.wrap(new int[] {1, 0, 0, 1}), 2, 2, id);
        assertInstanceOf(ScilabBooleanReference.class, c.captured);
    }

    @Test
    public void sendStringMatrix() {
        Capture c = new Capture();
        ScilabVariables.sendData("s", TOP, new String[][] {{"hello", "world"}}, false, register(c));
        ScilabString s = assertInstanceOf(ScilabString.class, c.captured);
        assertEquals("hello", s.getData()[0][0]);
        assertEquals(ScilabTypeEnum.sci_strings, s.getType());
    }

    @Test
    public void sendRealAndComplexSparse() {
        Capture c = new Capture();
        int id = register(c);

        ScilabVariables.sendData("sp", TOP, 1, 1, 1, new int[] {1}, new int[] {0}, new double[] {5.0}, id);
        ScilabSparse real = assertInstanceOf(ScilabSparse.class, c.captured);
        assertTrue(real.isReal());
        assertEquals(5.0, real.getRealElement(0), EPS);

        ScilabVariables.sendData("spc", TOP, 1, 1, 1, new int[] {1}, new int[] {0}, new double[] {3.0}, new double[] {4.0}, id);
        ScilabSparse complex = assertInstanceOf(ScilabSparse.class, c.captured);
        assertFalse(complex.isReal());
        assertEquals(4.0, complex.getImaginaryElement(0), EPS);
    }

    @Test
    public void sendRealAndComplexPolynomial() {
        Capture c = new Capture();
        int id = register(c);

        ScilabVariables.sendPolynomial("p", TOP, "x", new double[][][] {{{1.0, 2.0, 3.0}}}, false, id);
        ScilabPolynomial real = assertInstanceOf(ScilabPolynomial.class, c.captured);
        assertEquals(ScilabTypeEnum.sci_poly, real.getType());
        assertEquals("p", real.getVarName());

        ScilabVariables.sendPolynomial("pc", TOP, "x",
                                       new double[][][] {{{1.0, 2.0}}}, new double[][][] {{{5.0, 6.0}}}, false, id);
        assertInstanceOf(ScilabPolynomial.class, c.captured);
    }

    @Test
    public void buildAListThroughTheCharProtocol() {
        Capture c = new Capture();
        int id = register(c);
        // Open a top-level list, populate two slots via non-empty indexes, then close.
        ScilabVariables.sendData("mylist", 0, TOP, 'l', id);
        ScilabVariables.sendData(null, new int[] {1}, new double[][] {{1.0}}, false, id);
        ScilabVariables.sendData(null, new int[] {2}, new double[][] {{2.0}}, false, id);
        ScilabVariables.closeList(TOP, id);

        ScilabList list = assertInstanceOf(ScilabList.class, c.captured);
        assertEquals(2, list.size());
        assertEquals(1.0, ((ScilabDouble) list.get(0)).getRealElement(0, 0), EPS);
        assertEquals(2.0, ((ScilabDouble) list.get(1)).getRealElement(0, 0), EPS);
    }

    @Test
    public void buildAnMListThroughTheCharProtocol() {
        Capture c = new Capture();
        int id = register(c);
        ScilabVariables.sendData("mm", 0, TOP, 'm', id);
        ScilabVariables.sendData(null, new int[] {1}, new String[][] {{"myType"}}, false, id);
        ScilabVariables.closeList(TOP, id);
        assertInstanceOf(ScilabMList.class, c.captured);
    }

    @Test
    public void buildATListThroughTheCharProtocol() {
        Capture c = new Capture();
        int id = register(c);
        ScilabVariables.sendData("tt", 0, TOP, 't', id);
        ScilabVariables.sendData(null, new int[] {1}, new String[][] {{"myType"}}, false, id);
        ScilabVariables.closeList(TOP, id);
        assertInstanceOf(ScilabTList.class, c.captured);
    }
}
