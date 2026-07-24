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

package org.scilab.modules.graphic_objects.console;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import org.scilab.modules.graphic_objects.console.Console.ScilabMode;
import org.scilab.modules.graphic_objects.graphicObject.GraphicObject.GraphicObjectPropertyType;
import org.scilab.modules.graphic_objects.graphicObject.GraphicObject.UpdateStatus;

import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_CONSOLE__;
import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_SHOWHIDDENHANDLES__;
import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_TOOLBAR_VISIBLE__;

/**
 * Hermetic unit tests for {@link Console}, the singleton graphic object backing
 * the Scilab console. Because it is a shared singleton, each test establishes
 * the state it needs before asserting, so the tests are order-independent.
 */
public class ConsoleTest {

    @Test
    public void getConsoleReturnsStableSingleton() {
        Console a = Console.getConsole();
        assertNotNull(a);
        assertSame(a, Console.getConsole());
    }

    @Test
    public void typeIsConsole() {
        assertEquals(Integer.valueOf(__GO_CONSOLE__), Console.getConsole().getType());
    }

    @Test
    public void scilabModeRoundTrips() {
        Console c = Console.getConsole();
        assertEquals(UpdateStatus.Success, c.setScilabMode(ScilabMode.NW));
        assertEquals(ScilabMode.NW, c.getScilabMode());
        assertEquals(UpdateStatus.Success, c.setScilabMode(ScilabMode.STD));
        assertEquals(ScilabMode.STD, c.getScilabMode());
    }

    @Test
    public void showHiddenHandlesHasNoChangeDetection() {
        Console c = Console.getConsole();
        assertEquals(UpdateStatus.Success, c.setShowHiddenHandles(true));
        assertTrue(c.getShowHiddenHandles());
        // No change detection: repeating the same value still reports Success.
        assertEquals(UpdateStatus.Success, c.setShowHiddenHandles(true));
        assertEquals(UpdateStatus.Success, c.setShowHiddenHandles(false));
        assertFalse(c.getShowHiddenHandles());
    }

    @Test
    public void showHiddenPropertiesHasNoChangeDetection() {
        Console c = Console.getConsole();
        assertEquals(UpdateStatus.Success, c.setShowHiddenProperties(true));
        assertTrue(c.getShowHiddenProperties());
        assertEquals(UpdateStatus.Success, c.setShowHiddenProperties(false));
        assertFalse(c.getShowHiddenProperties());
    }

    @Test
    public void useDeprecatedLFChangeDetection() {
        Console c = Console.getConsole();
        c.setUseDeprecatedLF(false); // known baseline
        assertEquals(UpdateStatus.Success, c.setUseDeprecatedLF(true));
        assertTrue(c.getUseDeprecatedLF());
        assertEquals(UpdateStatus.NoChange, c.setUseDeprecatedLF(true));
        c.setUseDeprecatedLF(false); // restore
    }

    @Test
    public void toolbarVisibleChangeDetection() {
        Console c = Console.getConsole();
        c.setToolbarVisible(false); // known baseline
        assertEquals(UpdateStatus.Success, c.setToolbarVisible(true));
        assertTrue(c.getToolbarVisible());
        assertEquals(UpdateStatus.NoChange, c.setToolbarVisible(true));
        c.setToolbarVisible(false); // restore
    }

    @Test
    public void fastPropertyDispatchRoundTripsThroughByNameKey() {
        Console c = Console.getConsole();
        // ConsoleProperty enum keys are package-private; obtain them by name.
        Object handlesKey = c.getPropertyFromName(__GO_SHOWHIDDENHANDLES__);
        assertNotNull(handlesKey);
        c.setProperty(handlesKey, true);
        assertEquals(Boolean.TRUE, c.getProperty(handlesKey));
        c.setProperty(handlesKey, false);
        assertEquals(Boolean.FALSE, c.getProperty(handlesKey));

        Object toolbarKey = c.getPropertyFromName(__GO_TOOLBAR_VISIBLE__);
        c.setProperty(toolbarKey, true);
        assertEquals(Boolean.TRUE, c.getProperty(toolbarKey));
        c.setProperty(toolbarKey, false);
    }

    @Test
    public void unknownPropertyDelegatesToSuper() {
        Console c = Console.getConsole();
        // TYPE is a base-class property, so dispatch falls through to super.
        assertEquals(Integer.valueOf(__GO_CONSOLE__),
                     c.getProperty(GraphicObjectPropertyType.TYPE));
    }

    @Test
    public void acceptIsANoOp() {
        // The Console visitor hook is intentionally empty; it must not throw.
        assertDoesNotThrow(() -> Console.getConsole().accept(null));
    }
}
