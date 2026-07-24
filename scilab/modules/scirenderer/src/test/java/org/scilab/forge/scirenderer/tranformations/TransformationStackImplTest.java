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

package org.scilab.forge.scirenderer.tranformations;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EmptyStackException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hermetic unit tests for {@link TransformationStackImpl}, the LIFO stack of
 * {@link Transformation}s used by the transformation manager. Everything here is pure
 * Java (an in-memory stack plus a Swing {@code EventListenerList}); no canvas, no GL.
 */
public class TransformationStackImplTest {

    /** A listener that records every {@code changed} callback it receives. */
    private static final class RecordingListener implements TransformationStackListener {
        final List<TransformationStack.TransformationStackEvent> events = new ArrayList<>();
        final List<Transformation> tops = new ArrayList<>();

        @Override
        public void changed(TransformationStack stack, TransformationStack.TransformationStackEvent event, Transformation top) {
            events.add(event);
            tops.add(top);
        }
    }

    @Test
    public void emptyStackPeeksTheIdentity() {
        TransformationStackImpl stack = new TransformationStackImpl();
        assertTrue(stack.peek().isIdentity());
        // The identity singleton is what a fresh stack hands back.
        assertSame(TransformationFactory.getIdentity(), stack.peek());
    }

    @Test
    public void pushThenPeekReturnsTheSameInstance() {
        TransformationStackImpl stack = new TransformationStackImpl();
        Transformation t = TransformationFactory.getTranslateTransformation(1, 2, 3);
        stack.push(t);
        assertSame(t, stack.peek());
        assertFalse(stack.peek().isIdentity());
    }

    @Test
    public void pushNullIsANoOpAndFiresNothing() {
        TransformationStackImpl stack = new TransformationStackImpl();
        RecordingListener listener = new RecordingListener();
        stack.addListener(listener);

        stack.push(null);

        assertTrue(stack.peek().isIdentity());
        assertTrue(listener.events.isEmpty(), "push(null) must not notify listeners");
    }

    @Test
    public void popReturnsThePushedValueAndFiresPopped() {
        TransformationStackImpl stack = new TransformationStackImpl();
        Transformation t = TransformationFactory.getTranslateTransformation(5, 0, 0);
        RecordingListener listener = new RecordingListener();
        stack.push(t);
        stack.addListener(listener);

        Transformation popped = stack.pop();

        assertSame(t, popped);
        assertTrue(stack.peek().isIdentity(), "stack is empty again after popping the only element");
        assertEquals(1, listener.events.size());
        assertEquals(TransformationStack.TransformationStackEvent.POPPED, listener.events.get(0));
        assertSame(t, listener.tops.get(0));
    }

    @Test
    public void popOnEmptyStackThrows() {
        // Defect characterization: peek() degrades gracefully to the identity on an empty
        // stack, but pop() delegates straight to java.util.Stack.pop() and therefore throws
        // an unchecked EmptyStackException rather than returning the identity.
        TransformationStackImpl stack = new TransformationStackImpl();
        assertThrows(EmptyStackException.class, stack::pop);
    }

    @Test
    public void pushRightMultiplyOnEmptyStackComposesWithTheIdentity() {
        TransformationStackImpl stack = new TransformationStackImpl();
        Transformation t = TransformationFactory.getTranslateTransformation(2, 2, 2);
        stack.pushRightMultiply(t);
        // identity.rightTimes(t) collapses to t itself.
        assertSame(t, stack.peek());
    }

    @Test
    public void pushLeftMultiplyOnEmptyStackComposesWithTheIdentity() {
        TransformationStackImpl stack = new TransformationStackImpl();
        Transformation t = TransformationFactory.getTranslateTransformation(0, 3, 0);
        stack.pushLeftMultiply(t);
        assertSame(t, stack.peek());
    }

    @Test
    public void composingTwoTranslationsAddsTheirOffsets() {
        TransformationStackImpl stack = new TransformationStackImpl();
        stack.push(TransformationFactory.getTranslateTransformation(1, 0, 0));
        stack.pushRightMultiply(TransformationFactory.getTranslateTransformation(0, 2, 0));

        Vector3d image = stack.peek().project(new Vector3d(0, 0, 0));
        assertTrue(new Vector3d(1, 2, 0).equals(image), "translations compose additively, got " + image);
        // The composite is genuinely a product, no longer the identity.
        assertFalse(stack.peek().isIdentity());
    }

    @Test
    public void clearEmptiesTheStackAndFiresClearedWithTheIdentityTop() {
        TransformationStackImpl stack = new TransformationStackImpl();
        stack.push(TransformationFactory.getTranslateTransformation(1, 1, 1));
        stack.push(TransformationFactory.getTranslateTransformation(2, 2, 2));
        RecordingListener listener = new RecordingListener();
        stack.addListener(listener);

        stack.clear();

        assertTrue(stack.peek().isIdentity());
        assertEquals(1, listener.events.size());
        assertEquals(TransformationStack.TransformationStackEvent.CLEARED, listener.events.get(0));
        assertTrue(listener.tops.get(0).isIdentity(), "the reported top after clear is the identity");
    }

    @Test
    public void pushedEventCarriesThePushedTransformation() {
        TransformationStackImpl stack = new TransformationStackImpl();
        RecordingListener listener = new RecordingListener();
        stack.addListener(listener);

        Transformation t = TransformationFactory.getTranslateTransformation(7, 8, 9);
        stack.push(t);

        assertEquals(1, listener.events.size());
        assertEquals(TransformationStack.TransformationStackEvent.PUSHED, listener.events.get(0));
        assertSame(t, listener.tops.get(0));
    }

    @Test
    public void removedListenerStopsReceivingEvents() {
        TransformationStackImpl stack = new TransformationStackImpl();
        RecordingListener listener = new RecordingListener();
        stack.addListener(listener);
        stack.push(TransformationFactory.getTranslateTransformation(1, 0, 0));
        assertEquals(1, listener.events.size());

        stack.removeListener(listener);
        stack.push(TransformationFactory.getTranslateTransformation(0, 1, 0));
        stack.pop();

        assertEquals(1, listener.events.size(), "no further events after removeListener");
    }

    @Test
    public void severalListenersAllGetNotified() {
        TransformationStackImpl stack = new TransformationStackImpl();
        RecordingListener a = new RecordingListener();
        RecordingListener b = new RecordingListener();
        stack.addListener(a);
        stack.addListener(b);

        stack.push(TransformationFactory.getTranslateTransformation(1, 2, 3));

        assertEquals(1, a.events.size());
        assertEquals(1, b.events.size());
    }
}
