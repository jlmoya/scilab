package org.scilab.tests.modules.javasci;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.scilab.modules.javasci.Scilab;
import org.scilab.modules.javasci.ScilabReferenceException;
import org.scilab.modules.types.ScilabBoolean;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression guards for ScilabBooleanRef -- the third and last live
 * by-reference view (register B18).
 *
 * The pre-existing coverage of the by-reference boolean path is
 * testReadWriteBuf.putAndGetRefBooleanTest, which writes strictly WITHIN the
 * existing shape. That is the benign case: no reallocation, so the raw
 * IntBuffer the marshaller hands out still points at live engine memory and
 * the test passes even with the defect present. Everything here deliberately
 * crosses a reallocation, retypes the variable, or clears it -- the three
 * things a raw buffer cannot survive.
 *
 * A NON-SQUARE 2x4 shape is used throughout: it is the shape where a
 * row/column-major mix-up is visible at all (on an n x n matrix a transposed
 * read still has the right dimensions and looks plausible).
 */
public class ScilabBooleanRefTest {
    private Scilab sci;

    private static boolean[][] sample() {
        return new boolean[][] {{true, false, true, false},
                                {false, true, false, true}};
    }

    @BeforeEach
    public void open() throws Exception {
        sci = new Scilab();
        sci.open();
    }

    @AfterEach
    public void close() {
        sci.close();
    }

    /** The view must report Scilab's write. */
    @Test
    public void refSeesScilabWrite() throws Exception {
        sci.put("a", new ScilabBoolean(sample()));
        ScilabBoolean ref = (ScilabBoolean) sci.getByReference("a");
        sci.exec("a(2,3)=%t;");
        assertTrue(ref.getElement(1, 2));
    }

    /** A write through the view must land in the engine. */
    @Test
    public void scilabSeesRefWrite() throws Exception {
        sci.put("a", new ScilabBoolean(sample()));
        ScilabBoolean ref = (ScilabBoolean) sci.getByReference("a");
        ref.setElement(1, 2, true);
        sci.exec("b=a(2,3);");
        assertTrue(((ScilabBoolean) sci.get("b")).getElement(0, 0));
    }

    /**
     * THE regression guard for the defect this class exists to fix. `a(3,5)`
     * grows the variable past its allocation, so Scilab reallocates and frees
     * the old buffer -- which is exactly the buffer a raw
     * ScilabBooleanReference still points at. An element untouched by the grow
     * must keep its value and the new element must be visible through the same
     * view.
     */
    @Test
    public void refSurvivesReallocatingResize() throws Exception {
        sci.put("a", new ScilabBoolean(sample()));
        ScilabBoolean ref = (ScilabBoolean) sci.getByReference("a");
        sci.exec("a(3,5)=%t;");
        assertTrue(ref.getElement(0, 0));
        assertFalse(ref.getElement(0, 1));
        assertTrue(ref.getElement(2, 4));
    }

    /**
     * The WRITE half of the same defect, and the one the original diagnosis
     * called out by name: after the grow, ScilabBooleanReference.setElement()
     * does intBuffer.put(i + nbRows * j, ...) straight into the freed buffer.
     * The write must instead reach the reallocated variable, which only a
     * re-resolving view can do.
     */
    @Test
    public void refWriteSurvivesReallocatingResize() throws Exception {
        sci.put("a", new ScilabBoolean(sample()));
        ScilabBoolean ref = (ScilabBoolean) sci.getByReference("a");
        sci.exec("a(3,3)=%t;");
        ref.setElement(1, 2, true);
        sci.exec("b=a(2,3);");
        assertTrue(((ScilabBoolean) sci.get("b")).getElement(0, 0));
    }

    /**
     * Once the variable is no longer a boolean, the view must fail loudly
     * instead of returning stale or nonsensical data: live()'s instanceof
     * check is what's under test here.
     */
    @Test
    public void refThrowsAfterTypeChange() throws Exception {
        sci.put("a", new ScilabBoolean(sample()));
        ScilabBoolean ref = (ScilabBoolean) sci.getByReference("a");
        sci.exec("a=double(a);");
        assertThrows(ScilabReferenceException.class, () -> ref.getElement(0, 0));
    }

    /**
     * Clearing the variable is a different failure path through live() than a
     * type change: getInCurrentScilabSession() throws JavasciException rather
     * than returning a non-ScilabBoolean, so this exercises live()'s other
     * catch branch.
     */
    @Test
    public void refThrowsAfterVariableCleared() throws Exception {
        sci.put("a", new ScilabBoolean(sample()));
        ScilabBoolean ref = (ScilabBoolean) sci.getByReference("a");
        sci.exec("clear a;");
        assertThrows(ScilabReferenceException.class, () -> ref.getElement(0, 0));
    }

    /**
     * getVarName() must report the variable's actual name. A ScilabBooleanRef
     * that declares its own varName field would SHADOW rather than populate
     * the inherited one -- fields are not polymorphic -- and the inherited
     * getVarName() would silently return null.
     */
    @Test
    public void refReportsVarName() throws Exception {
        sci.put("a", new ScilabBoolean(sample()));
        ScilabBoolean ref = (ScilabBoolean) sci.getByReference("a");
        assertEquals("a", ref.getVarName());
    }

    /**
     * The WHOLE-MATRIX surface, after an engine-side write. getData(),
     * getRawData(), toString(), hashCode(), isEmpty() and the shape accessors
     * must all reflect the current variable, not the shape and contents
     * captured when the view was taken. This is the surface where a future
     * "optimization" of getWidth()/getData() back to a plain field read would
     * silently start returning stale answers with nothing else failing.
     */
    @Test
    public void wholeMatrixAccessorsReflectEngineWrite() throws Exception {
        sci.put("a", new ScilabBoolean(sample()));
        ScilabBoolean ref = (ScilabBoolean) sci.getByReference("a");
        sci.exec("a(1,2)=%t;");

        assertFalse(ref.isEmpty());
        assertEquals(2, ref.getHeight());
        assertEquals(4, ref.getWidth());

        boolean[][] expected = new boolean[][] {{true, true, true, false},
                                                {false, true, false, true}};
        assertArrayEquals(expected, ref.getData());
        assertArrayEquals(expected, (boolean[][]) ref.getRawData());
        assertArrayEquals(expected, (boolean[][]) ref.getSerializedObject());

        assertEquals("[%t, %t, %t, %f ; %f, %t, %f, %t]", ref.toString());

        ScilabBoolean byValue = (ScilabBoolean) sci.get("a");
        assertEquals(byValue.hashCode(), ref.hashCode());
        assertTrue(ref.equals(byValue));
    }
}
