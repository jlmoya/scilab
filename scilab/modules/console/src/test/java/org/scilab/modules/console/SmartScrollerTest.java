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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.event.AdjustmentListener;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link SmartScroller}. Only the constructor is
 * exercised: it validates its {@code scrollDirection}/{@code viewportPosition}
 * arguments and registers itself as an {@link AdjustmentListener} on the chosen
 * scrollbar. Swing widgets are only constructed (never shown), which is
 * headless-safe; {@code checkScrollBar} runs on the EDT via {@code invokeLater}
 * and is out of scope here.
 */
public class SmartScrollerTest {

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

    private static boolean containsSame(AdjustmentListener[] listeners, Object target) {
        for (AdjustmentListener l : listeners) {
            if (l == target) {
                return true;
            }
        }
        return false;
    }
}
