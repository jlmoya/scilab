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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link ScilabOperations#add}, the only operator the module
 * overloads: a String concatenation between a wrapped String and any other wrapped value.
 * Values are supplied through the {@link ScilabJavaObject} reference table (the id-based
 * protocol the JNI layer uses) and the concatenated result is read back via
 * {@code getRepresentation}. The private {@code toString} helper's array-formatting rules
 * are pinned as observable behavior through {@code add}.
 */
public class ScilabOperationsTest {

    private static String addAndRead(int idA, int idB) throws ScilabJavaException {
        return ScilabJavaObject.getRepresentation(ScilabOperations.add(idA, idB));
    }

    @Test
    public void concatenatesTwoStrings() throws ScilabJavaException {
        assertEquals("foobar", addAndRead(ScilabJavaObject.wrap("foo"), ScilabJavaObject.wrap("bar")));
    }

    @Test
    public void concatenatesStringOnTheLeftWithAScalar() throws ScilabJavaException {
        assertEquals("v=2.5", addAndRead(ScilabJavaObject.wrap("v="), ScilabJavaObject.wrap(2.5)));
    }

    @Test
    public void concatenatesScalarOnTheLeftWithStringOnTheRight() throws ScilabJavaException {
        assertEquals("2.5=v", addAndRead(ScilabJavaObject.wrap(2.5), ScilabJavaObject.wrap("=v")));
    }

    @Test
    public void concatenatesAPrimitiveArrayFormattedWithoutOuterBrackets() throws ScilabJavaException {
        // A primitive array is deep-printed then stripped of one bracket level: "[1.0, 2.0]".
        assertEquals("[1.0, 2.0]!",
                     addAndRead(ScilabJavaObject.wrap(new double[] {1.0, 2.0}), ScilabJavaObject.wrap("!")));
    }

    @Test
    public void concatenatesAReferenceArrayViaDeepToString() throws ScilabJavaException {
        assertEquals("[a, b]!",
                     addAndRead(ScilabJavaObject.wrap(new String[] {"a", "b"}), ScilabJavaObject.wrap("!")));
    }

    @Test
    public void rejectsTwoNonStringOperands() {
        assertThrows(ScilabJavaException.class,
                     () -> ScilabOperations.add(ScilabJavaObject.wrap(1.0), ScilabJavaObject.wrap(2.0)));
    }

    @Test
    public void rejectsNullOperands() {
        int s = ScilabJavaObject.wrap("s");
        assertThrows(ScilabJavaException.class, () -> ScilabOperations.add(0, s));
        assertThrows(ScilabJavaException.class, () -> ScilabOperations.add(s, 0));
    }

    @Test
    public void theUtilityClassCanBeInstantiated() {
        // ScilabOperations is a stateless static-only helper; constructing it exercises the
        // implicit default constructor and documents that it has no side effects.
        assertNotNull(new ScilabOperations());
    }
}
