/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Scilab test coverage
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 *
 */

package org.scilab.modules.xcos.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for the local {@link Stack} implementation (pure Java, no
 * runtime dependencies).
 *
 * Comparisons against {@code pop()}/{@code peek()} results use boxed
 * {@link Integer} expected values so the reference {@code assertEquals} overload
 * is selected unambiguously.
 */
public class StackTest {

    @Test
    public void newStackIsEmpty() {
        Stack<String> stack = new Stack<>();
        assertEquals(0, stack.size());
        assertEquals("[]", stack.toString());
    }

    @Test
    public void pushIncreasesSize() {
        Stack<Integer> stack = new Stack<>();
        stack.push(42);
        assertEquals(1, stack.size());
        stack.push(43);
        assertEquals(2, stack.size());
    }

    @Test
    public void pushThenPopIsLifo() {
        Stack<Integer> stack = new Stack<>();
        stack.push(1);
        stack.push(2);
        stack.push(3);

        assertEquals(Integer.valueOf(3), stack.pop());
        assertEquals(Integer.valueOf(2), stack.pop());
        assertEquals(Integer.valueOf(1), stack.pop());
        assertEquals(0, stack.size());
    }

    @Test
    public void peekReturnsTopWithoutRemoving() {
        Stack<String> stack = new Stack<>();
        stack.push("a");
        stack.push("b");

        assertEquals("b", stack.peek());
        assertEquals(2, stack.size(), "peek must not mutate the stack");
        // still the same top on a second peek
        assertEquals("b", stack.peek());
    }

    @Test
    public void peekDefaultEqualsPeekAtDepthZero() {
        Stack<String> stack = new Stack<>();
        stack.push("only");
        assertEquals(stack.peek(0), stack.peek());
    }

    @Test
    public void peekAtDepthWalksDownFromTop() {
        Stack<Integer> stack = new Stack<>();
        stack.push(10);
        stack.push(20);
        stack.push(30);

        assertEquals(Integer.valueOf(30), stack.peek(0));
        assertEquals(Integer.valueOf(20), stack.peek(1));
        assertEquals(Integer.valueOf(10), stack.peek(2));
    }

    @Test
    public void addAllAppendsInIterationOrder() {
        Stack<Integer> stack = new Stack<>();
        stack.push(1);
        stack.addAll(Arrays.asList(2, 3));

        assertEquals(3, stack.size());
        // last element of the added collection becomes the new top
        assertEquals(Integer.valueOf(3), stack.peek());
        assertEquals(Integer.valueOf(1), stack.peek(2));
        assertEquals("[1, 2, 3]", stack.toString());
    }

    @Test
    public void addAllEmptyCollectionLeavesStackUnchanged() {
        Stack<Integer> stack = new Stack<>();
        stack.push(7);
        stack.addAll(Collections.<Integer>emptyList());
        assertEquals(1, stack.size());
        assertEquals(Integer.valueOf(7), stack.peek());
    }

    @Test
    public void popAfterAddAllHonoursLifo() {
        Stack<Integer> stack = new Stack<>();
        stack.addAll(Arrays.asList(1, 2, 3));
        assertEquals(Integer.valueOf(3), stack.pop());
        assertEquals(Integer.valueOf(2), stack.pop());
        assertEquals(Integer.valueOf(1), stack.pop());
    }

    @Test
    public void toStringUsesUnderlyingInsertionOrder() {
        Stack<String> stack = new Stack<>();
        stack.push("a");
        stack.push("b");
        stack.push("c");
        // toString delegates to the backing ArrayList: bottom-to-top order.
        assertEquals("[a, b, c]", stack.toString());
    }

    @Test
    public void streamIteratesTopToBottom() {
        Stack<Integer> stack = new Stack<>();
        stack.push(1);
        stack.push(2);
        stack.push(3);

        List<Integer> streamed = stack.stream().collect(Collectors.toList());
        assertEquals(Arrays.asList(3, 2, 1), streamed);
    }

    @Test
    public void streamDoesNotConsumeTheStack() {
        Stack<Integer> stack = new Stack<>();
        stack.push(1);
        stack.push(2);

        long count = stack.stream().count();
        assertEquals(2, count);
        assertEquals(2, stack.size(), "streaming must not drain the stack");
    }

    @Test
    public void streamOfEmptyStackIsEmpty() {
        Stack<Integer> stack = new Stack<>();
        assertEquals(0, stack.stream().count());
    }

    @Test
    public void supportsNullElements() {
        Stack<String> stack = new Stack<>();
        stack.push(null);
        assertEquals(1, stack.size());
        assertNull(stack.peek());
        assertNull(stack.pop());
        assertEquals(0, stack.size());
    }

    @Test
    public void interleavedPushPopMaintainsLifoInvariant() {
        Stack<Integer> stack = new Stack<>();
        stack.push(1);
        stack.push(2);
        assertEquals(Integer.valueOf(2), stack.pop());
        stack.push(3);
        assertEquals(Integer.valueOf(3), stack.pop());
        assertEquals(Integer.valueOf(1), stack.pop());
        assertEquals(0, stack.size());
    }

    /* ---- boundary / exception behavior ---- */

    @Test
    public void popOnEmptyStackThrows() {
        Stack<Integer> stack = new Stack<>();
        // backing ArrayList.remove(-1) throws IndexOutOfBoundsException
        assertThrows(IndexOutOfBoundsException.class, () -> stack.pop());
    }

    @Test
    public void peekOnEmptyStackThrows() {
        Stack<Integer> stack = new Stack<>();
        assertThrows(IndexOutOfBoundsException.class, () -> stack.peek());
    }

    @Test
    public void peekAtDepthBeyondBottomThrows() {
        Stack<Integer> stack = new Stack<>();
        stack.push(1);
        // depth 5 -> ArrayList.get(-5)
        assertThrows(IndexOutOfBoundsException.class, () -> stack.peek(5));
    }

    @Test
    public void peekAtNegativeDepthThrows() {
        Stack<Integer> stack = new Stack<>();
        stack.push(1);
        stack.push(2);
        // depth -1 -> index size-1-(-1) == size, which is out of range
        assertThrows(IndexOutOfBoundsException.class, () -> stack.peek(-1));
    }
}
