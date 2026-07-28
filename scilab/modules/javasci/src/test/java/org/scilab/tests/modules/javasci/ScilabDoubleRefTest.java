package org.scilab.tests.modules.javasci;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.scilab.modules.javasci.Scilab;
import org.scilab.modules.types.ScilabDouble;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
