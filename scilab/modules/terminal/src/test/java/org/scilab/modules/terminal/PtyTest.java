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

package org.scilab.modules.terminal;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Hermetic unit tests for the pure-Java surface of {@link Pty}.
 *
 * <p>{@code Pty} is a JNA bridge to libc, but an <em>unstarted</em> PTY never touches
 * native code: the libc handle lives in the {@code Pty.C} interface's static field,
 * which is only initialised on first active use of {@code C.I}. The initial-state
 * accessors and the idempotent teardown path both guard on {@code masterFd >= 0} /
 * {@code childPid > 0}, so on a fresh instance neither branch runs and {@code
 * Native.load} is never called. These tests exercise exactly that hermetic surface
 * (plus the pure {@code nullTerminate} argv/envp helper via reflection); they never
 * call {@code start()}, {@code read()}, {@code write()} or any other native path.
 */
public class PtyTest {

    @Test
    public void freshPtyHasNoMasterFdOrChild() {
        Pty pty = new Pty();
        assertEquals(-1, pty.masterFd(), "an unstarted PTY exposes no master fd");
        assertEquals(-1, pty.pid(), "an unstarted PTY has no child pid");
    }

    @Test
    public void closeOnUnstartedPtyIsANoOpAndIdempotent() {
        Pty pty = new Pty();
        // close() guards on masterFd >= 0; on a fresh PTY it makes no libc call and
        // must be safe to call repeatedly.
        assertDoesNotThrow(pty::close);
        assertDoesNotThrow(pty::close);
        assertEquals(-1, pty.masterFd());
    }

    @Test
    public void terminateOnUnstartedPtyIsANoOpAndIdempotent() {
        Pty pty = new Pty();
        // terminate() guards on childPid > 0 (SIGHUP/reap branch) and then calls the
        // guarded close(); on a fresh PTY neither branch runs, so it touches no libc
        // and is safe from any thread / a JVM shutdown hook, exactly as documented.
        assertDoesNotThrow(pty::terminate);
        assertDoesNotThrow(pty::terminate);
        assertEquals(-1, pty.pid());
        assertEquals(-1, pty.masterFd());
    }

    @Test
    public void nullTerminateAppendsANullTerminatorPreservingOrder() throws Exception {
        Method m = Pty.class.getDeclaredMethod("nullTerminate", String[].class);
        m.setAccessible(true);

        String[] in = {"/bin/zsh", "-l", "-i"};
        String[] out = (String[]) m.invoke(null, (Object) in);

        assertEquals(4, out.length, "exactly one slot is added for the NULL terminator");
        assertArrayEquals(new String[] {"/bin/zsh", "-l", "-i", null}, out);
        // The helper copies into a new array; the caller's argv is left untouched.
        assertArrayEquals(new String[] {"/bin/zsh", "-l", "-i"}, in);
    }

    @Test
    public void nullTerminateOnEmptyArrayYieldsASingleNull() throws Exception {
        Method m = Pty.class.getDeclaredMethod("nullTerminate", String[].class);
        m.setAccessible(true);

        String[] out = (String[]) m.invoke(null, (Object) new String[0]);
        assertEquals(1, out.length);
        assertNull(out[0], "an empty argv becomes just the NULL terminator");
    }
}
