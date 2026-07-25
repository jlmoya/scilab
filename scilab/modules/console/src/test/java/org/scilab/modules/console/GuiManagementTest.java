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

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link GuiManagement}, the SWIG-generated facade over
 * the native {@code GuiManagementJNI}. Its {@code setScilabLines}/{@code
 * forceScilabLines} methods bridge to JNI and need a running Scilab, so they are
 * out of scope. Verifiable without any native library is the facade's shape: a
 * non-instantiable utility class (its protected constructor throws) whose
 * line-sizing entry points are declared {@code static}. This locks in the SWIG
 * contract against an accidental regeneration.
 */
public class GuiManagementTest {

    @Test
    public void theProtectedConstructorRefusesInstantiation() throws Exception {
        Constructor<GuiManagement> ctor = GuiManagement.class.getDeclaredConstructor();
        assertTrue(Modifier.isProtected(ctor.getModifiers()), "constructor is expected to be protected");
        ctor.setAccessible(true);

        InvocationTargetException wrapper =
            assertThrows(InvocationTargetException.class, () -> ctor.newInstance());
        assertInstanceOf(UnsupportedOperationException.class, wrapper.getCause());
    }

    @Test
    public void forceScilabLinesIsExposedAsAPublicStaticEntryPoint() throws Exception {
        Method m = GuiManagement.class.getDeclaredMethod("forceScilabLines", int.class, int.class);
        assertTrue(Modifier.isStatic(m.getModifiers()), "forceScilabLines must be static");
        assertTrue(Modifier.isPublic(m.getModifiers()), "forceScilabLines must be public");
    }

    @Test
    public void setScilabLinesIsAStaticPackagePrivateHelper() throws Exception {
        // Characterization of the SWIG output: setScilabLines is generated as a
        // static, package-private companion to the public forceScilabLines.
        Method m = GuiManagement.class.getDeclaredMethod("setScilabLines", int.class, int.class);
        assertTrue(Modifier.isStatic(m.getModifiers()), "setScilabLines must be static");
        assertTrue(!Modifier.isPublic(m.getModifiers()) && !Modifier.isProtected(m.getModifiers())
                   && !Modifier.isPrivate(m.getModifiers()),
                   "setScilabLines is expected to be package-private");
    }
}
