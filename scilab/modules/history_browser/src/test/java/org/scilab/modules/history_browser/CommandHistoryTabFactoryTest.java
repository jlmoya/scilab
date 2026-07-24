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

package org.scilab.modules.history_browser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.scilab.modules.gui.tabfactory.AbstractScilabTabFactory;

/**
 * Hermetic unit tests for {@link CommandHistoryTabFactory}.
 *
 * The factory's identity metadata (application / package / class name), its
 * UUID predicate and its (idiosyncratic) singleton accessor are all pure Java
 * and touch neither a live Scilab engine nor a display. {@code getTab(String)}
 * is the only method that reaches into the Swing tab machinery, so it is never
 * invoked here.
 */
class CommandHistoryTabFactoryTest {

    /**
     * The command-history tab UUID, duplicated here as a literal on purpose so
     * the test never has to load the Swing-heavy {@code CommandHistory} class
     * (whose static initializer registers a tab factory) merely to read the
     * {@code COMMANDHISTORYUUID} constant.
     */
    private static final String HISTORY_UUID = "856207f6-0a60-47a0-b9f4-232feedd4bf4";

    @Test
    void constantsHaveTheExpectedValues() {
        assertEquals("CommandHistory", CommandHistoryTabFactory.APPLICATION);
        assertEquals("", CommandHistoryTabFactory.PACKAGE);
        assertEquals("org.scilab.modules.history_browser.CommandHistoryTabFactory",
                     CommandHistoryTabFactory.CLASS);
    }

    @Test
    void classConstantNamesTheActualClass() {
        // The CLASS constant is used reflectively by the tab-restoration
        // machinery, so it must stay in sync with the real class name.
        assertEquals(CommandHistoryTabFactory.class.getName(), CommandHistoryTabFactory.CLASS);
    }

    @Test
    void gettersReturnTheConstants() {
        CommandHistoryTabFactory f = CommandHistoryTabFactory.getInstance();
        assertEquals(CommandHistoryTabFactory.APPLICATION, f.getApplication());
        assertEquals(CommandHistoryTabFactory.PACKAGE, f.getPackage());
        assertEquals(CommandHistoryTabFactory.CLASS, f.getClassName());
    }

    @Test
    void validUuidIsAccepted() {
        assertTrue(CommandHistoryTabFactory.getInstance().isAValidUUID(HISTORY_UUID));
    }

    @Test
    void otherUuidsAreRejected() {
        CommandHistoryTabFactory f = CommandHistoryTabFactory.getInstance();
        assertFalse(f.isAValidUUID("00000000-0000-0000-0000-000000000000"));
        assertFalse(f.isAValidUUID(""));
        // Matching is a plain String.equals, i.e. case-sensitive.
        assertFalse(f.isAValidUUID(HISTORY_UUID.toUpperCase()));
    }

    @Test
    void nullUuidIsRejectedWithoutThrowing() {
        // isAValidUUID delegates to CONSTANT.equals(uuid), which is null-safe.
        assertFalse(CommandHistoryTabFactory.getInstance().isAValidUUID(null));
    }

    @Test
    void getInstanceIsNonNullAndStable() {
        CommandHistoryTabFactory a = CommandHistoryTabFactory.getInstance();
        CommandHistoryTabFactory b = CommandHistoryTabFactory.getInstance();
        assertNotNull(a);
        assertSame(a, b, "getInstance() must always return the one cached instance");
    }

    @Test
    void getInstanceIsAnAbstractScilabTabFactory() {
        assertTrue(CommandHistoryTabFactory.getInstance() instanceof AbstractScilabTabFactory,
                   "the factory must extend the generic tab-factory contract");
    }

    @Test
    void freshlyConstructedFactoryIsNotTheSingleton() {
        // Defect/quirk characterization: getInstance() constructs a throwaway
        // object on every call yet returns the ORIGINAL cached instance (set by
        // whichever construction happened first). So a directly-constructed
        // factory is never the one getInstance() hands back, and constructing
        // it must not disturb the cached instance.
        CommandHistoryTabFactory cached = CommandHistoryTabFactory.getInstance();
        CommandHistoryTabFactory fresh = new CommandHistoryTabFactory();
        assertNotSame(cached, fresh);
        assertSame(cached, CommandHistoryTabFactory.getInstance(),
                   "constructing a new factory must not replace the cached instance");
    }
}
