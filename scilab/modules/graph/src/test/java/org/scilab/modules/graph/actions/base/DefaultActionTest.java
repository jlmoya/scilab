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

package org.scilab.modules.graph.actions.base;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import javax.swing.Action;
import javax.swing.KeyStroke;

import org.junit.jupiter.api.Test;
import org.scilab.modules.graph.ScilabGraph;

/**
 * Hermetic unit tests for {@link DefaultAction}, the abstract base of every
 * graph menu/toolbar action.
 *
 * The class is exercised through tiny concrete subclasses built with a
 * {@code null} graph, so nothing here constructs a Swing {@link ScilabGraph}.
 * The reflection-driven {@code installProperties()} (which copies the static
 * {@code NAME} / {@code MNEMONIC_KEY} / {@code ACCELERATOR_KEY} fields into the
 * Swing {@link Action} value map) is the main behaviour under test.
 */
public class DefaultActionTest {

    /** No mnemonic and an empty icon name => a bare NAME-only action. */
    public static class SimpleAction extends DefaultAction {
        public static final String NAME = "Simple";
        public static final String SMALL_ICON = "";
        public static final int MNEMONIC_KEY = 0;
        public static final int ACCELERATOR_KEY = 0;

        public SimpleAction(ScilabGraph graph) {
            super(graph);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            // no-op
        }
    }

    /** A real shortcut (VK_C + CTRL mask) drives the mnemonic/accelerator path. */
    public static class MnemonicAction extends DefaultAction {
        public static final String NAME = "Mnemonic";
        public static final String SMALL_ICON = "";
        public static final int MNEMONIC_KEY = KeyEvent.VK_C;
        public static final int ACCELERATOR_KEY = InputEvent.CTRL_DOWN_MASK;

        public MnemonicAction(ScilabGraph graph) {
            super(graph);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            // no-op
        }
    }

    /** Uses the deprecated (label, graph) constructor which skips installProperties. */
    public static class DeprecatedCtorAction extends DefaultAction {
        public DeprecatedCtorAction() {
            super("deprecated-label", (ScilabGraph) null);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            // no-op
        }
    }

    private static boolean assertionsEnabled() {
        boolean enabled = false;
        assert enabled = true;
        return enabled;
    }

    @Test
    public void installPropertiesCopiesNameIntoTheDescriptionValues() {
        SimpleAction action = new SimpleAction(null);
        assertEquals("Simple", action.getValue(Action.NAME));
        assertEquals("Simple", action.getValue(Action.SHORT_DESCRIPTION));
        assertEquals("Simple", action.getValue(Action.LONG_DESCRIPTION));
    }

    @Test
    public void emptyIconNameLeavesTheSmallIconUnset() {
        SimpleAction action = new SimpleAction(null);
        assertNull(action.getValue(Action.SMALL_ICON));
    }

    @Test
    public void zeroMnemonicLeavesTheAcceleratorUnset() {
        // When MNEMONIC_KEY == 0 the constructor installs neither the mnemonic
        // nor the accelerator key stroke.
        SimpleAction action = new SimpleAction(null);
        assertNull(action.getValue(Action.MNEMONIC_KEY));
        assertNull(action.getValue(Action.ACCELERATOR_KEY));
    }

    @Test
    public void nonZeroMnemonicInstallsMnemonicAndAcceleratorKeyStroke() {
        MnemonicAction action = new MnemonicAction(null);
        assertEquals(Integer.valueOf(KeyEvent.VK_C), action.getValue(Action.MNEMONIC_KEY));

        KeyStroke expected = KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK);
        assertEquals(expected, action.getValue(Action.ACCELERATOR_KEY));
    }

    @Test
    public void deprecatedConstructorDoesNotInstallProperties() {
        // The (label, graph) constructor stores the callback label but never
        // calls installProperties, so the Swing NAME value stays unset.
        DeprecatedCtorAction action = new DeprecatedCtorAction();
        assertNull(action.getValue(Action.NAME));
    }

    @Test
    public void getGraphReturnsNullWhenWeakGraphIsNullAndEventIsNull() {
        SimpleAction action = new SimpleAction(null);
        assertNull(action.getGraph(null));
    }

    @Test
    public void getGraphReturnsNullWhenEventSourceIsNotAComponent() {
        SimpleAction action = new SimpleAction(null);
        ActionEvent event = new ActionEvent(new Object(), ActionEvent.ACTION_PERFORMED, "cmd");
        assertNull(action.getGraph(event));
    }

    @Test
    public void callBackTripsItsGuardAssertionWhenAssertionsAreEnabled() {
        SimpleAction action = new SimpleAction(null);
        if (assertionsEnabled()) {
            // callBack() must never be reached in production; its body asserts false.
            assertThrows(AssertionError.class, action::callBack);
        } else {
            assertDoesNotThrow(action::callBack);
        }
    }
}
