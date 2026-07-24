/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Scilab Enterprises
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 *
 */

package org.scilab.modules.graphic_objects.graphicObject;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import org.scilab.modules.graphic_objects.graphicObject.GraphicObject.UpdateStatus;

/**
 * Hermetic unit tests for {@link CallBack}: a plain command/command-type holder
 * used to back the callback property of every graphic object.
 */
public class CallBackTest {

    @Test
    public void singleArgConstructorDefaultsToUntyped() {
        CallBack cb = new CallBack("plot(1:10)");
        assertEquals("plot(1:10)", cb.getCommand());
        assertEquals(CallBack.UNTYPED, cb.getCommandType());
        assertEquals(-1, CallBack.UNTYPED);
    }

    @Test
    public void twoArgConstructorStoresBoth() {
        CallBack cb = new CallBack("disp(1)", CallBack.SCILAB_INSTRUCTION);
        assertEquals("disp(1)", cb.getCommand());
        assertEquals(CallBack.SCILAB_INSTRUCTION, cb.getCommandType());
    }

    @Test
    public void publicTypeConstantsHaveExpectedValues() {
        assertEquals(-1, CallBack.UNTYPED);
        assertEquals(0, CallBack.SCILAB_INSTRUCTION);
        assertEquals(10, CallBack.SCILAB_NOT_INTERRUPTIBLE_INSTRUCTION);
        assertEquals(1, CallBack.C_FORTRAN);
        assertEquals(2, CallBack.SCILAB_FUNCTION);
        assertEquals(12, CallBack.SCILAB_NOT_INTERRUPTIBLE_FUNCTION);
        assertEquals(-2, CallBack.SCILAB_OUT_OF_XCLICK_AND_XGETMOUSE);
        assertEquals(3, CallBack.JAVA);
        assertEquals(-3, CallBack.JAVA_OUT_OF_XCLICK_AND_XGETMOUSE);
        assertEquals(4, CallBack.SCILAB_INSTRUCTION_WITHOUT_GCBO);
    }

    @Test
    public void setCommandUpdatesValueAndAlwaysReportsSuccess() {
        CallBack cb = new CallBack("");
        assertEquals(UpdateStatus.Success, cb.setCommand("f()"));
        assertEquals("f()", cb.getCommand());
        // No change detection: setting the same value again still reports Success.
        assertEquals(UpdateStatus.Success, cb.setCommand("f()"));
    }

    @Test
    public void setCommandTypeUpdatesValueAndAlwaysReportsSuccess() {
        CallBack cb = new CallBack("");
        assertEquals(UpdateStatus.Success, cb.setCommandType(CallBack.JAVA));
        assertEquals(CallBack.JAVA, cb.getCommandType());
        assertEquals(UpdateStatus.Success, cb.setCommandType(CallBack.JAVA));
    }

    @Test
    public void constructorAcceptsNullCommand() {
        CallBack cb = new CallBack(null);
        assertNull(cb.getCommand());
        assertEquals(CallBack.UNTYPED, cb.getCommandType());
    }
}
