/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Scilab Enterprises
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 *
 */

package org.scilab.modules.graphic_objects.xmlloader;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Hermetic unit tests for the generic {@link Pair} data holder, covering
 * accessors, mutators and the equals/hashCode contract.
 */
public class PairTest {

    @Test
    public void constructorAndAccessors() {
        Pair<String, Integer> p = new Pair<>("a", 1);
        assertEquals("a", p.getFirst());
        assertEquals(Integer.valueOf(1), p.getSecond());
    }

    @Test
    public void settersUpdateFields() {
        Pair<String, Integer> p = new Pair<>("a", 1);
        p.setFirst("b");
        p.setSecond(2);
        assertEquals("b", p.getFirst());
        assertEquals(Integer.valueOf(2), p.getSecond());
    }

    @Test
    public void setUpdatesBothAtOnce() {
        Pair<String, Integer> p = new Pair<>("a", 1);
        p.set("z", 99);
        assertEquals("z", p.getFirst());
        assertEquals(Integer.valueOf(99), p.getSecond());
    }

    @Test
    public void allowsNullComponents() {
        Pair<String, Integer> p = new Pair<>(null, null);
        assertNull(p.getFirst());
        assertNull(p.getSecond());
    }

    @Test
    public void equalsIsReflexive() {
        Pair<String, Integer> p = new Pair<>("a", 1);
        assertEquals(p, p);
    }

    @Test
    public void equalPairsAreEqualAndShareHashCode() {
        Pair<String, Integer> a = new Pair<>("a", 1);
        Pair<String, Integer> b = new Pair<>("a", 1);
        assertEquals(a, b);
        assertEquals(b, a);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void differsWhenEitherComponentDiffers() {
        Pair<String, Integer> base = new Pair<>("a", 1);
        assertNotEquals(base, new Pair<>("a", 2));
        assertNotEquals(base, new Pair<>("b", 1));
    }

    @Test
    public void notEqualToNullOrOtherType() {
        Pair<String, Integer> p = new Pair<>("a", 1);
        assertNotEquals(p, null);
        assertNotEquals(p, "a");
    }

    @Test
    public void nullComponentsCompareEqualAndHashToZero() {
        Pair<String, Integer> a = new Pair<>(null, null);
        Pair<String, Integer> b = new Pair<>(null, null);
        assertEquals(a, b);
        assertEquals(0, a.hashCode());
    }

    @Test
    public void nullVersusNonNullComponentIsNotEqual() {
        assertNotEquals(new Pair<>(null, 1), new Pair<>("a", 1));
        assertNotEquals(new Pair<>("a", null), new Pair<>("a", 1));
    }

    @Test
    public void hashCodeUsesFirstThenSecond() {
        // Documented formula: 31 * first.hashCode() + second.hashCode().
        Pair<String, String> p = new Pair<>("x", "y");
        int expected = 31 * "x".hashCode() + "y".hashCode();
        assertEquals(expected, p.hashCode());
    }
}
