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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.swing.JPanel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.artenum.rosetta.interfaces.ui.OutputView;

/**
 * Hermetic unit tests for {@link SciCommandLineView}. It is a plain
 * {@link JPanel} whose {@code setVisible} is repurposed to grow/shrink the
 * component's maximum size (rather than to toggle Swing visibility). Only
 * construction and the size/child-management bookkeeping are exercised; no
 * widget is ever shown, which is headless-safe.
 */
public class SciCommandLineViewTest {

    private SciCommandLineView view;

    @BeforeEach
    public void setUp() {
        view = new SciCommandLineView();
    }

    @Test
    public void constructorPinsTheMinimumSizeToTheSmallDimension() {
        assertEquals(OutputView.SMALL, view.getMinimumSize());
    }

    @Test
    public void theSmallAndBigReferenceDimensionsAreDistinct() {
        // The whole expand/shrink behaviour is only observable because the two
        // rosetta reference dimensions differ.
        assertNotEquals(OutputView.SMALL, OutputView.BIG);
    }

    @Test
    public void makingItVisibleExpandsTheMaximumSizeToBig() {
        view.setVisible(true);
        assertEquals(OutputView.BIG, view.getMaximumSize());
    }

    @Test
    public void makingItInvisibleShrinksTheMaximumSizeToSmall() {
        view.setVisible(true);
        view.setVisible(false);
        assertEquals(OutputView.SMALL, view.getMaximumSize());
    }

    @Test
    public void setVisibleDoesNotAffectTheActualSwingVisibilityFlag() {
        // Characterization: the override never calls super.setVisible, so the
        // component stays "visible" in the Swing sense regardless of the argument.
        assertTrue(view.isVisible());
        view.setVisible(false);
        assertTrue(view.isVisible());
        view.setVisible(true);
        assertTrue(view.isVisible());
    }

    @Test
    public void setInputCommandViewAddsTheComponentToTheContainer() {
        assertEquals(0, view.getComponentCount());
        JPanel input = new JPanel();
        view.setInputCommandView(input);
        assertEquals(1, view.getComponentCount());
        assertSameComponentPresent(view, input);
    }

    @Test
    public void setPromptViewAddsTheComponentToTheContainer() {
        JPanel prompt = new JPanel();
        view.setPromptView(prompt);
        assertEquals(1, view.getComponentCount());
        assertSameComponentPresent(view, prompt);
    }

    @Test
    public void bothPromptAndInputCanBeHostedSimultaneously() {
        view.setPromptView(new JPanel());
        view.setInputCommandView(new JPanel());
        assertEquals(2, view.getComponentCount());
    }

    private static void assertSameComponentPresent(SciCommandLineView container, java.awt.Component target) {
        boolean found = false;
        for (java.awt.Component c : container.getComponents()) {
            if (c == target) {
                found = true;
                break;
            }
        }
        assertTrue(found, "expected the added component to be a child of the view");
    }
}
