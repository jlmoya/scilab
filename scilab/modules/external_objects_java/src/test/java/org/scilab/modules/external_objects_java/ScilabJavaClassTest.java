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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.util.ArrayList;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link ScilabJavaClass}, the {@link ScilabJavaObject}
 * specialization whose wrapped value is a {@link Class} (so {@code object == clazz}).
 * Covered: construction/registration, the URL accessor pair, the {@code clone()} deep
 * copy, and {@code newInstance} both on the happy path (a no-arg constructor) and its two
 * guard clauses. Instantiation here uses plain JDK classes only — no Scilab, no native.
 */
public class ScilabJavaClassTest {

    @Test
    public void aClassWrapperHoldsTheClassAsBothObjectAndType() {
        ScilabJavaClass c = new ScilabJavaClass(ArrayList.class);
        assertTrue(c.id > 0);
        assertSame(ArrayList.class, c.clazz);
        assertSame(c.object, c.clazz, "a class wrapper stores the Class as its object too");
        assertSame(c, ScilabJavaObject.arraySJO[c.id]);
    }

    @Test
    public void urlIsNullUntilSetThenReadsBack() throws Exception {
        ScilabJavaClass c = new ScilabJavaClass(String.class);
        assertNull(c.getURL(), "no location until one is assigned");
        URL url = new java.io.File("/tmp/example.jar").toURI().toURL();
        c.setURL(url);
        assertSame(url, c.getURL());
    }

    @Test
    public void cloneCopiesClassAndUrlIntoAFreshRegistration() throws Exception {
        ScilabJavaClass c = new ScilabJavaClass(ArrayList.class);
        URL url = new java.io.File("/tmp/example.jar").toURI().toURL();
        c.setURL(url);

        ScilabJavaObject cloned = c.clone();
        assertTrue(cloned instanceof ScilabJavaClass);
        assertNotEquals(c.id, cloned.id, "the clone is a distinct registration");
        assertSame(ArrayList.class, cloned.clazz);
        assertSame(url, ((ScilabJavaClass) cloned).getURL());
    }

    @Test
    public void newInstanceConstructsFromANoArgConstructor() throws ScilabJavaException {
        ScilabJavaClass c = new ScilabJavaClass(ArrayList.class);
        int resultId = ScilabJavaClass.newInstance(c.id, new int[0]);

        assertTrue(ScilabJavaObject.isValidJavaObject(resultId));
        Object built = ScilabJavaObject.arraySJO[resultId].object;
        assertTrue(built instanceof ArrayList, "a real ArrayList instance is produced");
        assertEquals(0, ((ArrayList) built).size());
    }

    @Test
    public void newInstanceOnNullClassIsRejected() {
        assertThrows(ScilabJavaException.class, () -> ScilabJavaClass.newInstance(0, new int[0]));
    }

    @Test
    public void newInstanceOnANonClassObjectIsRejected() {
        // A plain wrapped value (not a ScilabJavaClass) cannot be instantiated.
        int notAClass = ScilabJavaObject.wrap("just a string");
        assertThrows(ScilabJavaException.class, () -> ScilabJavaClass.newInstance(notAClass, new int[0]));
    }

    @Test
    public void newInstanceWithAnArgumentInvokesTheMatchingConstructor() throws ScilabJavaException {
        ScilabJavaClass c = new ScilabJavaClass(StringBuilder.class);
        int resultId = ScilabJavaClass.newInstance(c.id, new int[] {ScilabJavaObject.wrap("seed")});

        assertTrue(ScilabJavaObject.isValidJavaObject(resultId));
        Object built = ScilabJavaObject.arraySJO[resultId].object;
        assertTrue(built instanceof StringBuilder, "the StringBuilder(String) constructor is selected");
        assertEquals("seed", built.toString());
    }
}
