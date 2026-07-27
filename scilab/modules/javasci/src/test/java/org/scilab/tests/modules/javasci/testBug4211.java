/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2010 - DIGITEO - Sylvestre LEDRU
 *
 * Copyright (C) 2012 - 2016 - Scilab Enterprises
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 *
 */
package org.scilab.tests.modules.javasci;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

import org.scilab.modules.javasci.Scilab;
import org.scilab.modules.javasci.JavasciException;
import org.scilab.modules.javasci.JavasciException.InitializationException;
import org.scilab.modules.javasci.JavasciException.UnsupportedTypeException;
import org.scilab.modules.javasci.JavasciException.UndefinedVariableException;
import org.scilab.modules.types.ScilabType;
import org.scilab.modules.types.ScilabDouble;
import org.scilab.modules.types.ScilabBoolean;
import org.scilab.modules.types.ScilabString;
import org.scilab.modules.types.ScilabTypeEnum;

public class testBug4211 {
    private Scilab sci;

    /*
     * This method will be called for each test.
     * with @AfterEach, this ensures that all the time the engine is closed
     * especially in case of error.
     * Otherwise, the engine might be still running and all subsequent tests
     * would fail.
     */
    @BeforeEach
    public void open() throws NullPointerException, JavasciException {
        sci = new Scilab();
        assertTrue(sci.open());
    }

    @Test()
    public void nonRegBug4211() throws NullPointerException, JavasciException {
        // (expected, actual) — JUnit's order; this file had it backwards.
        assertEquals(false, sci.exec("disp(plop);"));
        // 999, not the Scilab-5-era 4 ("undefined variable"). The current
        // AST/parser engine reports 999 for this path and it is the ENGINE's own
        // answer, not a javasci translation loss: plain Scilab's
        // `execstr(...,"errcatch")` and `lasterror()` both say 999 too. Verified
        // against the product before editing the test. Same rot as
        // testErrorManagement.getLastErrorCodeTest; see register B18.
        assertEquals(999, sci.getLastErrorCode());
        sci.close();

    }

    /**
     * See #open()
     */
    @AfterEach
    public void close() {
        sci.close();

    }
}