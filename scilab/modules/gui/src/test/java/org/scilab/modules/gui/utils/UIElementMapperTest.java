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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;

import org.junit.jupiter.api.Test;
import org.scilab.modules.gui.uielement.UIElement;

/**
 * Hermetic unit tests for {@link UIElementMapper}.
 *
 * <p>{@code UIElementMapper} is a pure in-memory registry: {@code add} hands out
 * a monotonically increasing integer id, {@code getCorrespondingUIElement}
 * looks an element up by id, and {@code removeMapping} drops it. No native
 * runtime is involved, so it is tested directly.
 *
 * <p>Two things constrain these tests. First, the mapper's {@code maxId} and
 * backing map are <em>static</em> and shared across the whole test run — so the
 * assertions never depend on an absolute id, only on ids captured from the very
 * {@code add} call under test, and on the strictly-increasing contract. Second,
 * {@link UIElement} is a wide interface; rather than pull in a mock framework
 * (none is on the classpath) the tests fabricate distinct instances with a JDK
 * {@link Proxy}. Their methods are never invoked — only their identity matters.
 */
public class UIElementMapperTest {

    /** A fresh, distinct UIElement stub whose methods are never called. */
    private static UIElement newStubElement() {
        InvocationHandler handler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "toString":
                    return "StubUIElement@" + System.identityHashCode(proxy);
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                default:
                    return null;
            }
        };
        return (UIElement) Proxy.newProxyInstance(
                   UIElement.class.getClassLoader(),
                   new Class<?>[] {UIElement.class},
                   handler);
    }

    // --- add / lookup round-trip -------------------------------------------

    @Test
    public void addReturnsAnIdThatResolvesBackToTheSameElement() {
        UIElement e = newStubElement();
        int id = UIElementMapper.add(e);
        assertSame(e, UIElementMapper.getCorrespondingUIElement(id),
                   "lookup by the returned id must yield the very element that was added");
    }

    @Test
    public void assignedIdsAreStrictlyPositive() {
        // maxId starts at 0 and is pre-incremented, so the first id ever handed
        // out is 1 and every subsequent one is larger still.
        int id = UIElementMapper.add(newStubElement());
        assertTrue(id > 0, "expected a strictly positive id but got " + id);
    }

    @Test
    public void consecutiveAddsHandOutStrictlyIncreasingIds() {
        int id1 = UIElementMapper.add(newStubElement());
        int id2 = UIElementMapper.add(newStubElement());
        int id3 = UIElementMapper.add(newStubElement());
        assertTrue(id2 > id1 && id3 > id2,
                   "ids must increase monotonically, got " + id1 + ", " + id2 + ", " + id3);
    }

    @Test
    public void consecutiveAddsIncrementTheIdByExactlyOne() {
        // Characterization of the ++maxId allocator: with nothing interleaved
        // (tests run sequentially), back-to-back ids differ by exactly 1.
        int id1 = UIElementMapper.add(newStubElement());
        int id2 = UIElementMapper.add(newStubElement());
        assertEquals(id1 + 1, id2);
    }

    @Test
    public void distinctElementsGetIndependentMappings() {
        UIElement a = newStubElement();
        UIElement b = newStubElement();
        int idA = UIElementMapper.add(a);
        int idB = UIElementMapper.add(b);

        assertNotEquals(idA, idB);
        assertSame(a, UIElementMapper.getCorrespondingUIElement(idA));
        assertSame(b, UIElementMapper.getCorrespondingUIElement(idB));
    }

    @Test
    public void theSameElementAddedTwiceGetsTwoDistinctIds() {
        // The registry keys on the generated id, not on the element, so adding
        // one instance twice yields two separate live entries.
        UIElement e = newStubElement();
        int id1 = UIElementMapper.add(e);
        int id2 = UIElementMapper.add(e);
        assertNotEquals(id1, id2);
        assertSame(e, UIElementMapper.getCorrespondingUIElement(id1));
        assertSame(e, UIElementMapper.getCorrespondingUIElement(id2));
    }

    // --- removeMapping ------------------------------------------------------

    @Test
    public void removeMappingDropsTheEntry() {
        UIElement e = newStubElement();
        int id = UIElementMapper.add(e);
        assertSame(e, UIElementMapper.getCorrespondingUIElement(id));

        UIElementMapper.removeMapping(id);
        assertNull(UIElementMapper.getCorrespondingUIElement(id),
                   "the element must be gone after removeMapping");
    }

    @Test
    public void removeMappingLeavesOtherEntriesUntouched() {
        UIElement keep = newStubElement();
        UIElement drop = newStubElement();
        int keepId = UIElementMapper.add(keep);
        int dropId = UIElementMapper.add(drop);

        UIElementMapper.removeMapping(dropId);

        assertNull(UIElementMapper.getCorrespondingUIElement(dropId));
        assertSame(keep, UIElementMapper.getCorrespondingUIElement(keepId));
    }

    @Test
    public void removeMappingOfAnUnknownIdIsASilentNoOp() {
        // Negative ids are never allocated, so this can never hit a real entry.
        assertDoesNotThrow(() -> UIElementMapper.removeMapping(-987654));
    }

    // --- lookup of never-assigned ids --------------------------------------

    @Test
    public void lookupOfNeverAssignedIdsReturnsNull() {
        // id 0 is never handed out (maxId is pre-incremented) and negatives
        // never are either.
        assertNull(UIElementMapper.getCorrespondingUIElement(0));
        assertNull(UIElementMapper.getCorrespondingUIElement(-1));
        assertNull(UIElementMapper.getCorrespondingUIElement(Integer.MIN_VALUE));
    }

    // --- null element characterization -------------------------------------

    @Test
    public void addingNullStoresANullValueIndistinguishableFromAbsent() {
        // Characterization: add(null) still consumes an id, but the lookup then
        // returns null — the same result a missing id gives.
        int id = UIElementMapper.add(null);
        assertTrue(id > 0);
        assertNull(UIElementMapper.getCorrespondingUIElement(id));
    }

    // --- utility-class shape ------------------------------------------------

    @Test
    public void theClassIsFinal() {
        assertTrue(Modifier.isFinal(UIElementMapper.class.getModifiers()),
                   "UIElementMapper is documented as a utility class and is declared final");
    }

    @Test
    public void theOnlyConstructorIsPrivate() throws Exception {
        Constructor<?>[] ctors = UIElementMapper.class.getDeclaredConstructors();
        assertEquals(1, ctors.length, "utility class should expose exactly one constructor");
        assertTrue(Modifier.isPrivate(ctors[0].getModifiers()),
                   "the sole constructor must be private (utility class, no instances)");
    }
}
