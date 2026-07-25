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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link DropFiles}, the SWIG-generated facade over the
 * native {@code DropFilesJNI}. The {@code dropFiles(String[])} method itself
 * bridges to JNI and needs a running Scilab, so it is out of scope; what IS
 * verifiable without any native library is the facade's shape: it is a
 * non-instantiable utility class (its protected constructor throws) exposing a
 * single {@code public static} entry point. This pins down the SWIG contract so
 * a regeneration that accidentally made the class instantiable would be caught.
 */
public class DropFilesTest {

    @Test
    public void theProtectedConstructorRefusesInstantiation() throws Exception {
        Constructor<DropFiles> ctor = DropFiles.class.getDeclaredConstructor();
        assertTrue(Modifier.isProtected(ctor.getModifiers()), "constructor is expected to be protected");
        ctor.setAccessible(true);

        InvocationTargetException wrapper =
            assertThrows(InvocationTargetException.class, () -> ctor.newInstance());
        assertInstanceOf(UnsupportedOperationException.class, wrapper.getCause());
    }

    @Test
    public void dropFilesIsExposedAsAPublicStaticBooleanEntryPoint() throws Exception {
        Method m = DropFiles.class.getDeclaredMethod("dropFiles", String[].class);
        assertTrue(Modifier.isStatic(m.getModifiers()), "dropFiles must be static");
        assertTrue(Modifier.isPublic(m.getModifiers()), "dropFiles must be public");
        assertEquals(boolean.class, m.getReturnType());
    }
}
