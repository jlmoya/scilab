package org.scilab.tests.modules.javasci;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.scilab.modules.javasci.Scilab;
import org.scilab.modules.javasci.ScilabReferenceException;
import org.scilab.modules.types.ScilabDouble;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ScilabDoubleRefTest {
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

    /** The view must report Scilab's write, including when the assignment reallocates. */
    @Test
    public void refSeesScilabWrite() throws Exception {
        sci.put("a", new ScilabDouble(new double[][] {{1.0, 2.0}, {3.0, 4.0}}));
        ScilabDouble ref = (ScilabDouble) sci.getByReference("a");
        sci.exec("a(2,2)=99;");
        assertEquals(99.0, ref.getRealElement(1, 1), 1e-9);
    }

    /** A write through the view must land in the engine. */
    @Test
    public void scilabSeesRefWrite() throws Exception {
        sci.put("a", new ScilabDouble(new double[][] {{1.0, 2.0}, {3.0, 4.0}}));
        ScilabDouble ref = (ScilabDouble) sci.getByReference("a");
        ref.setRealElement(1, 1, 42.5);
        sci.exec("b=a(2,2);");
        assertEquals(42.5, ((ScilabDouble) sci.get("b")).getRealPart()[0][0], 1e-9);
    }

    /**
     * The view must survive a RESIZE, which reallocates the underlying variable:
     * an element untouched by the resize must keep its original value, and the
     * newly written element must be visible through the same view. Neither test
     * above triggers a reallocation (both write within the existing 2x2 shape),
     * so this is the regression guard for the actual defect the class exists to
     * fix -- a raw by-reference double reads freed memory here instead.
     */
    @Test
    public void refSurvivesReallocatingResize() throws Exception {
        sci.put("a", new ScilabDouble(new double[][] {{1.0, 2.0}, {3.0, 4.0}}));
        ScilabDouble ref = (ScilabDouble) sci.getByReference("a");
        sci.exec("a(3,3)=77;");
        assertEquals(4.0, ref.getRealElement(1, 1), 1e-9);
        assertEquals(77.0, ref.getRealElement(2, 2), 1e-9);
    }

    /**
     * Once the variable is no longer a double, the view must fail loudly
     * instead of returning stale or nonsensical data: live()'s instanceof
     * check is what's under test here.
     */
    @Test
    public void refThrowsAfterTypeChange() throws Exception {
        sci.put("a", new ScilabDouble(new double[][] {{1.0, 2.0}, {3.0, 4.0}}));
        ScilabDouble ref = (ScilabDouble) sci.getByReference("a");
        sci.exec("a=int32(a);");
        assertThrows(ScilabReferenceException.class, () -> ref.getRealElement(0, 0));
    }

    /**
     * Clearing the variable is a different failure path through live() than a
     * type change: getInCurrentScilabSession() throws JavasciException rather
     * than returning a non-ScilabDouble, so this exercises live()'s other
     * catch branch.
     */
    @Test
    public void refThrowsAfterVariableCleared() throws Exception {
        sci.put("a", new ScilabDouble(new double[][] {{1.0, 2.0}, {3.0, 4.0}}));
        ScilabDouble ref = (ScilabDouble) sci.getByReference("a");
        sci.exec("clear a;");
        assertThrows(ScilabReferenceException.class, () -> ref.getRealElement(0, 0));
    }

    /**
     * getVarName() must report the variable's actual name. A ScilabDoubleRef
     * that shadows rather than populates the inherited varName field would
     * silently return null here instead.
     */
    @Test
    public void refReportsVarName() throws Exception {
        sci.put("a", new ScilabDouble(new double[][] {{1.0, 2.0}, {3.0, 4.0}}));
        ScilabDouble ref = (ScilabDouble) sci.getByReference("a");
        assertEquals("a", ref.getVarName());
    }

    /**
     * Inherited ScilabDouble.setElement(i,j,x,y) assigns realPart[i][j]/
     * imaginaryPart[i][j] directly, bypassing setRealElement()/
     * setImaginaryElement(). Left un-overridden, the write would silently
     * land in this object's frozen construction-time snapshot and never
     * reach the engine -- no exception, no signal.
     *
     * Uses a complex matrix: setElement() always writes both parts
     * (realPart[i][j] AND imaginaryPart[i][j]), and a real-only ScilabDouble's
     * imaginaryPart is a genuinely empty double[0][] -- calling setElement on
     * one throws ArrayIndexOutOfBoundsException regardless of ScilabDoubleRef,
     * identically on a plain ScilabDouble. That is pre-existing behavior of
     * setElement() itself, not something this fix needs to work around.
     */
    @Test
    public void scilabSeesRefSetElementWrite() throws Exception {
        sci.put("a", new ScilabDouble(new double[][] {{1.0, 2.0}, {3.0, 4.0}},
                                       new double[][] {{0.0, 0.0}, {0.0, 0.0}}));
        ScilabDouble ref = (ScilabDouble) sci.getByReference("a");
        ref.setElement(1, 1, 12.5, 3.5);
        sci.exec("b=a(2,2);");
        ScilabDouble b = (ScilabDouble) sci.get("b");
        assertEquals(12.5, b.getRealPart()[0][0], 1e-9);
        assertEquals(3.5, b.getImaginaryPart()[0][0], 1e-9);
    }

    /**
     * getHeight()/getWidth() must report the CURRENT engine shape, not the
     * shape captured at getByReference() time -- otherwise a natural
     * `for (i < ref.getHeight())` loop would silently miss rows added after
     * the view was taken.
     */
    @Test
    public void refReportsCurrentShapeAfterResize() throws Exception {
        sci.put("a", new ScilabDouble(new double[][] {{1.0, 2.0}, {3.0, 4.0}}));
        ScilabDouble ref = (ScilabDouble) sci.getByReference("a");
        sci.exec("a(3,3)=77;");
        assertEquals(3, ref.getHeight());
        assertEquals(3, ref.getWidth());
    }

    /**
     * The WHOLE-MATRIX surface, after an engine-side write that also RESIZES.
     * Every other test in this class uses only per-element accessors, which is
     * exactly the gap this closes: nothing anywhere called getRealPart(),
     * getRawRealPart(), getSerializedObject(), toString(), hashCode() or
     * isEmpty() on a live view, so a future "optimization" of getWidth() or
     * getRealPart() back to a plain field read would have gone unnoticed while
     * silently breaking equals() and toString().
     *
     * The matrix is NON-SQUARE (2x4 grown to 2x5) on purpose. That is the only
     * shape where a row/column-major mix-up is visible at all: on an n x n
     * matrix the whole-matrix getters return the exact TRANSPOSE, which still
     * has the right dimensions and looks entirely plausible.
     */
    @Test
    public void wholeMatrixAccessorsReflectEngineWrite() throws Exception {
        sci.put("a", new ScilabDouble(new double[][] {{1.0, 2.0, 3.0, 4.0},
                                                      {5.0, 6.0, 7.0, 8.0}}));
        ScilabDouble ref = (ScilabDouble) sci.getByReference("a");
        sci.exec("a(1,5)=99;");

        assertFalse(ref.isEmpty());
        assertTrue(ref.isReal());
        assertEquals(2, ref.getHeight());
        assertEquals(5, ref.getWidth());

        double[][] expected = new double[][] {{1.0, 2.0, 3.0, 4.0, 99.0},
                                              {5.0, 6.0, 7.0, 8.0, 0.0}};
        assertArrayEquals(expected, ref.getRealPart());
        assertArrayEquals(expected, (double[][]) ref.getRawRealPart());
        assertArrayEquals(expected, (double[][]) ((Object[]) ref.getSerializedObject())[0]);

        assertEquals("[1, 2, 3, 4, 99 ; 5, 6, 7, 8, 0]", ref.toString());

        ScilabDouble byValue = (ScilabDouble) sci.get("a");
        assertEquals(byValue.hashCode(), ref.hashCode());
        assertTrue(ref.equals(byValue));
    }

    /**
     * getSerializedComplexMatrix() is overridden to resolve the variable once
     * instead of inheriting a body whose loop conditions call getHeight()/
     * getWidth() per iteration. This checks the delegation actually produces
     * the engine's current contents (and does not recurse): the serialized
     * form is column-major, real block first, then the imaginary block.
     */
    @Test
    public void serializedComplexMatrixReflectsEngineWrite() throws Exception {
        sci.put("a", new ScilabDouble(new double[][] {{1.0, 2.0, 3.0}},
                                       new double[][] {{4.0, 5.0, 6.0}}));
        ScilabDouble ref = (ScilabDouble) sci.getByReference("a");
        sci.exec("a(1,2)=7+8*%i;");

        assertArrayEquals(new double[] {1.0, 7.0, 3.0, 4.0, 8.0, 6.0},
                          ref.getSerializedComplexMatrix(), 1e-9);
    }
}
