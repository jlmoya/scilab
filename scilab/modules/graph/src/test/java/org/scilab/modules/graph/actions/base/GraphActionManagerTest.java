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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import javax.swing.Action;
import javax.swing.KeyStroke;

import org.junit.jupiter.api.Test;
import org.scilab.modules.graph.ScilabGraph;

/**
 * Hermetic unit tests for {@link GraphActionManager}, the per-graph action
 * registry.
 *
 * All behaviour is exercised against the {@code null} graph (the "out of
 * graphs" action set), which is the one code path that does not require
 * constructing a Swing {@link ScilabGraph}. Each test uses its own dedicated
 * action class so the static {@code WeakHashMap} registries never leak state
 * between tests regardless of execution order.
 */
public class GraphActionManagerTest {

    /** Minimal concrete action with the reflection-required static fields. */
    public static class AlphaAction extends DefaultAction {
        public static final String NAME = "Alpha";
        public static final String SMALL_ICON = "";
        public static final int MNEMONIC_KEY = 0;
        public static final int ACCELERATOR_KEY = 0;

        public AlphaAction(ScilabGraph graph) {
            super(graph);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            // no-op
        }
    }

    /** A second, distinct action class (same shape as {@link AlphaAction}). */
    public static class BetaAction extends DefaultAction {
        public static final String NAME = "Beta";
        public static final String SMALL_ICON = "";
        public static final int MNEMONIC_KEY = 0;
        public static final int ACCELERATOR_KEY = 0;

        public BetaAction(ScilabGraph graph) {
            super(graph);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            // no-op
        }
    }

    /** Dedicated class for the enable/disable propagation test. */
    public static class EnableAction extends DefaultAction {
        public static final String NAME = "Enable";
        public static final String SMALL_ICON = "";
        public static final int MNEMONIC_KEY = 0;
        public static final int ACCELERATOR_KEY = 0;

        public EnableAction(ScilabGraph graph) {
            super(graph);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            // no-op
        }
    }

    /** Dedicated class for the key-stroke update test. */
    public static class KeyStrokeAction extends DefaultAction {
        public static final String NAME = "KeyStroke";
        public static final String SMALL_ICON = "";
        public static final int MNEMONIC_KEY = 0;
        public static final int ACCELERATOR_KEY = 0;

        public KeyStrokeAction(ScilabGraph graph) {
            super(graph);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            // no-op
        }
    }

    /** Never instantiated: used to prove getEnable() is vacuously true. */
    public static class NeverInstantiatedAction extends DefaultAction {
        public static final String NAME = "Never";
        public static final String SMALL_ICON = "";
        public static final int MNEMONIC_KEY = 0;
        public static final int ACCELERATOR_KEY = 0;

        public NeverInstantiatedAction(ScilabGraph graph) {
            super(graph);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            // no-op
        }
    }

    /**
     * Has no {@code (ScilabGraph)} constructor, so
     * {@link GraphActionManager#getInstance} cannot reflectively build it.
     */
    public static class NoGraphConstructorAction extends DefaultAction {
        public static final String NAME = "NoGraphCtor";
        public static final String SMALL_ICON = "";
        public static final int MNEMONIC_KEY = 0;
        public static final int ACCELERATOR_KEY = 0;

        public NoGraphConstructorAction() {
            super((ScilabGraph) null);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            // no-op
        }
    }

    @Test
    public void getReturnsNullBeforeAnyInstantiation() {
        // BetaAction has never been asked for, so the registry has no instance.
        assertNull(GraphActionManager.get(null, BetaAction.class));
    }

    @Test
    public void getInstanceCreatesThenCachesTheSameInstance() {
        AlphaAction first = GraphActionManager.getInstance(null, AlphaAction.class);
        assertNotNull(first);

        // A second getInstance must return the very same cached object.
        AlphaAction second = GraphActionManager.getInstance(null, AlphaAction.class);
        assertSame(first, second);

        // And get() must now find it too.
        assertSame(first, GraphActionManager.get(null, AlphaAction.class));
    }

    @Test
    public void distinctActionClassesGetDistinctInstances() {
        DefaultAction enable = GraphActionManager.getInstance(null, EnableAction.class);
        DefaultAction ks = GraphActionManager.getInstance(null, KeyStrokeAction.class);

        assertNotNull(enable);
        assertNotNull(ks);
        assertNotSame(enable, ks);
        assertTrue(enable instanceof EnableAction);
        assertTrue(ks instanceof KeyStrokeAction);
    }

    @Test
    public void setEnableTogglesRegisteredActionsAndGetEnableReflectsIt() {
        EnableAction action = GraphActionManager.getInstance(null, EnableAction.class);
        // AbstractAction actions are enabled by default.
        assertTrue(action.isEnabled());
        assertTrue(GraphActionManager.getEnable(EnableAction.class));

        GraphActionManager.setEnable(EnableAction.class, false);
        assertFalse(action.isEnabled());
        assertFalse(GraphActionManager.getEnable(EnableAction.class));

        GraphActionManager.setEnable(EnableAction.class, true);
        assertTrue(action.isEnabled());
        assertTrue(GraphActionManager.getEnable(EnableAction.class));
    }

    @Test
    public void getEnableIsVacuouslyTrueForAClassWithNoInstances() {
        // No NeverInstantiatedAction has ever been created, so the AND-fold over
        // an empty match set yields the identity element, true.
        assertTrue(GraphActionManager.getEnable(NeverInstantiatedAction.class));
    }

    @Test
    public void updateActionKeyStrokeInstallsTheAcceleratorOnEachInstance() {
        KeyStrokeAction action = GraphActionManager.getInstance(null, KeyStrokeAction.class);
        assertNull(action.getValue(Action.ACCELERATOR_KEY));

        KeyStroke stroke = KeyStroke.getKeyStroke(KeyEvent.VK_K, 0);
        GraphActionManager.updateActionKeyStroke(KeyStrokeAction.class, stroke);

        assertSame(stroke, action.getValue(Action.ACCELERATOR_KEY));
    }

    @Test
    public void getInstanceReturnsNullWhenNoGraphConstructorExists() {
        // The reflective lookup of a (ScilabGraph) constructor fails with
        // NoSuchMethodException; getInstance swallows it and returns null.
        // (A stack trace is printed to stderr by design - this is the error path.)
        assertNull(GraphActionManager.getInstance(null, NoGraphConstructorAction.class));

        // Nothing was cached, so a later lookup is still empty.
        assertNull(GraphActionManager.get(null, NoGraphConstructorAction.class));
    }
}
