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

package org.scilab.modules.gui.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link SwingScilabTextItem}.
 *
 * <p>{@code SwingScilabTextItem} is a tiny String wrapper that exists for one
 * reason (see its Javadoc / bug #7898): it renders via {@code toString()} but
 * deliberately <em>keeps {@code Object}'s identity-based {@code equals} and
 * {@code hashCode}</em>, so two items carrying the same text are never treated
 * as duplicates. These tests pin both halves of that contract: the text
 * pass-through, and the intentional absence of value equality.
 */
public class SwingScilabTextItemTest {

    // --- toString pass-through ---------------------------------------------

    @Test
    public void toStringReturnsTheTextGivenToTheConstructor() {
        assertEquals("hello", new SwingScilabTextItem("hello").toString());
    }

    @Test
    public void toStringReturnsNullWhenConstructedWithNull() {
        // Characterization: the wrapper does no null-guarding.
        assertNull(new SwingScilabTextItem(null).toString());
    }

    @Test
    public void toStringReturnsAnEmptyStringVerbatim() {
        assertEquals("", new SwingScilabTextItem("").toString());
    }

    @Test
    public void toStringPreservesWhitespaceAndUnicodeExactly() {
        String text = "  spaced \t line\nÉ→😀 ";
        assertEquals(text, new SwingScilabTextItem(text).toString());
    }

    @Test
    public void toStringReturnsTheSameStringInstanceItWasGiven() {
        // No copying/interning happens; the exact reference is handed back.
        String text = new String("payload");
        assertSame(text, new SwingScilabTextItem(text).toString());
    }

    // --- identity equality (the reason this class exists) -------------------

    @Test
    public void anItemEqualsItself() {
        SwingScilabTextItem item = new SwingScilabTextItem("x");
        assertEquals(item, item);
    }

    @Test
    public void twoItemsWithTheSameTextAreNotEqual() {
        // The load-bearing behaviour: value-equal text must NOT make them equal.
        SwingScilabTextItem a = new SwingScilabTextItem("same");
        SwingScilabTextItem b = new SwingScilabTextItem("same");
        assertFalse(a.equals(b), "same-text items must remain distinct (bug #7898 workaround)");
        assertFalse(b.equals(a), "inequality must be symmetric");
    }

    @Test
    public void twoItemsBothWrappingNullAreStillNotEqual() {
        SwingScilabTextItem a = new SwingScilabTextItem(null);
        SwingScilabTextItem b = new SwingScilabTextItem(null);
        assertFalse(a.equals(b));
    }

    @Test
    public void anItemDoesNotEqualNull() {
        assertFalse(new SwingScilabTextItem("x").equals(null));
    }

    @Test
    public void anItemDoesNotEqualTheRawStringItWraps() {
        // Even though toString() equals the String, equals() is identity-based.
        SwingScilabTextItem item = new SwingScilabTextItem("x");
        assertFalse(item.equals("x"));
    }

    // --- identity hashCode --------------------------------------------------

    @Test
    public void hashCodeIsTheIdentityHashCodeNotDerivedFromText() {
        SwingScilabTextItem item = new SwingScilabTextItem("anything");
        assertEquals(System.identityHashCode(item), item.hashCode());
    }

    @Test
    public void hashCodeIsStableAcrossRepeatedCalls() {
        SwingScilabTextItem item = new SwingScilabTextItem("stable");
        int first = item.hashCode();
        assertEquals(first, item.hashCode());
        assertEquals(first, item.hashCode());
    }

    // --- real-world consequence: duplicates survive in a Set ----------------

    @Test
    public void sameTextItemsCoexistAsDistinctSetMembers() {
        // This is precisely the behaviour the class was written to provide:
        // a combo/list model can hold two entries reading "duplicate".
        Set<SwingScilabTextItem> set = new HashSet<SwingScilabTextItem>();
        set.add(new SwingScilabTextItem("duplicate"));
        set.add(new SwingScilabTextItem("duplicate"));
        assertEquals(2, set.size(), "identity equality must keep same-text items separate");
    }

    @Test
    public void aSetContainsAnItemOnlyByIdentity() {
        SwingScilabTextItem inside = new SwingScilabTextItem("member");
        Set<SwingScilabTextItem> set = new HashSet<SwingScilabTextItem>();
        set.add(inside);
        assertTrue(set.contains(inside));
        // A different instance with identical text is NOT considered present.
        assertFalse(set.contains(new SwingScilabTextItem("member")));
    }

    // --- design intent: equals/hashCode are intentionally not overridden -----

    @Test
    public void toStringIsOverriddenButEqualsAndHashCodeAreNot() throws Exception {
        // Directly documents the workaround: only toString is declared locally;
        // equals/hashCode are inherited unchanged from Object.
        assertEquals(String.class,
                     SwingScilabTextItem.class.getDeclaredMethod("toString").getReturnType());

        assertThrows(NoSuchMethodException.class,
                     () -> SwingScilabTextItem.class.getDeclaredMethod("equals", Object.class),
                     "equals must NOT be overridden — identity equality is the whole point");
        assertThrows(NoSuchMethodException.class,
                     () -> SwingScilabTextItem.class.getDeclaredMethod("hashCode"),
                     "hashCode must NOT be overridden — it stays identity-based");
    }
}
