package org.scilab.tests.modules.javasci;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.scilab.modules.javasci.Scilab;
import org.scilab.modules.javasci.ScilabReferenceException;
import org.scilab.modules.types.ScilabInteger;
import org.scilab.modules.types.ScilabIntegerTypeEnum;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression guards for what testReadWriteBuf does not exercise: the two
 * live() failure branches (ScilabReferenceException), the inherited varName
 * field (getVarName()), the reallocating resize, and the whole-matrix
 * accessor surface. testReadWriteBuf never retypes or clears the variable it
 * holds a reference to, never calls getVarName(), and uses only
 * getElement/setElement.
 *
 * The reallocation guard in particular is here so that the integer half of
 * register B18 does not depend on a LEGACY file: it was previously covered
 * only inside testReadWriteBuf, so editing that file could have removed the
 * guard with nothing to notice.
 */
public class ScilabIntegerRefTest {
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

    /**
     * Once the variable is no longer an integer, the view must fail loudly
     * instead of returning stale or nonsensical data: live()'s instanceof
     * check is what's under test here.
     */
    @Test
    public void refThrowsAfterTypeChange() throws Exception {
        sci.put("a", new ScilabInteger(new byte[][] {{1, 2}, {3, 4}}, false));
        ScilabInteger ref = (ScilabInteger) sci.getByReference("a");
        sci.exec("a=double(a);");
        assertThrows(ScilabReferenceException.class, () -> ref.getElement(0, 0));
    }

    /**
     * Clearing the variable is a different failure path through live() than a
     * type change: getInCurrentScilabSession() throws JavasciException rather
     * than returning a non-ScilabInteger, so this exercises live()'s other
     * catch branch.
     */
    @Test
    public void refThrowsAfterVariableCleared() throws Exception {
        sci.put("a", new ScilabInteger(new byte[][] {{1, 2}, {3, 4}}, false));
        ScilabInteger ref = (ScilabInteger) sci.getByReference("a");
        sci.exec("clear a;");
        assertThrows(ScilabReferenceException.class, () -> ref.getElement(0, 0));
    }

    /**
     * getVarName() must report the variable's actual name. A ScilabIntegerRef
     * that shadows rather than populates the inherited varName field would
     * silently return null here instead.
     */
    @Test
    public void refReportsVarName() throws Exception {
        sci.put("a", new ScilabInteger(new byte[][] {{1, 2}, {3, 4}}, false));
        ScilabInteger ref = (ScilabInteger) sci.getByReference("a");
        assertEquals("a", ref.getVarName());
    }

    /**
     * THE regression guard for the defect this class exists to fix, and the
     * one thing this class did NOT have of its own: until now the integer
     * reallocation case was covered only by the legacy testReadWriteBuf, so
     * editing that file could have silently removed the guard entirely.
     *
     * `a(3,5)` grows the variable past its allocation, so Scilab reallocates
     * and frees the old buffer -- the buffer a raw ScilabIntegerReference
     * still points at. Both a read and a write must survive it.
     */
    @Test
    public void refSurvivesReallocatingResize() throws Exception {
        sci.put("a", new ScilabInteger(new byte[][] {{1, 2, 3, 4}, {5, 6, 7, 8}}, false));
        ScilabInteger ref = (ScilabInteger) sci.getByReference("a");
        sci.exec("a(3,5)=int8(77);");

        assertEquals(1, ref.getElement(0, 0));
        assertEquals(8, ref.getElement(1, 3));
        assertEquals(77, ref.getElement(2, 4));

        ref.setElement(1, 2, 42);
        sci.exec("b=a(2,3);");
        assertEquals(42, ((ScilabInteger) sci.get("b")).getElement(0, 0));
    }

    /**
     * The WHOLE-MATRIX surface, after an engine-side write that also RESIZES.
     * Every other test here -- and the legacy testReadWriteBuf -- uses only
     * getElement/setElement, so nothing anywhere called getDataAsByte(),
     * getCorrectData(), getData(), getRawData(), toString(), hashCode() or
     * isEmpty() on a live view. That is exactly where a future "optimization"
     * of getWidth() or getCorrectData() back to a plain field read would
     * silently break equals() and toString() with nothing failing.
     *
     * The matrix is NON-SQUARE (2x4 grown to 2x5) on purpose: on an n x n
     * matrix a row/column-major mix-up returns the exact transpose, which
     * still has the right dimensions and looks entirely plausible.
     */
    @Test
    public void wholeMatrixAccessorsReflectEngineWrite() throws Exception {
        sci.put("a", new ScilabInteger(new byte[][] {{1, 2, 3, 4}, {5, 6, 7, 8}}, false));
        ScilabInteger ref = (ScilabInteger) sci.getByReference("a");
        sci.exec("a(1,5)=int8(99);");

        assertFalse(ref.isEmpty());
        assertFalse(ref.isUnsigned());
        assertEquals(ScilabIntegerTypeEnum.sci_int8, ref.getPrec());
        assertEquals(2, ref.getHeight());
        assertEquals(5, ref.getWidth());

        byte[][] expected = new byte[][] {{1, 2, 3, 4, 99}, {5, 6, 7, 8, 0}};
        assertArrayEquals(expected, ref.getDataAsByte());
        assertArrayEquals(expected, (byte[][]) ref.getCorrectData());
        assertArrayEquals(expected, (byte[][]) ref.getRawData());

        long[][] expectedLong = new long[][] {{1L, 2L, 3L, 4L, 99L}, {5L, 6L, 7L, 8L, 0L}};
        assertArrayEquals(expectedLong, ref.getData());

        assertEquals("int8([1, 2, 3, 4, 99 ; 5, 6, 7, 8, 0])", ref.toString());

        ScilabInteger byValue = (ScilabInteger) sci.get("a");
        assertEquals(byValue.hashCode(), ref.hashCode());
        assertTrue(ref.equals(byValue));
    }
}
