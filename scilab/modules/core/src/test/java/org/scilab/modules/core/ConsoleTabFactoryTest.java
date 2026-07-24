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

package org.scilab.modules.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link ConsoleTabFactory}.
 *
 * <p>This factory is the only class in the {@code core} module that can be exercised
 * without a running Scilab: its class initialisation, constructor, lazily-cached
 * singleton and the three descriptor getters ({@code getPackage}, {@code getClassName},
 * {@code getApplication}) touch nothing but {@code String} constants and its own
 * (side-effect-free) abstract superclass {@code AbstractScilabTabFactory}.
 *
 * <p>The two remaining public methods are deliberately NOT covered here because neither
 * is hermetic: {@code isAValidUUID(String)} dereferences
 * {@code org.scilab.modules.gui.SwingView.NULLUUID}, whose owning class runs
 * {@code static &#123; System.loadLibrary("gluegen_rt"); &#125;} at load time, and
 * {@code getTab(String)} builds the full Swing console tab. Both require native
 * libraries / a live GUI and belong to the {@code -Pnative-tests} path, not to a
 * hermetic unit test.
 */
public class ConsoleTabFactoryTest {

    /* ----------------------------------------------------------------------
     * Singleton contract: getInstance()
     * -------------------------------------------------------------------- */

    @Test
    public void getInstanceIsNeverNull() {
        assertNotNull(ConsoleTabFactory.getInstance());
    }

    @Test
    public void getInstanceCachesASingleInstance() {
        // Two calls must hand back the very same object reference (lazy cache).
        assertSame(ConsoleTabFactory.getInstance(), ConsoleTabFactory.getInstance());
    }

    @Test
    public void freshlyConstructedInstanceIsDistinctFromTheSingleton() {
        // The public no-arg constructor is real and independent of the cached singleton.
        ConsoleTabFactory fresh = new ConsoleTabFactory();
        assertNotSame(ConsoleTabFactory.getInstance(), fresh);
    }

    @Test
    public void isASubclassOfAbstractScilabTabFactory() {
        // Verified reflectively so the assertion does not itself pull in any gui type
        // beyond the already-loaded superclass Class object.
        assertEquals("org.scilab.modules.gui.tabfactory.AbstractScilabTabFactory",
                     ConsoleTabFactory.getInstance().getClass().getSuperclass().getName());
    }

    /* ----------------------------------------------------------------------
     * Descriptor getters — the hermetic public behaviour
     * -------------------------------------------------------------------- */

    @Test
    public void getPackageReturnsConsole() {
        assertEquals("Console", ConsoleTabFactory.getInstance().getPackage());
    }

    @Test
    public void getApplicationReturnsConsole() {
        assertEquals("Console", ConsoleTabFactory.getInstance().getApplication());
    }

    @Test
    public void getClassNameReturnsThisClassFullyQualifiedName() {
        assertEquals("org.scilab.modules.core.ConsoleTabFactory",
                     ConsoleTabFactory.getInstance().getClassName());
    }

    /* ----------------------------------------------------------------------
     * Getters agree with the published constants
     * -------------------------------------------------------------------- */

    @Test
    public void gettersMatchTheirCorrespondingConstants() {
        ConsoleTabFactory f = ConsoleTabFactory.getInstance();
        assertEquals(ConsoleTabFactory.PACKAGE, f.getPackage());
        assertEquals(ConsoleTabFactory.APPLICATION, f.getApplication());
        assertEquals(ConsoleTabFactory.CLASS, f.getClassName());
    }

    @Test
    public void constantsHaveTheirDocumentedValues() {
        assertEquals("Console", ConsoleTabFactory.APPLICATION);
        assertEquals("Console", ConsoleTabFactory.PACKAGE);
        assertEquals("org.scilab.modules.core.ConsoleTabFactory", ConsoleTabFactory.CLASS);
    }

    @Test
    public void packageAndApplicationConstantsAreIntentionallyEqual() {
        // Both describe the "Console" tab; document that they coincide by design.
        assertEquals(ConsoleTabFactory.PACKAGE, ConsoleTabFactory.APPLICATION);
    }

    /**
     * The CLASS constant / getClassName() must stay in lock-step with the real
     * fully-qualified name. This is the assertion that would fail (catching a real
     * maintenance defect) if the class were ever moved or renamed without the
     * hard-coded string being updated in tandem.
     */
    @Test
    public void classNameConstantTracksTheActualClass() {
        assertEquals(ConsoleTabFactory.class.getName(), ConsoleTabFactory.CLASS);
        assertEquals(ConsoleTabFactory.class.getName(),
                     ConsoleTabFactory.getInstance().getClassName());
    }

    /* ----------------------------------------------------------------------
     * Getters are pure/stable and instance-independent
     * -------------------------------------------------------------------- */

    @Test
    public void gettersAreStableAcrossRepeatedCalls() {
        ConsoleTabFactory f = ConsoleTabFactory.getInstance();
        assertEquals(f.getPackage(), f.getPackage());
        assertEquals(f.getApplication(), f.getApplication());
        assertEquals(f.getClassName(), f.getClassName());
    }

    @Test
    public void gettersDoNotDependOnWhichInstanceIsUsed() {
        // A directly-constructed instance yields identical descriptors to the singleton,
        // proving the getters read shared constants rather than per-instance state.
        ConsoleTabFactory singleton = ConsoleTabFactory.getInstance();
        ConsoleTabFactory fresh = new ConsoleTabFactory();
        assertEquals(singleton.getPackage(), fresh.getPackage());
        assertEquals(singleton.getApplication(), fresh.getApplication());
        assertEquals(singleton.getClassName(), fresh.getClassName());
    }

    @Test
    public void descriptorsAreNonEmpty() {
        ConsoleTabFactory f = ConsoleTabFactory.getInstance();
        assertTrue(f.getPackage().length() > 0);
        assertTrue(f.getApplication().length() > 0);
        assertTrue(f.getClassName().length() > 0);
    }
}
