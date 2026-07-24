/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Jose Moya
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 *
 */

package org.scilab.modules.action_binding.highlevel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link ScilabInterpreterManagement#buildCall}, the
 * pure Java that turns a function name plus Java arguments into the text of a
 * Scilab call. No interpreter, no native code and no engine is required — only
 * the argument-rendering rules are exercised.
 */
class ScilabInterpreterManagementTest {

    @AfterAll
    static void shutdownExecutor() {
        // Class init created a single-thread ExecutorService (a non-daemon
        // worker). Shut it down so the forked test JVM leaves nothing running.
        ScilabInterpreterManagement.forceClose();
    }

    @Test
    void noArgsProducesEmptyParentheses() {
        assertEquals("foo()", ScilabInterpreterManagement.buildCall("foo"));
    }

    @Test
    void stringArgumentIsDoubleQuoted() {
        assertEquals("disp(\"hello\")", ScilabInterpreterManagement.buildCall("disp", "hello"));
    }

    @Test
    void charArrayArgumentIsRawAndUnquoted() {
        // char[] is the documented escape hatch for a raw identifier.
        assertEquals("disp(x)", ScilabInterpreterManagement.buildCall("disp", new char[] {'x'}));
        assertEquals("clear(myVar)", ScilabInterpreterManagement.buildCall("clear", "myVar".toCharArray()));
    }

    @Test
    void booleanArgumentsBecomeScilabBooleans() {
        assertEquals("f(%t)", ScilabInterpreterManagement.buildCall("f", Boolean.TRUE));
        assertEquals("f(%f)", ScilabInterpreterManagement.buildCall("f", Boolean.FALSE));
        // autoboxed primitives take the same branch
        assertEquals("g(%t, %f)", ScilabInterpreterManagement.buildCall("g", true, false));
    }

    @Test
    void numericArgumentsUseTheirToString() {
        assertEquals("f(42)", ScilabInterpreterManagement.buildCall("f", 42));
        assertEquals("f(3.5)", ScilabInterpreterManagement.buildCall("f", 3.5));
        assertEquals("f(-7)", ScilabInterpreterManagement.buildCall("f", -7L));
    }

    @Test
    void nonStringCharSequenceIsStillQuoted() {
        // The branch keys off CharSequence, not String specifically.
        assertEquals("f(\"ab\")", ScilabInterpreterManagement.buildCall("f", new StringBuilder("ab")));
    }

    @Test
    void multipleMixedArgumentsAreCommaSpaceSeparated() {
        String cmd = ScilabInterpreterManagement.buildCall(
            "plot", "x", 1, Boolean.TRUE, new char[] {'h'});
        assertEquals("plot(\"x\", 1, %t, h)", cmd);
    }

    @Test
    void embeddedDoubleQuotesAreNotEscaped() {
        // buildCall wraps a CharSequence in double quotes but does NOT escape an
        // embedded quote. This pins the actual (verbatim) behaviour.
        assertEquals("f(\"a\"b\")", ScilabInterpreterManagement.buildCall("f", "a\"b"));
    }

    @Test
    void nullElementIsRenderedAsLiteralNull() {
        assertEquals("f(null)", ScilabInterpreterManagement.buildCall("f", new Object[] {null}));
    }

    @Test
    void functionNameIsPreservedVerbatim() {
        String cmd = ScilabInterpreterManagement.buildCall("my_ns_fn", 1);
        assertTrue(cmd.startsWith("my_ns_fn("), "the function name prefixes the call");
        assertTrue(cmd.endsWith(")"), "the call is closed with a parenthesis");
    }
}
