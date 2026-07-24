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

package org.scilab.modules.jvm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import org.junit.jupiter.api.Test;

/**
 * Hermetic tests for {@link LoadClassPath}.
 *
 * <p>{@code loadOnUse()} is not fully hermetic (it wants {@code $SCI/etc/classpath.xml} and the
 * {@code commons} XML helper classes), but its <em>failure</em> path is deterministic here and
 * worth pinning: this module declares no dependency on {@code commons}, so
 * {@code ScilabXPathFactory} is absent from the test class loader.
 *
 * <p>The structural tests cover the utility-class shape; {@code loadOnUseThrows...} is a
 * defect-characterization test for a real latent bug (see its Javadoc).
 */
public class LoadClassPathTest {

    @Test
    public void classIsFinal() {
        assertTrue(Modifier.isFinal(LoadClassPath.class.getModifiers()));
    }

    @Test
    public void hasASinglePrivateConstructor() {
        Constructor<?>[] ctors = LoadClassPath.class.getDeclaredConstructors();
        assertEquals(1, ctors.length);
        assertTrue(Modifier.isPrivate(ctors[0].getModifiers()));
    }

    @Test
    public void privateConstructorInstantiatesToNoOpInstance() throws Exception {
        Constructor<LoadClassPath> ctor = LoadClassPath.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        assertNotNull(ctor.newInstance());
    }

    /**
     * Defect characterization. {@code loadOnUse()} catches the {@link ClassNotFoundException}
     * from {@code loadClass("...ScilabXPathFactory")} but only prints it, leaving the local
     * {@code sxpfClass} at {@code null}; the very next statement,
     * {@code sxpfClass.getDeclaredMethod(...)}, then dereferences that {@code null} and throws
     * an uncaught {@link NullPointerException} (the surrounding catch clauses handle only
     * reflection-specific checked exceptions). A robust implementation would surface the missing
     * dependency instead of NPE-ing. A module name never registered before guarantees the
     * early {@code loadedModules} cache check does not short-circuit this path.
     */
    @Test
    public void loadOnUseThrowsNpeWhenCommonsXmlHelpersAbsent() {
        assertThrows(NullPointerException.class,
                     () -> LoadClassPath.loadOnUse("a-module-never-registered-xyz"));
    }
}
