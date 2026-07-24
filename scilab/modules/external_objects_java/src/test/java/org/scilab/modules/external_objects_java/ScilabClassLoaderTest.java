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

package org.scilab.modules.external_objects_java;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link ScilabClassLoader#loadJavaClass}. Loading by name resolves
 * through the ordinary system class loader, so JDK classes on the test classpath load
 * without any Scilab runtime. The result is registered as a {@link ScilabJavaClass} in the
 * reference table and its integer id returned; a missing name is surfaced as a
 * {@link ScilabJavaException}.
 */
public class ScilabClassLoaderTest {

    @Test
    public void loadsAKnownJdkClassAndRegistersItAsAClassWrapper() throws ScilabJavaException {
        int id = ScilabClassLoader.loadJavaClass("java.util.StringJoiner");
        assertTrue(ScilabJavaObject.isValidJavaObject(id));
        assertEquals("java.util.StringJoiner", ScilabJavaObject.getClassName(id));
        assertTrue(ScilabJavaObject.arraySJO[id] instanceof ScilabJavaClass,
                   "a loaded class is wrapped as a ScilabJavaClass");
    }

    @Test
    public void loadWithoutReloadAlsoResolvesAFreshClass() throws ScilabJavaException {
        int id = ScilabClassLoader.loadJavaClass("java.util.StringTokenizer", false);
        assertTrue(ScilabJavaObject.isValidJavaObject(id));
        assertEquals("java.util.StringTokenizer", ScilabJavaObject.getClassName(id));
    }

    @Test
    public void missingClassIsReportedAsScilabJavaException() {
        assertThrows(ScilabJavaException.class,
                     () -> ScilabClassLoader.loadJavaClass("no.such.pkg.DefinitelyMissing12345"));
    }
}
