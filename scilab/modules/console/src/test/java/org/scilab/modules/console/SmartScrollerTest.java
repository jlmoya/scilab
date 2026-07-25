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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link SmartScroller}.
 *
 * <p>Two things are exercised. First the constructor: it validates its {@code
 * scrollDirection}/{@code viewportPosition} arguments and registers itself as an
 * {@link AdjustmentListener} on the chosen scrollbar. Second — and this is the
 * bulk of the class — the private {@code checkScrollBar} repositioning algorithm,
 * driven directly with a crafted {@link AdjustmentEvent} whose source is a real
 * {@link JScrollBar} with a fully controlled {@code BoundedRangeModel}. That
 * method reads {@code e.getSource()} (not the stored scrollbar) plus the
 * instance's {@code previousValue}/{@code previousMaximum}/{@code
 * adjustScrollBar} bookkeeping, so every branch of the "keep the viewport pinned
 * to the top/bottom" logic can be reproduced deterministically off the EDT.
 *
 * <p>Swing widgets are only constructed, never shown, which is headless-safe.
 */
public class SmartScrollerTest {

    // --- constants ----------------------------------------------------------

    @Test
    public void directionAndPositionConstantsHaveTheirDocumentedValues() {
        assertEquals(0, SmartScroller.HORIZONTAL);
        assertEquals(1, SmartScroller.VERTICAL);
        assertEquals(0, SmartScroller.START);
        assertEquals(1, SmartScroller.END);
        // Characterization: the direction and position namespaces share the 0/1 values.
        assertEquals(SmartScroller.HORIZONTAL, SmartScroller.START);
        assertEquals(SmartScroller.VERTICAL, SmartScroller.END);
    }

    // --- constructor --------------------------------------------------------

    @Test
    public void invalidScrollDirectionThrows() {
        JScrollPane pane = new JScrollPane();
        assertThrows(IllegalArgumentException.class,
            () -> new SmartScroller(pane, 99, SmartScroller.END));
    }

    @Test
    public void invalidViewportPositionThrows() {
        JScrollPane pane = new JScrollPane();
        assertThrows(IllegalArgumentException.class,
            () -> new SmartScroller(pane, SmartScroller.VERTICAL, 99));
    }

    @Test
    public void verticalConstructionRegistersListenerOnTheVerticalScrollBar() {
        JScrollPane pane = new JScrollPane(new JTextArea("some\ntext"));
        int before = pane.getVerticalScrollBar().getAdjustmentListeners().length;
        SmartScroller scroller = new SmartScroller(pane, SmartScroller.VERTICAL, SmartScroller.END);
        assertNotNull(scroller);
        AdjustmentListener[] after = pane.getVerticalScrollBar().getAdjustmentListeners();
        assertEquals(before + 1, after.length);
        assertTrue(containsSame(after, scroller));
    }

    @Test
    public void horizontalConstructionRegistersListenerOnTheHorizontalScrollBar() {
        JScrollPane pane = new JScrollPane(new JTextArea());
        SmartScroller scroller = new SmartScroller(pane, SmartScroller.HORIZONTAL, SmartScroller.START);
        assertTrue(containsSame(pane.getHorizontalScrollBar().getAdjustmentListeners(), scroller));
        // The vertical bar must be left untouched by a horizontal scroller.
        assertEquals(0, pane.getVerticalScrollBar().getAdjustmentListeners().length);
    }

    @Test
    public void convenienceConstructorDefaultsToVerticalEnd() {
        JScrollPane pane = new JScrollPane(new JTextArea());
        SmartScroller scroller = new SmartScroller(pane);
        assertTrue(containsSame(pane.getVerticalScrollBar().getAdjustmentListeners(), scroller));
    }

    @Test
    public void twoArgConvenienceConstructorUsesVerticalDirection() {
        JScrollPane pane = new JScrollPane(new JTextArea());
        SmartScroller scroller = new SmartScroller(pane, SmartScroller.START);
        assertTrue(containsSame(pane.getVerticalScrollBar().getAdjustmentListeners(), scroller));
    }

    @Test
    public void constructsCleanlyWhenTheViewportViewIsNotATextComponent() {
        // The "turn off automatic scrolling" branch only applies to JTextComponents;
        // a non-text view must construct without error.
        JScrollPane pane = new JScrollPane(new JPanel());
        SmartScroller scroller = new SmartScroller(pane, SmartScroller.VERTICAL, SmartScroller.END);
        assertNotNull(scroller);
    }

    // --- checkScrollBar: END (pin to bottom) --------------------------------

    @Test
    public void endScrollerPinsTheViewportToTheBottomOnTheFirstEvent() throws Exception {
        // First event ever: previousValue/previousMaximum are still -1 so the
        // "user repositioned" decision is skipped and the default adjustScrollBar
        // (true) drives a jump to maximum-extent.
        SmartScroller scroller = endScroller();
        JScrollBar bar = barWith(40, 20, 0, 100);
        invokeCheck(scroller, event(bar));

        assertEquals(80, bar.getValue(), "should snap to maximum-extent = 100-20");
        assertEquals(80, intField(scroller, "previousValue"));
        assertEquals(100, intField(scroller, "previousMaximum"));
        assertTrue(boolField(scroller, "adjustScrollBar"));
    }

    @Test
    public void endScrollerReEnablesAutoScrollWhenTheUserIsBackAtTheBottom() throws Exception {
        // The user had turned auto-scroll off; now they scrolled to the very
        // bottom (value+extent == maximum) with the maximum unchanged, which must
        // flip auto-scroll back on and re-pin to the bottom.
        SmartScroller scroller = endScroller();
        setField(scroller, "adjustScrollBar", false);
        setField(scroller, "previousValue", 30);
        setField(scroller, "previousMaximum", 100);

        JScrollBar bar = barWith(80, 20, 0, 100); // at the bottom
        invokeCheck(scroller, event(bar));

        assertTrue(boolField(scroller, "adjustScrollBar"));
        assertEquals(80, bar.getValue());
    }

    @Test
    public void endScrollerDisablesAutoScrollWhenTheUserScrollsAwayFromTheBottom() throws Exception {
        // A value change with an unchanged maximum where the viewport is NOT at
        // the bottom is read as a deliberate user scroll: auto-scroll turns off
        // and the viewport is left exactly where the user put it.
        SmartScroller scroller = endScroller();
        setField(scroller, "adjustScrollBar", true);
        setField(scroller, "previousValue", 80);
        setField(scroller, "previousMaximum", 100);

        JScrollBar bar = barWith(10, 20, 0, 100); // 10+20 = 30 < 100 => not at bottom
        invokeCheck(scroller, event(bar));

        assertFalse(boolField(scroller, "adjustScrollBar"));
        assertEquals(10, bar.getValue(), "the viewport must stay where the user left it");
        assertEquals(10, intField(scroller, "previousValue"));
    }

    @Test
    public void endScrollerLeavesEverythingAloneWhenNeitherValueNorMaximumChanged() throws Exception {
        // A pure no-op event (identical value and maximum) must not toggle
        // auto-scroll nor move the viewport.
        SmartScroller scroller = endScroller();
        setField(scroller, "adjustScrollBar", false);
        setField(scroller, "previousValue", 50);
        setField(scroller, "previousMaximum", 100);

        JScrollBar bar = barWith(50, 20, 0, 100);
        invokeCheck(scroller, event(bar));

        assertFalse(boolField(scroller, "adjustScrollBar"));
        assertEquals(50, bar.getValue());
        assertEquals(50, intField(scroller, "previousValue"));
        assertEquals(100, intField(scroller, "previousMaximum"));
    }

    // --- checkScrollBar: START (keep relative position) ---------------------

    @Test
    public void startScrollerKeepsTheRelativePositionWhenDataIsAddedAtTheTop() throws Exception {
        // Data added at the top grows the maximum (100 -> 150). With auto-scroll
        // on, the value is shifted by the maximum delta so the same content stays
        // under the viewport: 30 + (150 - 100) = 80.
        SmartScroller scroller = startScroller();
        setField(scroller, "adjustScrollBar", true);
        setField(scroller, "previousValue", 5);
        setField(scroller, "previousMaximum", 100);

        JScrollBar bar = barWith(30, 20, 0, 150);
        invokeCheck(scroller, event(bar));

        assertEquals(80, bar.getValue());
        assertEquals(80, intField(scroller, "previousValue"));
        assertEquals(150, intField(scroller, "previousMaximum"));
    }

    @Test
    public void startScrollerDisablesAutoScrollOnceTheViewportReachesTheTop() throws Exception {
        // For a START scroller a manual move to value 0 (the top) with the
        // maximum unchanged turns auto-scroll off.
        SmartScroller scroller = startScroller();
        setField(scroller, "adjustScrollBar", true);
        setField(scroller, "previousValue", 7);
        setField(scroller, "previousMaximum", 100);

        JScrollBar bar = barWith(0, 20, 0, 100);
        invokeCheck(scroller, event(bar));

        assertFalse(boolField(scroller, "adjustScrollBar"));
        assertEquals(0, bar.getValue());
    }

    @Test
    public void startScrollerKeepsAutoScrollOnWhenTheViewportIsNotAtTheTop() throws Exception {
        // A non-zero value with the maximum unchanged keeps auto-scroll on; since
        // the maximum did not move, the relative-position shift is a no-op.
        SmartScroller scroller = startScroller();
        setField(scroller, "adjustScrollBar", false);
        setField(scroller, "previousValue", 3);
        setField(scroller, "previousMaximum", 100);

        JScrollBar bar = barWith(9, 20, 0, 100);
        invokeCheck(scroller, event(bar));

        assertTrue(boolField(scroller, "adjustScrollBar"));
        assertEquals(9, bar.getValue());
        assertEquals(9, intField(scroller, "previousValue"));
    }

    // --- adjustmentValueChanged (the public EDT entry point) ----------------

    @Test
    public void adjustmentValueChangedSchedulesTheCheckOnTheEventDispatchThread() throws Exception {
        // The public listener hook defers to checkScrollBar via invokeLater; after
        // flushing the EDT the same first-event bottom-pin must have happened.
        SmartScroller scroller = new SmartScroller(new JScrollPane(new JTextArea()));
        JScrollBar bar = barWith(40, 20, 0, 100);

        scroller.adjustmentValueChanged(event(bar));
        SwingUtilities.invokeAndWait(() -> { }); // drain the EDT queue

        assertEquals(80, bar.getValue());
        assertEquals(80, intField(scroller, "previousValue"));
    }

    // --- helpers ------------------------------------------------------------

    private static SmartScroller endScroller() {
        return new SmartScroller(new JScrollPane(new JTextArea()), SmartScroller.VERTICAL, SmartScroller.END);
    }

    private static SmartScroller startScroller() {
        return new SmartScroller(new JScrollPane(new JTextArea()), SmartScroller.VERTICAL, SmartScroller.START);
    }

    /** A vertical scrollbar whose model is pinned to exactly the given range. */
    private static JScrollBar barWith(int value, int extent, int min, int max) {
        JScrollBar bar = new JScrollBar(JScrollBar.VERTICAL);
        bar.getModel().setRangeProperties(value, extent, min, max, false);
        return bar;
    }

    private static AdjustmentEvent event(JScrollBar bar) {
        return new AdjustmentEvent(bar, AdjustmentEvent.ADJUSTMENT_VALUE_CHANGED, AdjustmentEvent.TRACK, bar.getValue());
    }

    private static void invokeCheck(SmartScroller scroller, AdjustmentEvent e) throws Exception {
        Method m = SmartScroller.class.getDeclaredMethod("checkScrollBar", AdjustmentEvent.class);
        m.setAccessible(true);
        m.invoke(scroller, e);
    }

    private static void setField(SmartScroller scroller, String name, Object value) throws Exception {
        Field f = SmartScroller.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(scroller, value);
    }

    private static int intField(SmartScroller scroller, String name) throws Exception {
        Field f = SmartScroller.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.getInt(scroller);
    }

    private static boolean boolField(SmartScroller scroller, String name) throws Exception {
        Field f = SmartScroller.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.getBoolean(scroller);
    }

    private static boolean containsSame(AdjustmentListener[] listeners, Object target) {
        for (AdjustmentListener l : listeners) {
            if (l == target) {
                return true;
            }
        }
        return false;
    }
}
