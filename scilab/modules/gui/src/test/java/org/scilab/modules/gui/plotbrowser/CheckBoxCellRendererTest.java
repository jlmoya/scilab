/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Scilab
 *
 * Hermetic JUnit 6 unit tests for the gui module.
 * This file is hereby licensed under the terms of the GNU GPL v2.0.
 */

package org.scilab.modules.gui.plotbrowser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Insets;

import javax.swing.JCheckBox;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;

import org.junit.jupiter.api.Test;

/**
 * Tests {@link CheckBoxCellRenderer}, the {@link ListCellRenderer} that draws each
 * list element as a {@link JCheckBox}.
 *
 * <p>The renderer does not create its own component: it casts the incoming {@code value}
 * to a {@link JCheckBox}, mutates it (background, foreground, enabled, font, focus/border
 * painting and border) from the owning {@link JList}'s state, and returns that same
 * instance. Every assertion here therefore inspects the checkbox that was passed in.</p>
 *
 * <p>Everything is pure Swing/AWT and touches no Scilab native code. Component creation,
 * {@code JList} colour/font defaults and {@link UIManager#getBorder} all work on a headless
 * JVM (the cross-platform Metal L&amp;F). The one look-and-feel-dependent value used here,
 * {@code "List.focusCellHighlightBorder"}, is read from {@link UIManager} at test time and
 * compared by reference; it is a cached, reference-stable {@code Border} under both the
 * headless (Metal) and the macOS (Aqua) look and feels, so the tests do not hardcode any
 * L&amp;F-specific value.</p>
 */
public class CheckBoxCellRendererTest {

    /**
     * A {@link JList} carrying four mutually-distinct colours plus a font, so that a
     * background/foreground/selection-background mix-up cannot pass unnoticed.
     */
    private static JList newList() {
        JList list = new JList();
        list.setBackground(new Color(1, 2, 3));
        list.setForeground(new Color(4, 5, 6));
        list.setSelectionBackground(new Color(7, 8, 9));
        list.setSelectionForeground(new Color(10, 11, 12));
        list.setFont(new Font("Dialog", Font.PLAIN, 11));
        list.setEnabled(true);
        return list;
    }

    // ------------------------------------------------------------------
    // Contract & identity
    // ------------------------------------------------------------------

    @Test
    public void implementsListCellRenderer() {
        assertInstanceOf(ListCellRenderer.class, new CheckBoxCellRenderer());
    }

    @Test
    public void returnsTheVeryCheckBoxItWasGiven() {
        // The renderer edits and returns the caller's component; it never builds a new one.
        CheckBoxCellRenderer renderer = new CheckBoxCellRenderer();
        JCheckBox checkbox = new JCheckBox("item");
        Component result = renderer.getListCellRendererComponent(newList(), checkbox, 0, false, false);
        assertSame(checkbox, result);
    }

    // ------------------------------------------------------------------
    // Colours
    // ------------------------------------------------------------------

    @Test
    public void unselectedUsesListBackgroundAndForeground() {
        CheckBoxCellRenderer renderer = new CheckBoxCellRenderer();
        JList list = newList();
        JCheckBox checkbox = new JCheckBox();

        renderer.getListCellRendererComponent(list, checkbox, 0, false, false);

        assertEquals(new Color(1, 2, 3), checkbox.getBackground());
        assertEquals(new Color(4, 5, 6), checkbox.getForeground());
    }

    @Test
    public void selectedUsesSelectionBackgroundAndOrangeForeground() {
        CheckBoxCellRenderer renderer = new CheckBoxCellRenderer();
        JList list = newList();
        JCheckBox checkbox = new JCheckBox();

        renderer.getListCellRendererComponent(list, checkbox, 0, true, false);

        assertEquals(new Color(7, 8, 9), checkbox.getBackground());
        assertEquals(Color.ORANGE, checkbox.getForeground());
    }

    /**
     * Defect-characterization: when a cell is selected the foreground is the hardcoded
     * {@link Color#ORANGE}, not the list's own {@code selectionForeground}. This documents
     * that {@code JList.getSelectionForeground()} is deliberately ignored.
     */
    @Test
    public void selectedForegroundIsHardcodedOrangeAndIgnoresListSelectionForeground() {
        CheckBoxCellRenderer renderer = new CheckBoxCellRenderer();
        JList list = newList();
        list.setSelectionForeground(Color.MAGENTA);
        JCheckBox checkbox = new JCheckBox();

        renderer.getListCellRendererComponent(list, checkbox, 0, true, false);

        assertEquals(Color.ORANGE, checkbox.getForeground());
        assertNotSame(Color.MAGENTA, checkbox.getForeground());
    }

    // ------------------------------------------------------------------
    // Enabled state, font, focus/border painting
    // ------------------------------------------------------------------

    @Test
    public void enabledStateIsCopiedFromTheList() {
        CheckBoxCellRenderer renderer = new CheckBoxCellRenderer();

        // A disabled list disables an otherwise-enabled checkbox...
        JList disabledList = newList();
        disabledList.setEnabled(false);
        JCheckBox cb1 = new JCheckBox();
        cb1.setEnabled(true);
        renderer.getListCellRendererComponent(disabledList, cb1, 0, false, false);
        assertFalse(cb1.isEnabled());

        // ...and an enabled list re-enables an otherwise-disabled checkbox.
        JList enabledList = newList();
        enabledList.setEnabled(true);
        JCheckBox cb2 = new JCheckBox();
        cb2.setEnabled(false);
        renderer.getListCellRendererComponent(enabledList, cb2, 0, false, false);
        assertTrue(cb2.isEnabled());
    }

    @Test
    public void fontIsCopiedFromTheList() {
        CheckBoxCellRenderer renderer = new CheckBoxCellRenderer();
        JList list = newList();
        Font listFont = new Font("Serif", Font.BOLD, 17);
        list.setFont(listFont);
        JCheckBox checkbox = new JCheckBox();

        renderer.getListCellRendererComponent(list, checkbox, 0, false, false);

        assertSame(listFont, checkbox.getFont());
    }

    @Test
    public void focusPaintingIsAlwaysTurnedOff() {
        CheckBoxCellRenderer renderer = new CheckBoxCellRenderer();
        JCheckBox checkbox = new JCheckBox();
        checkbox.setFocusPainted(true); // renderer must flip this to false

        renderer.getListCellRendererComponent(newList(), checkbox, 0, false, false);

        assertFalse(checkbox.isFocusPainted());
    }

    @Test
    public void borderPaintingIsAlwaysTurnedOn() {
        CheckBoxCellRenderer renderer = new CheckBoxCellRenderer();
        JCheckBox checkbox = new JCheckBox();
        checkbox.setBorderPainted(false); // renderer must flip this to true

        renderer.getListCellRendererComponent(newList(), checkbox, 0, false, false);

        assertTrue(checkbox.isBorderPainted());
    }

    // ------------------------------------------------------------------
    // Border selection
    // ------------------------------------------------------------------

    @Test
    public void unselectedBorderIsTheRenderersSharedNoFocusBorder() {
        CheckBoxCellRenderer renderer = new CheckBoxCellRenderer();
        JCheckBox checkbox = new JCheckBox();

        renderer.getListCellRendererComponent(newList(), checkbox, 0, false, false);

        // Same field instance is reused for every unselected cell.
        assertSame(renderer.noFocusBorder, checkbox.getBorder());
    }

    @Test
    public void noFocusBorderIsAnEmptyBorderOfUnitInsets() {
        CheckBoxCellRenderer renderer = new CheckBoxCellRenderer();
        assertInstanceOf(EmptyBorder.class, renderer.noFocusBorder);
        EmptyBorder border = (EmptyBorder) renderer.noFocusBorder;
        assertEquals(new Insets(1, 1, 1, 1), border.getBorderInsets());
    }

    @Test
    public void eachRendererOwnsItsOwnNoFocusBorderInstance() {
        // The border is a per-instance field, not a shared static.
        assertNotSame(new CheckBoxCellRenderer().noFocusBorder,
                      new CheckBoxCellRenderer().noFocusBorder);
    }

    @Test
    public void selectedBorderIsTheLookAndFeelFocusHighlightBorder() {
        CheckBoxCellRenderer renderer = new CheckBoxCellRenderer();
        JCheckBox checkbox = new JCheckBox();

        renderer.getListCellRendererComponent(newList(), checkbox, 0, true, false);

        Border expected = UIManager.getBorder("List.focusCellHighlightBorder");
        assertSame(expected, checkbox.getBorder());
    }

    @Test
    public void selectedAndUnselectedBordersDiffer() {
        CheckBoxCellRenderer renderer = new CheckBoxCellRenderer();

        JCheckBox selected = new JCheckBox();
        renderer.getListCellRendererComponent(newList(), selected, 0, true, false);

        JCheckBox unselected = new JCheckBox();
        renderer.getListCellRendererComponent(newList(), unselected, 0, false, false);

        assertNotSame(selected.getBorder(), unselected.getBorder());
        assertSame(renderer.noFocusBorder, unselected.getBorder());
    }

    /**
     * Defect-characterization: the border is chosen from {@code isSelected}, never from
     * {@code cellHasFocus}. A focused-but-unselected cell gets the plain no-focus border,
     * and a selected-but-unfocused cell gets the highlight border - the opposite of the
     * conventional "focus paints the highlight border" rule. This pins current behaviour.
     */
    @Test
    public void borderIsDrivenBySelectionNotByCellFocus() {
        CheckBoxCellRenderer renderer = new CheckBoxCellRenderer();
        Border highlight = UIManager.getBorder("List.focusCellHighlightBorder");

        // Focused but NOT selected -> no-focus border.
        JCheckBox focusedUnselected = new JCheckBox();
        renderer.getListCellRendererComponent(newList(), focusedUnselected, 0, false, true);
        assertSame(renderer.noFocusBorder, focusedUnselected.getBorder());

        // Selected but NOT focused -> highlight border.
        JCheckBox unfocusedSelected = new JCheckBox();
        renderer.getListCellRendererComponent(newList(), unfocusedSelected, 0, true, false);
        assertSame(highlight, unfocusedSelected.getBorder());
    }

    // ------------------------------------------------------------------
    // Ignored parameters & re-render behaviour
    // ------------------------------------------------------------------

    /**
     * Neither {@code index} nor {@code cellHasFocus} affects any produced property, so wild
     * index values and either focus flag yield an identical result.
     */
    @Test
    public void indexAndCellHasFocusDoNotAffectTheResult() {
        CheckBoxCellRenderer renderer = new CheckBoxCellRenderer();

        JCheckBox a = new JCheckBox();
        renderer.getListCellRendererComponent(newList(), a, -1, false, false);

        JCheckBox b = new JCheckBox();
        renderer.getListCellRendererComponent(newList(), b, 9999, false, true);

        assertEquals(a.getBackground(), b.getBackground());
        assertEquals(a.getForeground(), b.getForeground());
        assertEquals(a.isEnabled(), b.isEnabled());
        assertSame(a.getBorder(), b.getBorder()); // both the shared noFocusBorder
    }

    @Test
    public void reRenderingTheSameCheckBoxTracksTheCurrentSelectionState() {
        CheckBoxCellRenderer renderer = new CheckBoxCellRenderer();
        JList list = newList();
        JCheckBox checkbox = new JCheckBox();

        // First render selected...
        renderer.getListCellRendererComponent(list, checkbox, 0, true, false);
        assertEquals(new Color(7, 8, 9), checkbox.getBackground());
        assertEquals(Color.ORANGE, checkbox.getForeground());
        assertSame(UIManager.getBorder("List.focusCellHighlightBorder"), checkbox.getBorder());

        // ...then render the same instance unselected: every selected-state property reverts.
        renderer.getListCellRendererComponent(list, checkbox, 0, false, false);
        assertEquals(new Color(1, 2, 3), checkbox.getBackground());
        assertEquals(new Color(4, 5, 6), checkbox.getForeground());
        assertSame(renderer.noFocusBorder, checkbox.getBorder());
    }

    // ------------------------------------------------------------------
    // Error paths
    // ------------------------------------------------------------------

    @Test
    public void nonCheckBoxValueThrowsClassCastException() {
        CheckBoxCellRenderer renderer = new CheckBoxCellRenderer();
        // The very first statement casts value to JCheckBox.
        assertThrows(ClassCastException.class,
            () -> renderer.getListCellRendererComponent(newList(), "not a checkbox", 0, false, false));
    }

    @Test
    public void nullValueThrowsNullPointerException() {
        CheckBoxCellRenderer renderer = new CheckBoxCellRenderer();
        // (JCheckBox) null casts fine, but the first setter dereferences the null checkbox.
        assertThrows(NullPointerException.class,
            () -> renderer.getListCellRendererComponent(newList(), null, 0, false, false));
    }

    @Test
    public void nullListThrowsNullPointerException() {
        CheckBoxCellRenderer renderer = new CheckBoxCellRenderer();
        JCheckBox checkbox = new JCheckBox();

        // Unselected branch dereferences list.getBackground().
        assertThrows(NullPointerException.class,
            () -> renderer.getListCellRendererComponent(null, checkbox, 0, false, false));

        // Selected branch dereferences list.getSelectionBackground().
        assertThrows(NullPointerException.class,
            () -> renderer.getListCellRendererComponent(null, checkbox, 0, true, false));
    }
}
