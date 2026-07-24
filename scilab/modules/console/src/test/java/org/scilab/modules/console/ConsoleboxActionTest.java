/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Scilab
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 *
 */

package org.scilab.modules.console;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link ConsoleboxAction}. The consolebox toggle is a
 * Windows-only feature, so the action's whole payload is gated behind a private
 * static {@code isWindows()} check. On every non-Windows platform (where these
 * tests actually run) {@code actionPerformed} must be an inert no-op that never
 * reaches the native interpreter queue.
 */
public class ConsoleboxActionTest {

    private static boolean invokeIsWindows() throws Exception {
        Method m = ConsoleboxAction.class.getDeclaredMethod("isWindows");
        m.setAccessible(true);
        return (Boolean) m.invoke(null);
    }

    @Test
    public void isWindowsAgreesWithTheOsNameProperty() throws Exception {
        boolean expected = System.getProperty("os.name").toLowerCase().contains("windows");
        assertEquals(expected, invokeIsWindows());
    }

    @Test
    public void actionPerformedIsANoOpOnNonWindowsPlatforms() throws Exception {
        assumeFalse(invokeIsWindows(), "payload path is Windows-only and reaches native code");
        ConsoleboxAction action = new ConsoleboxAction();
        // No configuration wired: on non-Windows the action must not touch anything.
        assertDoesNotThrow(() -> action.actionPerformed(null));
    }
}
