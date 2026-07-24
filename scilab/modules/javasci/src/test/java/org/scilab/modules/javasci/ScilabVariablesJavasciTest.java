/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Jose Moya
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 *
 */

package org.scilab.modules.javasci;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import org.junit.jupiter.api.Test;

import org.scilab.modules.types.ScilabVariablesHandler;

/**
 * Hermetic unit tests for {@link ScilabVariablesJavasci}.
 *
 * Only the null / empty-name contract of {@code getScilabVariable} is
 * exercised. Those branches are pure Java: they register the singleton handler
 * (via {@code ScilabVariables.addScilabVariablesHandler}, a plain
 * {@code Vector} operation) and then return {@code null} without ever reaching
 * the native {@code GetScilabVariable} retrieval path — which only runs for a
 * non-null, non-empty name. No Scilab engine or native library is required.
 *
 * <p>The static handler {@code id} is registered lazily on first use and reused
 * thereafter, so every null / empty call returns {@code null} regardless of the
 * order the test methods run in.</p>
 */
class ScilabVariablesJavasciTest {

    // ------------------------------------------------------------------
    // null name -> null (the guard `name != null` short-circuits before the
    // native retrieval branch) across all three overloads and both byref modes
    // ------------------------------------------------------------------

    @Test
    void nullNameReturnsNull_singleArgOverload() {
        assertNull(ScilabVariablesJavasci.getScilabVariable(null));
    }

    @Test
    void nullNameReturnsNull_swapRowColOverload() {
        assertNull(ScilabVariablesJavasci.getScilabVariable(null, true));
        assertNull(ScilabVariablesJavasci.getScilabVariable(null, false));
    }

    @Test
    void nullNameReturnsNull_byRefOverload() {
        // byref == true must NOT reach GetScilabVariable.getScilabVariableAsReference
        // when the name is null.
        assertNull(ScilabVariablesJavasci.getScilabVariable(null, true, true));
        assertNull(ScilabVariablesJavasci.getScilabVariable(null, false, true));
        assertNull(ScilabVariablesJavasci.getScilabVariable(null, true, false));
    }

    // ------------------------------------------------------------------
    // empty name -> null (the guard `!name.isEmpty()` short-circuits) across
    // all three overloads and both byref modes
    // ------------------------------------------------------------------

    @Test
    void emptyNameReturnsNull_singleArgOverload() {
        assertNull(ScilabVariablesJavasci.getScilabVariable(""));
    }

    @Test
    void emptyNameReturnsNull_swapRowColOverload() {
        assertNull(ScilabVariablesJavasci.getScilabVariable("", true));
        assertNull(ScilabVariablesJavasci.getScilabVariable("", false));
    }

    @Test
    void emptyNameReturnsNull_byRefOverload() {
        assertNull(ScilabVariablesJavasci.getScilabVariable("", true, true));
        assertNull(ScilabVariablesJavasci.getScilabVariable("", false, true));
        assertNull(ScilabVariablesJavasci.getScilabVariable("", true, false));
    }

    // ------------------------------------------------------------------
    // Structural invariants of the handler class
    // ------------------------------------------------------------------

    @Test
    void classIsFinal() {
        assertTrue(Modifier.isFinal(ScilabVariablesJavasci.class.getModifiers()),
                   "ScilabVariablesJavasci is declared final");
    }

    @Test
    void implementsScilabVariablesHandler() {
        assertTrue(ScilabVariablesHandler.class.isAssignableFrom(ScilabVariablesJavasci.class),
                   "ScilabVariablesJavasci is the javasci ScilabVariablesHandler");
    }

    @Test
    void constructorIsPrivate() throws NoSuchMethodException {
        // The class is used only through its static getScilabVariable factory /
        // internally-registered singleton, so its sole constructor is private.
        Constructor<ScilabVariablesJavasci> ctor = ScilabVariablesJavasci.class.getDeclaredConstructor();
        assertTrue(Modifier.isPrivate(ctor.getModifiers()),
                   "the only constructor must be private");
    }
}
