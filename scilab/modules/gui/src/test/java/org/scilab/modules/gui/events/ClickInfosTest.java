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

package org.scilab.modules.gui.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link ClickInfos}, the process-wide singleton that
 * carries the last mouse/menu click state (button number, x/y coordinates,
 * window id and menu callback).
 *
 * <p>{@code ClickInfos} is a lazily-created singleton with shared mutable
 * state, so every test starts from a clean slate by calling {@link
 * ClickInfos#init()} in {@link #resetSingleton()}. That makes the tests
 * independent of execution order and of any state left behind by production
 * code sharing the same JVM.
 */
public class ClickInfosTest {

    private ClickInfos infos;

    @BeforeEach
    public void resetSingleton() {
        infos = ClickInfos.getInstance();
        infos.init();
    }

    // --- singleton semantics ------------------------------------------------

    @Test
    public void getInstanceNeverReturnsNull() {
        assertNotNull(ClickInfos.getInstance());
    }

    @Test
    public void getInstanceAlwaysReturnsTheSameObject() {
        assertSame(ClickInfos.getInstance(), ClickInfos.getInstance());
    }

    // --- default state after init() ----------------------------------------

    @Test
    public void initEstablishesTheDocumentedDefaults() {
        assertEquals(0, infos.getMouseButtonNumber());
        assertEquals(0.0, infos.getXCoordinate(), 0.0);
        assertEquals(0.0, infos.getYCoordinate(), 0.0);
        assertEquals(Integer.valueOf(0), infos.getWindowID());
        assertEquals("void", infos.getMenuCallback());
    }

    // --- setter / getter round-trips ---------------------------------------

    @Test
    public void mouseButtonNumberRoundTrips() {
        infos.setMouseButtonNumber(3);
        assertEquals(3, infos.getMouseButtonNumber());
    }

    @Test
    public void xCoordinateRoundTripsIncludingNegativeAndFractionalValues() {
        infos.setXCoordinate(-12.5);
        assertEquals(-12.5, infos.getXCoordinate(), 0.0);
    }

    @Test
    public void yCoordinateRoundTripsIncludingNegativeAndFractionalValues() {
        infos.setYCoordinate(1024.75);
        assertEquals(1024.75, infos.getYCoordinate(), 0.0);
    }

    @Test
    public void windowIdRoundTrips() {
        infos.setWindowID(Integer.valueOf(17));
        assertEquals(Integer.valueOf(17), infos.getWindowID());
    }

    @Test
    public void windowIdAcceptsNull() {
        // The setter performs no validation; null is stored and returned as-is.
        infos.setWindowID(null);
        assertNull(infos.getWindowID());
    }

    @Test
    public void menuCallbackRoundTrips() {
        infos.setMenuCallback("myCallback()");
        assertEquals("myCallback()", infos.getMenuCallback());
    }

    @Test
    public void settersAreIndependentAndDoNotBleedIntoEachOther() {
        infos.setMouseButtonNumber(2);
        infos.setXCoordinate(3.0);
        infos.setYCoordinate(4.0);
        infos.setWindowID(Integer.valueOf(5));
        infos.setMenuCallback("cb");

        assertEquals(2, infos.getMouseButtonNumber());
        assertEquals(3.0, infos.getXCoordinate(), 0.0);
        assertEquals(4.0, infos.getYCoordinate(), 0.0);
        assertEquals(Integer.valueOf(5), infos.getWindowID());
        assertEquals("cb", infos.getMenuCallback());
    }

    // --- init() resets everything ------------------------------------------

    @Test
    public void initResetsAllMutatedFieldsBackToDefaults() {
        infos.setMouseButtonNumber(9);
        infos.setXCoordinate(99.0);
        infos.setYCoordinate(88.0);
        infos.setWindowID(Integer.valueOf(7));
        infos.setMenuCallback("dirty");

        infos.init();

        assertEquals(0, infos.getMouseButtonNumber());
        assertEquals(0.0, infos.getXCoordinate(), 0.0);
        assertEquals(0.0, infos.getYCoordinate(), 0.0);
        assertEquals(Integer.valueOf(0), infos.getWindowID());
        assertEquals("void", infos.getMenuCallback());
    }

    @Test
    public void initIsIdempotent() {
        infos.init();
        infos.init();
        assertEquals(0, infos.getMouseButtonNumber());
        assertEquals("void", infos.getMenuCallback());
    }
}
