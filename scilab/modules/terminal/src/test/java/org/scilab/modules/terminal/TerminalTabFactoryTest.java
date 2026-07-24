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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Hermetic unit tests for {@link TerminalTabFactory}: the tab-factory singleton and
 * its identity/metadata contract.
 *
 * <p>Only the singleton bookkeeping and the constant-returning metadata getters are
 * exercised - none of these touch GUI or native state. The {@code getTab(uuid)} /
 * {@code isAValidUUID(uuid)} methods delegate into {@code ScilabTerminal} (which
 * builds the heavy JediTerm/FlexDock graph and spawns a shell) and are deliberately
 * not called.
 */
public class TerminalTabFactoryTest {

    @Test
    public void getInstanceReturnsASingleton() {
        TerminalTabFactory a = TerminalTabFactory.getInstance();
        TerminalTabFactory b = TerminalTabFactory.getInstance();
        assertNotNull(a);
        assertSame(a, b, "getInstance() always returns the one cached factory");
    }

    @Test
    public void theFirstConstructedInstanceWinsForever() {
        TerminalTabFactory singleton = TerminalTabFactory.getInstance();
        // The constructor only claims the static slot while it is still null, so a
        // later `new` yields a distinct object that does NOT displace the singleton.
        TerminalTabFactory another = new TerminalTabFactory();
        assertNotSame(singleton, another, "a freshly new-ed factory is a different object");
        assertSame(singleton, TerminalTabFactory.getInstance(),
                   "getInstance() still returns the original singleton");
    }

    @Test
    public void applicationIsEmptySoTerminalsAreNeverPersisted() {
        // Documented contract: "" keeps ephemeral terminals out of
        // windowsConfiguration.xml (a restored terminal would hold a dead shell).
        assertEquals("", TerminalTabFactory.getInstance().getApplication());
    }

    @Test
    public void classNameAndPackageReportTheFactoryCoordinates() {
        TerminalTabFactory f = TerminalTabFactory.getInstance();
        assertEquals("org.scilab.modules.terminal.TerminalTabFactory", f.getClassName());
        assertEquals("", f.getPackage());
    }
}
