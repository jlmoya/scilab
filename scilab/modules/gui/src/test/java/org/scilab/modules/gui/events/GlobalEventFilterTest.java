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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.awt.event.MouseEvent;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.scilab.modules.gui.utils.SciTranslator;

/**
 * Hermetic unit tests for {@link GlobalEventFilter}.
 *
 * <p>{@code GlobalEventFilter} is a static utility (its {@code protected}
 * constructor deliberately throws {@link UnsupportedOperationException}). Its
 * {@code filterKey} / {@code filterCallback} / {@code filterMouse} methods all
 * push state into the process-wide {@link ClickInfos} singleton under its
 * monitor and then {@code notify()} any waiter. These tests observe the effect
 * on {@code ClickInfos} rather than any return value (the methods are
 * {@code void}).
 *
 * <p>Each test starts from a clean slate via {@link ClickInfos#init()} so the
 * shared singleton cannot leak state between tests or from production code
 * sharing the same JVM.
 *
 * <p>{@code filterMouse} and both {@code filterCallback} overloads are fully
 * hermetic. {@code filterKey} is <em>not</em>: it resolves the pointer location
 * through {@link java.awt.MouseInfo#getPointerInfo()} and the source's
 * on-screen location, both of which require a live display. The tests here pin
 * that documented limitation instead of trying to fake a display.
 */
public class GlobalEventFilterTest {

    /** Mirror of the private GlobalEventFilter.SCILAB_CALLBACK sentinel. */
    private static final int SCILAB_CALLBACK = -2;

    /** Reusable lightweight, non-displayed component to source MouseEvents from. */
    private final Container source = new Container();

    @BeforeEach
    public void resetClickInfos() {
        ClickInfos.getInstance().init();
    }

    private MouseEvent mouseEventAt(int x, int y, int button) {
        return new MouseEvent(source, MouseEvent.MOUSE_PRESSED, 0L, 0, x, y, 1, false, button);
    }

    // --- constructor: static-utility guard ---------------------------------

    @Test
    public void constructorThrowsUnsupportedOperationException() {
        // Same-package access to the protected constructor: it must refuse
        // instantiation outright.
        assertThrows(UnsupportedOperationException.class, () -> new GlobalEventFilter());
    }

    @Test
    public void declaredNoArgConstructorIsProtected() throws Exception {
        Constructor<GlobalEventFilter> c = GlobalEventFilter.class.getDeclaredConstructor();
        assertTrue(Modifier.isProtected(c.getModifiers()),
                   "the sole constructor is documented as protected");
    }

    @Test
    public void reflectiveConstructionWrapsUnsupportedOperationException() throws Exception {
        Constructor<GlobalEventFilter> c = GlobalEventFilter.class.getDeclaredConstructor();
        c.setAccessible(true);
        InvocationTargetException ex = assertThrows(InvocationTargetException.class, c::newInstance);
        assertInstanceOf(UnsupportedOperationException.class, ex.getCause());
    }

    // --- filterCallback(String) --------------------------------------------

    @Test
    public void filterCallbackSetsTheCallbackSentinelState() {
        GlobalEventFilter.filterCallback("myMenu()");

        ClickInfos infos = ClickInfos.getInstance();
        assertEquals(SCILAB_CALLBACK, infos.getMouseButtonNumber());
        assertEquals("myMenu()", infos.getMenuCallback());
        assertEquals(Integer.valueOf(0), infos.getWindowID());
        assertEquals(-1.0, infos.getXCoordinate(), 0.0);
        assertEquals(-1.0, infos.getYCoordinate(), 0.0);
    }

    @Test
    public void filterCallbackOverwritesAnyPreExistingState() {
        // Dirty the singleton with unrelated values first...
        ClickInfos infos = ClickInfos.getInstance();
        infos.setMouseButtonNumber(999);
        infos.setWindowID(Integer.valueOf(42));
        infos.setXCoordinate(123.0);
        infos.setYCoordinate(456.0);
        infos.setMenuCallback("stale");

        GlobalEventFilter.filterCallback("fresh()");

        // ...and every field must be reset to the callback sentinel state,
        // notably windowID forced back to 0 and coordinates to -1.
        assertEquals(SCILAB_CALLBACK, infos.getMouseButtonNumber());
        assertEquals("fresh()", infos.getMenuCallback());
        assertEquals(Integer.valueOf(0), infos.getWindowID());
        assertEquals(-1.0, infos.getXCoordinate(), 0.0);
        assertEquals(-1.0, infos.getYCoordinate(), 0.0);
    }

    @Test
    public void filterCallbackAcceptsNullAndEmptyCommands() {
        // The underlying setter performs no validation.
        GlobalEventFilter.filterCallback((String) null);
        assertNull(ClickInfos.getInstance().getMenuCallback());

        GlobalEventFilter.filterCallback("");
        assertEquals("", ClickInfos.getInstance().getMenuCallback());
    }

    // --- filterCallback(String, int, Integer) ------------------------------

    @Test
    public void filterCallbackWithReturnCodeAndFigureSetsState() {
        GlobalEventFilter.filterCallback("close()", 7, Integer.valueOf(13));

        ClickInfos infos = ClickInfos.getInstance();
        // The return code becomes the mouse-button number verbatim (it is NOT
        // the SCILAB_CALLBACK sentinel used by the single-arg overload).
        assertEquals(7, infos.getMouseButtonNumber());
        assertEquals("close()", infos.getMenuCallback());
        assertEquals(Integer.valueOf(13), infos.getWindowID());
        assertEquals(-1.0, infos.getXCoordinate(), 0.0);
        assertEquals(-1.0, infos.getYCoordinate(), 0.0);
    }

    @Test
    public void filterCallbackWithReturnCodePreservesArbitraryReturnCodes() {
        GlobalEventFilter.filterCallback("cb", Integer.MIN_VALUE, Integer.valueOf(1));
        assertEquals(Integer.MIN_VALUE, ClickInfos.getInstance().getMouseButtonNumber());

        GlobalEventFilter.filterCallback("cb", Integer.MAX_VALUE, Integer.valueOf(1));
        assertEquals(Integer.MAX_VALUE, ClickInfos.getInstance().getMouseButtonNumber());
    }

    @Test
    public void filterCallbackWithReturnCodeAcceptsNullFigureUID() {
        GlobalEventFilter.filterCallback("cb", 0, null);
        assertNull(ClickInfos.getInstance().getWindowID());
    }

    // --- filterMouse: null axes = no-op ------------------------------------

    @Test
    public void filterMouseWithNullAxesLeavesClickInfosUntouched() {
        ClickInfos infos = ClickInfos.getInstance();
        GlobalEventFilter.filterMouse(mouseEventAt(5, 6, MouseEvent.BUTTON1), null, SciTranslator.CLICKED, false);

        // Still the pristine init() defaults: the method must have short-circuited.
        assertEquals(0, infos.getMouseButtonNumber());
        assertEquals(Integer.valueOf(0), infos.getWindowID());
        assertEquals(0.0, infos.getXCoordinate(), 0.0);
        assertEquals(0.0, infos.getYCoordinate(), 0.0);
        assertEquals("void", infos.getMenuCallback());
    }

    @Test
    public void filterMouseWithNullAxesToleratesANullEvent() {
        // With a null axesUID the event is never dereferenced, so even a null
        // MouseEvent must not raise (documents the guard order).
        assertDoesNotThrow(() -> GlobalEventFilter.filterMouse(null, null, 0, false));
    }

    // --- filterMouse: real axes -------------------------------------------

    @Test
    public void filterMouseWithAxesRecordsButtonWindowAndCoordinates() {
        // BUTTON1 (1) + PRESSED (-1) + no Ctrl = 0  (the documented example).
        GlobalEventFilter.filterMouse(mouseEventAt(10, 20, MouseEvent.BUTTON1),
                                      Integer.valueOf(42), SciTranslator.PRESSED, false);

        ClickInfos infos = ClickInfos.getInstance();
        assertEquals(0, infos.getMouseButtonNumber());
        assertEquals(Integer.valueOf(42), infos.getWindowID());
        assertEquals(10.0, infos.getXCoordinate(), 0.0);
        assertEquals(20.0, infos.getYCoordinate(), 0.0);
        // filterMouse never touches the menu callback.
        assertEquals("void", infos.getMenuCallback());
    }

    @Test
    public void filterMouseAddsTheCtrlOffsetWhenControlIsDown() {
        // Same click but with Ctrl held: 0 + 1000 = 1000.
        GlobalEventFilter.filterMouse(mouseEventAt(10, 20, MouseEvent.BUTTON1),
                                      Integer.valueOf(42), SciTranslator.PRESSED, true);
        assertEquals(1000, ClickInfos.getInstance().getMouseButtonNumber());
    }

    @Test
    public void filterMouseCoordinatesComeFromTheEventNotThePointer() {
        // Unlike filterKey, filterMouse reads x/y straight off the event, so it
        // works headlessly and reflects whatever the event carries.
        GlobalEventFilter.filterMouse(mouseEventAt(-3, 777, MouseEvent.BUTTON3),
                                      Integer.valueOf(1), SciTranslator.CLICKED, false);
        ClickInfos infos = ClickInfos.getInstance();
        assertEquals(-3.0, infos.getXCoordinate(), 0.0);
        assertEquals(777.0, infos.getYCoordinate(), 0.0);
        // right button (3) + CLICKED (2) = 5
        assertEquals(5, infos.getMouseButtonNumber());
    }

    // --- filterKey: display-bound ------------------------------------------

    @Test
    public void filterKeyThrowsHeadlessExceptionWithoutADisplay() {
        // The surefire config forces java.awt.headless=true, so MouseInfo
        // .getPointerInfo() cannot resolve and filterKey fails fast. Guarded so
        // the test is a no-op (skipped) on a rare windowed run.
        org.junit.jupiter.api.Assumptions.assumeTrue(GraphicsEnvironment.isHeadless(),
                "filterKey's HeadlessException path only applies in a headless JVM");
        assertThrows(HeadlessException.class,
            () -> GlobalEventFilter.filterKey(65, Integer.valueOf(7), false, source));
    }

    @Test
    public void filterKeyMutatesButtonAndWindowBeforeFailingOnCoordinates() {
        // Characterization of a non-atomic write: filterKey sets the button
        // number and window id BEFORE it tries (and fails) to resolve the
        // pointer/source location. This holds in both a headless JVM
        // (HeadlessException from MouseInfo) and a windowed one
        // (IllegalComponentStateException from the never-shown source) -- both
        // are RuntimeExceptions, and both strike after the first two writes.
        assertThrows(RuntimeException.class,
            () -> GlobalEventFilter.filterKey(65, Integer.valueOf(7), false, source));

        ClickInfos infos = ClickInfos.getInstance();
        assertEquals(SciTranslator.javaKey2Scilab(65, false), infos.getMouseButtonNumber());
        assertEquals(Integer.valueOf(7), infos.getWindowID());
        // The coordinate write never ran, so x/y are still the init() default.
        assertEquals(0.0, infos.getXCoordinate(), 0.0);
        assertEquals(0.0, infos.getYCoordinate(), 0.0);
    }

    @Test
    public void filterKeyControlModifierWouldShiftTheButtonCodeByTheCtrlOffset() {
        // The button-code write happens before the display-bound failure, so we
        // can still assert the Ctrl offset (1000) is applied to the key code.
        assertThrows(RuntimeException.class,
            () -> GlobalEventFilter.filterKey(65, Integer.valueOf(7), true, source));
        assertEquals(SciTranslator.javaKey2Scilab(65, true),
                     ClickInfos.getInstance().getMouseButtonNumber());
        assertEquals(65 + 1000, ClickInfos.getInstance().getMouseButtonNumber());
    }
}
