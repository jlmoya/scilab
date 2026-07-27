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
import org.scilab.modules.types.ScilabDouble;
import org.scilab.modules.javasci.JavasciException;
import org.scilab.modules.javasci.JavasciException.InitializationException;
import org.scilab.modules.javasci.JavasciException.ScilabErrorException;


public class testErrorManagement {
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
    public void getLastErrorCodeTest() throws NullPointerException, JavasciException {
        // Codes are (expected, actual) — JUnit's order, which this file otherwise
        // has backwards, making its failures read inverted.
        assertEquals(0, sci.getLastErrorCode()); // No error
        sci.close();

        assertEquals(false, sci.open("a=1+"));
        // 999, not the Scilab-5-era 2 (syntax) and 4 (undefined variable) this
        // test used to expect. The current AST/parser engine sets 999 explicitly
        // for these paths (InitScilab.cpp:769, storeCommand.cpp:77), and it is
        // the ENGINE's own answer, not something javasci loses in translation:
        // `execstr("qq+ww", "errcatch")` and `lasterror()` both report 999 from
        // plain Scilab too. Verified against the product before touching the test
        // rather than assuming test rot. See deferred-fixes-register.md B18.
        assertEquals(999, sci.getLastErrorCode());
        sci.exec("errclear();");
        sci.exec("a+b");
        assertEquals(999, sci.getLastErrorCode());
        sci.exec("errclear();");
        // errclear() must reset it, or the assertions above prove nothing.
        assertEquals(0, sci.getLastErrorCode());
    }

    @Test()
    public void getLastErrorMessageTest() throws NullPointerException, JavasciException {
        sci.exec("errclear();"); // No error by default
        assertTrue(sci.getLastErrorMessage().equals(""));
        assertEquals(sci.getLastErrorMessage().length(), 0);

        sci.exec("errclear();");
        sci.close();

        assertEquals(sci.open("a=1+"), false);
        assertTrue(sci.getLastErrorMessage().length() > 0);
        sci.exec("errclear();");
        sci.exec("a+b"); //undefined a & b
        assertTrue(sci.getLastErrorMessage().length() > 0);
        sci.exec("errclear();");
        sci.exec("a=rand(10,10);");//no error
        assertEquals(sci.getLastErrorMessage().length(), 0);
    }

    @Test()
    public void getLastErrorMessageWithExceptionNonErrorTest() throws NullPointerException, JavasciException {
        sci.execException("errclear();"); // No error by default
        assertTrue(sci.getLastErrorMessage().equals(""));
        assertEquals(sci.getLastErrorMessage().length(), 0);

        sci.execException("errclear();");
    }

    @Test()
    public void getLastErrorMessageWithExceptionNonError2Test() throws NullPointerException, JavasciException {
        sci.execException("errclear();"); // No error by default
        assertTrue(sci.getLastErrorMessage().equals(""));
        assertEquals(sci.getLastErrorMessage().length(), 0);
        sci.execException("a=rand(10,10);");//no error
        assertEquals(sci.getLastErrorMessage().length(), 0);
    }


    @Test
    public void getLastErrorMessageWithExceptionWithErrorTest() throws NullPointerException, ScilabErrorException {
        assertThrows(ScilabErrorException.class, () -> {
            sci.execException("a+b"); //undefined a & b
        });
    }

    @Test
    public void getLastErrorMessageWithExceptionWithError2Test() throws NullPointerException, ScilabErrorException {
        assertThrows(ScilabErrorException.class, () -> {
            sci.execException("a+b*"); //undefined a & b
        });
    }

    /**
     * See #open()
     */
    @AfterEach
    public void close() {
        sci.close();
    }
}
