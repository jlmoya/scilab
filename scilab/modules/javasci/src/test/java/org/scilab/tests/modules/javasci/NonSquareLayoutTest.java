/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 */

package org.scilab.tests.modules.javasci;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.scilab.modules.javasci.Scilab;
import org.scilab.modules.types.ScilabBoolean;
import org.scilab.modules.types.ScilabDouble;
import org.scilab.modules.types.ScilabInteger;
import org.scilab.modules.types.ScilabIntegerTypeEnum;
import org.scilab.modules.types.ScilabString;
import org.scilab.modules.types.ScilabType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Register B23(c): the row recorded a suspected layout defect on the STRING
 * by-reference path but never said what it was, and the suspicion was never
 * tested against a running engine.
 *
 * B23(b) was a transposition: the whole-matrix getters read the engine buffer
 * row-major while the per-element accessors read it column-major, so every
 * shape came back wrong and SQUARE matrices came back as an exact transpose --
 * right dimensions, right values, wrong positions. NON-SQUARE shapes are the
 * cheap detector for that class of bug, because a wrong layout there cannot
 * even produce the right dimensions.
 *
 * So this pins the round trip for every marshalled scalar type at a non-square
 * 2x3, through BOTH get() and getByReference(). It also pins the fact that
 * getByReference degrades to by-value for strings: ScilabToJava.cpp's
 * sci_strings branch passes false rather than propagating byref, and there is
 * no ScilabStringReference for it to build.
 */
public class NonSquareLayoutTest {

    /** Deliberately non-square: a transposition cannot survive 2x3 unnoticed. */
    private static final int R = 2;
    private static final int C = 3;

    private Scilab sci;

    @BeforeEach
    public void open() throws Exception {
        sci = new Scilab();
        sci.open();
    }

    @AfterEach
    public void close() {
        sci.close();
    }

    private static String cell(int i, int j) {
        return "r" + i + "c" + j;
    }

    private void assertStringLayout(ScilabType value, String what) {
        assertInstanceOf(ScilabString.class, value, what + ": type");
        ScilabString s = (ScilabString) value;
        assertEquals(R, s.getHeight(), what + ": rows");
        assertEquals(C, s.getWidth(), what + ": cols");
        for (int i = 0; i < R; i++) {
            for (int j = 0; j < C; j++) {
                assertEquals(cell(i, j), s.getData()[i][j], what + ": element (" + i + "," + j + ")");
            }
        }
    }

    @Test
    public void nonSquareStringRoundTripsByValue() throws Exception {
        String[][] data = new String[R][C];
        for (int i = 0; i < R; i++) {
            for (int j = 0; j < C; j++) {
                data[i][j] = cell(i, j);
            }
        }
        sci.put("s", new ScilabString(data));
        assertStringLayout(sci.get("s"), "get");
    }

    @Test
    public void nonSquareStringByReferenceMatchesByValue() throws Exception {
        String[][] data = new String[R][C];
        for (int i = 0; i < R; i++) {
            for (int j = 0; j < C; j++) {
                data[i][j] = cell(i, j);
            }
        }
        sci.put("s", new ScilabString(data));

        ScilabType ref = sci.getByReference("s");
        assertStringLayout(ref, "getByReference");

        // Strings have no by-reference view: ScilabToJava.cpp's sci_strings
        // branch hardcodes byref false, so what comes back is an ordinary
        // detached ScilabString. Pin BOTH halves of that. The exact class (not
        // just instanceof) is what fails the day someone adds a
        // ScilabStringReference, forcing them to state its layout contract --
        // isReference() would not catch it, since it is hardcoded false on the
        // existing views too.
        assertSame(ScilabString.class, ref.getClass(),
                   "no ScilabStringReference exists; getByReference must degrade to by-value");
        sci.exec("s(1,1) = \"CHANGED\";");
        assertEquals(cell(0, 0), ((ScilabString) ref).getData()[0][0],
                     "the returned string must be detached, not a live view");
    }

    @Test
    public void nonSquareDoubleRoundTripsBothWays() throws Exception {
        double[][] data = new double[R][C];
        for (int i = 0; i < R; i++) {
            for (int j = 0; j < C; j++) {
                data[i][j] = i * 10 + j;
            }
        }
        sci.put("d", new ScilabDouble(data));

        for (ScilabType value : new ScilabType[] {sci.get("d"), sci.getByReference("d")}) {
            ScilabDouble d = (ScilabDouble) value;
            assertEquals(R, d.getHeight());
            assertEquals(C, d.getWidth());
            for (int i = 0; i < R; i++) {
                for (int j = 0; j < C; j++) {
                    assertEquals(i * 10 + j, d.getRealElement(i, j), 1e-9,
                                 "element (" + i + "," + j + ")");
                    assertEquals(i * 10 + j, d.getRealPart()[i][j], 1e-9,
                                 "whole-matrix (" + i + "," + j + ")");
                }
            }
        }
    }

    @Test
    public void nonSquareIntegerRoundTripsBothWays() throws Exception {
        int[][] data = new int[R][C];
        for (int i = 0; i < R; i++) {
            for (int j = 0; j < C; j++) {
                data[i][j] = i * 10 + j;
            }
        }
        sci.put("n", new ScilabInteger(data, false));

        for (ScilabType value : new ScilabType[] {sci.get("n"), sci.getByReference("n")}) {
            ScilabInteger n = (ScilabInteger) value;
            assertEquals(R, n.getHeight());
            assertEquals(C, n.getWidth());
            assertEquals(ScilabIntegerTypeEnum.sci_int32, n.getPrec());
            for (int i = 0; i < R; i++) {
                for (int j = 0; j < C; j++) {
                    assertEquals((long) (i * 10 + j), n.getElement(i, j),
                                 "element (" + i + "," + j + ")");
                    assertEquals(i * 10 + j, n.getDataAsInt()[i][j],
                                 "whole-matrix (" + i + "," + j + ")");
                }
            }
        }
    }

    @Test
    public void nonSquareBooleanRoundTripsBothWays() throws Exception {
        // Asymmetric under transposition: (0,1) and (1,0) differ.
        boolean[][] data = new boolean[R][C];
        for (int i = 0; i < R; i++) {
            for (int j = 0; j < C; j++) {
                data[i][j] = ((i + 2 * j) % 3) == 0;
            }
        }
        sci.put("b", new ScilabBoolean(data));

        for (ScilabType value : new ScilabType[] {sci.get("b"), sci.getByReference("b")}) {
            ScilabBoolean b = (ScilabBoolean) value;
            assertEquals(R, b.getHeight());
            assertEquals(C, b.getWidth());
            for (int i = 0; i < R; i++) {
                for (int j = 0; j < C; j++) {
                    assertEquals(((i + 2 * j) % 3) == 0, b.getElement(i, j),
                                 "element (" + i + "," + j + ")");
                    assertEquals(((i + 2 * j) % 3) == 0, b.getData()[i][j],
                                 "whole-matrix (" + i + "," + j + ")");
                }
            }
        }
    }
}
