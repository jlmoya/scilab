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

package org.scilab.modules.completion;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.awt.Component;

import com.artenum.rosetta.interfaces.core.CompletionItem;

/**
 * Hermetic tests for {@link SciCompletionItemListCellRenderer}.
 *
 * <p>The renderer extends {@code com.artenum.rosetta.ui.CompletionItemListCellRenderer}
 * (a {@link javax.swing.JLabel}). Its {@code getListCellRendererComponent}
 * override is pure property assignment — it sets the label text to the item's
 * method profile and picks foreground/background colours from the selection
 * flag, then returns {@code this}. No display is realised, so a headless JVM
 * renders exactly the same values. The {@code JList} argument is unused by the
 * override and is passed as {@code null} throughout.</p>
 */
public class SciCompletionItemListCellRendererTest {

    /** The blue the Scilab override uses for the selected row. */
    private static final Color SELECTED_BLUE = new Color(0, 120, 214);

    private static final class FakeItem implements CompletionItem {
        private String methodProfile;

        FakeItem(String methodProfile) {
            this.methodProfile = methodProfile;
        }

        @Override public String getType() {
            return "Function";
        }
        @Override public String getMethodProfile() {
            return methodProfile;
        }
        @Override public String getReturnValue() {
            return methodProfile;
        }
        @Override public String getHelp() {
            return "";
        }
        @Override public void setType(String t) {
        }
        @Override public void setMethodProfile(String m) {
            this.methodProfile = m;
        }
        @Override public void setReturnValue(String r) {
        }
        @Override public void setHelp(String h) {
        }
        @Override public int compareTo(CompletionItem o) {
            return this.methodProfile.compareTo(o.getMethodProfile());
        }
    }

    @Test
    void rendersTheItemMethodProfileAsLabelText() {
        SciCompletionItemListCellRenderer r = new SciCompletionItemListCellRenderer();
        r.getListCellRendererComponent(null, new FakeItem("plot(x, y)"), 0, false, false);
        assertEquals("plot(x, y)", r.getText());
    }

    @Test
    void returnsTheRendererItself() {
        SciCompletionItemListCellRenderer r = new SciCompletionItemListCellRenderer();
        Component c = r.getListCellRendererComponent(null, new FakeItem("x"), 0, false, false);
        assertSame(r, c);
    }

    @Test
    void selectedRowUsesBlueBackgroundAndWhiteForeground() {
        SciCompletionItemListCellRenderer r = new SciCompletionItemListCellRenderer();
        r.getListCellRendererComponent(null, new FakeItem("f"), 0, true, false);
        assertEquals(SELECTED_BLUE, r.getBackground());
        assertEquals(Color.white, r.getForeground());
    }

    @Test
    void unselectedRowUsesWhiteBackgroundAndBlackForeground() {
        SciCompletionItemListCellRenderer r = new SciCompletionItemListCellRenderer();
        r.getListCellRendererComponent(null, new FakeItem("f"), 0, false, false);
        assertEquals(Color.white, r.getBackground());
        assertEquals(Color.black, r.getForeground());
    }

    @Test
    void selectionColourOverridesTheParentRosettaRed() {
        // The parent CompletionItemListCellRenderer paints the selected row
        // Color.red; the Scilab override replaces it with the blue above.
        SciCompletionItemListCellRenderer r = new SciCompletionItemListCellRenderer();
        r.getListCellRendererComponent(null, new FakeItem("f"), 0, true, false);
        assertNotEquals(Color.red, r.getBackground());
        assertEquals(SELECTED_BLUE, r.getBackground());
    }

    @Test
    void stylingIsIndependentOfIndexAndListArguments() {
        SciCompletionItemListCellRenderer r = new SciCompletionItemListCellRenderer();

        r.getListCellRendererComponent(null, new FakeItem("f"), 0, true, false);
        Color bg0 = r.getBackground();
        Color fg0 = r.getForeground();

        r.getListCellRendererComponent(null, new FakeItem("f"), 999, true, false);
        assertEquals(bg0, r.getBackground());
        assertEquals(fg0, r.getForeground());
    }

    @Test
    void cellHasFocusFlagIsIgnored() {
        SciCompletionItemListCellRenderer r = new SciCompletionItemListCellRenderer();

        r.getListCellRendererComponent(null, new FakeItem("f"), 0, false, false);
        Color bgNoFocus = r.getBackground();
        Color fgNoFocus = r.getForeground();

        r.getListCellRendererComponent(null, new FakeItem("f"), 0, false, true);
        assertEquals(bgNoFocus, r.getBackground());
        assertEquals(fgNoFocus, r.getForeground());
    }

    @Test
    void reuseAcrossRowsRefreshesTextAndColours() {
        // A single renderer instance is reused for every row; each call must
        // fully re-stamp text + colours from the new arguments.
        SciCompletionItemListCellRenderer r = new SciCompletionItemListCellRenderer();

        r.getListCellRendererComponent(null, new FakeItem("selectedRow"), 0, true, false);
        assertEquals("selectedRow", r.getText());
        assertEquals(SELECTED_BLUE, r.getBackground());

        r.getListCellRendererComponent(null, new FakeItem("plainRow"), 1, false, false);
        assertEquals("plainRow", r.getText());
        assertEquals(Color.white, r.getBackground());
        assertEquals(Color.black, r.getForeground());
    }

    @Test
    void nullValueThrowsNullPointerException() {
        SciCompletionItemListCellRenderer r = new SciCompletionItemListCellRenderer();
        // The cast of null succeeds; getMethodProfile() on null does not.
        assertThrows(NullPointerException.class,
                     () -> r.getListCellRendererComponent(null, null, 0, false, false));
    }

    @Test
    void nonCompletionItemValueThrowsClassCastException() {
        SciCompletionItemListCellRenderer r = new SciCompletionItemListCellRenderer();
        assertThrows(ClassCastException.class,
                     () -> r.getListCellRendererComponent(null, "not an item", 0, false, false));
    }

    @Test
    void nullMethodProfileYieldsNullText() {
        // Defect-characterisation: a CompletionItem whose profile is null is
        // passed straight to JLabel.setText(null); getText() then returns null.
        SciCompletionItemListCellRenderer r = new SciCompletionItemListCellRenderer();
        r.getListCellRendererComponent(null, new FakeItem(null), 0, false, false);
        assertNull(r.getText());
    }
}
