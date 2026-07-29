package org.scilab.tests.modules.javasci;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.scilab.modules.javasci.Scilab;
import org.scilab.modules.javasci.ScilabReferenceException;
import org.scilab.modules.types.ScilabInteger;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Regression guards for the two review findings testReadWriteBuf itself does
 * not exercise: the two live() failure branches (ScilabReferenceException),
 * and the inherited varName field (getVarName()). testReadWriteBuf never
 * retypes or clears the variable it holds a reference to, and never calls
 * getVarName() -- both of those live() branches, and the constructor's
 * varName wiring, would be unguarded without these.
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
}
