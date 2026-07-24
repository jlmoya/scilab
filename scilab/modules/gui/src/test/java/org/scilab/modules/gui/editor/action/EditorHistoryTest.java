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

package org.scilab.modules.gui.editor.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link EditorHistory}, the editor undo/redo ring.
 *
 * <p>{@code EditorHistory} depends only on the {@link Action} interface (four
 * methods: {@code undo}, {@code redo}, {@code exists}, {@code dispose}), so the
 * tests drive it with an in-file {@link FakeAction} recorder — no Scilab
 * runtime, Swing peer or graphics object is touched.
 *
 * <p>Two families of tests live here:
 * <ul>
 *   <li><b>Public-behavior / defect characterization.</b> {@code head} is
 *       initialised to {@code -1} and no public method ever raises it to
 *       {@code >= 0} (the only increment, in {@code undo()}, sits <em>after</em>
 *       a {@code history.get(head)} that throws while {@code head == -1}).
 *       The consequences are pinned down: {@code undo()} always throws
 *       {@link IndexOutOfBoundsException}, {@code redo()} is always a no-op, and
 *       both {@code isUndoEnabled()}/{@code isRedoEnabled()} stay {@code false}
 *       even after actions are added. These document current behavior; several
 *       look like latent bugs.</li>
 *   <li><b>White-box branch coverage.</b> {@code head} and {@code history} are
 *       package-private, so state is injected directly to exercise the
 *       {@code exists()}-true/false branches of {@code undo()}/{@code redo()},
 *       the trim-and-dispose and max-capacity branches of {@code addAction()},
 *       and every boundary of the two {@code isXxxEnabled()} predicates.</li>
 * </ul>
 */
public class EditorHistoryTest {

    /** Records every interface call so tests can assert on interaction counts. */
    private static final class FakeAction implements Action {
        final String name;
        boolean exists;
        int undoCount;
        int redoCount;
        int disposeCount;
        int existsCount;

        FakeAction(String name) {
            this(name, true);
        }

        FakeAction(String name, boolean exists) {
            this.name = name;
            this.exists = exists;
        }

        public void undo() {
            undoCount++;
        }

        public void redo() {
            redoCount++;
        }

        public boolean exists() {
            existsCount++;
            return exists;
        }

        public void dispose() {
            disposeCount++;
        }

        public String toString() {
            return name;
        }
    }

    // --- construction -------------------------------------------------------

    @Test
    public void freshHistoryStartsEmptyWithHeadAtMinusOne() {
        EditorHistory h = new EditorHistory();
        assertNotNull(h.history);
        assertTrue(h.history.isEmpty());
        assertEquals(-1, h.head);
    }

    // --- addAction (default head == -1) ------------------------------------

    @Test
    public void addActionPushesOntoTheFrontAndLeavesHeadUntouched() {
        EditorHistory h = new EditorHistory();
        FakeAction a1 = new FakeAction("a1");
        FakeAction a2 = new FakeAction("a2");

        h.addAction(a1);
        h.addAction(a2);

        assertEquals(2, h.history.size());
        assertSame(a2, h.history.getFirst());   // most recent is at the front
        assertSame(a1, h.history.getLast());
        assertEquals(-1, h.head);                 // never advanced by addAction
        // Nothing was trimmed, so nothing was disposed.
        assertEquals(0, a1.disposeCount);
        assertEquals(0, a2.disposeCount);
    }

    @Test
    public void addActionTrimsAndDisposesEverythingAboveHeadWhenHeadIsPositive() {
        EditorHistory h = new EditorHistory();
        FakeAction a1 = new FakeAction("a1");
        FakeAction a2 = new FakeAction("a2");
        FakeAction a3 = new FakeAction("a3");
        h.addAction(a1);
        h.addAction(a2);
        h.addAction(a3);            // history == [a3, a2, a1]

        h.head = 2;                 // simulate having undone twice
        FakeAction a4 = new FakeAction("a4");
        h.addAction(a4);

        // The two front entries above the pointer are dropped and disposed,
        // head is walked back down to 0, then the new action is pushed.
        assertEquals(1, a3.disposeCount);
        assertEquals(1, a2.disposeCount);
        assertEquals(0, a1.disposeCount);
        assertEquals(0, a4.disposeCount);
        assertEquals(0, h.head);
        assertEquals(2, h.history.size());
        assertSame(a4, h.history.getFirst());
        assertSame(a1, h.history.getLast());
        assertFalse(h.history.contains(a3));
        assertFalse(h.history.contains(a2));
    }

    @Test
    public void addActionAtMaxCapacityDropsAndDisposesTheOldestEntry() {
        EditorHistory h = new EditorHistory();
        FakeAction oldest = new FakeAction("oldest");
        h.addAction(oldest);
        // Fill up to the 100-entry cap (1 + 99 == 100).
        for (int i = 0; i < 99; i++) {
            h.addAction(new FakeAction("f" + i));
        }
        assertEquals(100, h.history.size());

        FakeAction newest = new FakeAction("newest");
        h.addAction(newest);        // triggers the capacity branch

        assertEquals(100, h.history.size());          // still capped
        assertSame(newest, h.history.getFirst());     // newest at the front
        assertEquals(1, oldest.disposeCount);         // eldest evicted + disposed
        assertFalse(h.history.contains(oldest));
    }

    // --- undo: defect characterization (head stuck at -1) -------------------

    @Test
    public void undoOnAnEmptyHistoryThrowsBecauseHeadIsMinusOne() {
        EditorHistory h = new EditorHistory();
        assertThrows(IndexOutOfBoundsException.class, () -> h.undo());
    }

    @Test
    public void undoAfterAddingActionsStillThrowsBecauseHeadWasNeverAdvanced() {
        EditorHistory h = new EditorHistory();
        FakeAction a1 = new FakeAction("a1");
        h.addAction(a1);

        // get(-1) blows up before the action is ever consulted.
        assertThrows(IndexOutOfBoundsException.class, () -> h.undo());
        assertEquals(0, a1.undoCount);
        assertEquals(0, a1.existsCount);
    }

    // --- undo: white-box branch coverage -----------------------------------

    @Test
    public void undoInvokesTheActionAndAdvancesHeadWhenTheActionStillExists() {
        EditorHistory h = new EditorHistory();
        FakeAction a1 = new FakeAction("a1", true);
        h.addAction(a1);
        h.head = 0;                 // point at the single action

        h.undo();

        assertEquals(1, a1.undoCount);
        assertEquals(1, h.head);                  // advanced past the undone action
        assertEquals(1, h.history.size());        // kept in the list
        assertSame(a1, h.history.getFirst());
    }

    @Test
    public void undoDropsAStaleActionWithoutUndoingItWhenItNoLongerExists() {
        EditorHistory h = new EditorHistory();
        FakeAction a1 = new FakeAction("a1", false);
        h.addAction(a1);
        h.head = 0;

        h.undo();

        assertEquals(0, a1.undoCount);            // never undone
        assertEquals(0, h.history.size());        // removed from the list
        assertEquals(0, h.head);                  // head not advanced on the stale path
    }

    // --- redo: defect characterization (head stuck at <= 0) -----------------

    @Test
    public void redoOnAFreshHistoryIsASilentNoOp() {
        EditorHistory h = new EditorHistory();
        // head == -1, so the head > 0 guard is false: no throw, nothing happens.
        h.redo();
        assertEquals(-1, h.head);
        assertTrue(h.history.isEmpty());
    }

    @Test
    public void redoAfterAddingActionsDoesNothingBecauseHeadIsNotPositive() {
        EditorHistory h = new EditorHistory();
        FakeAction a1 = new FakeAction("a1");
        h.addAction(a1);

        h.redo();

        assertEquals(0, a1.redoCount);
        assertEquals(-1, h.head);
        assertEquals(1, h.history.size());
    }

    @Test
    public void redoIsANoOpAtTheHeadEqualsZeroBoundary() {
        EditorHistory h = new EditorHistory();
        FakeAction a1 = new FakeAction("a1");
        h.addAction(a1);
        h.head = 0;                 // head > 0 is false at exactly 0

        h.redo();

        assertEquals(0, a1.redoCount);
        assertEquals(0, h.head);
    }

    // --- redo: white-box branch coverage -----------------------------------

    @Test
    public void redoDecrementsHeadAndReplaysTheActionWhenItStillExists() {
        EditorHistory h = new EditorHistory();
        FakeAction a1 = new FakeAction("a1", true);
        FakeAction a2 = new FakeAction("a2", true);
        h.addAction(a1);
        h.addAction(a2);            // history == [a2, a1]
        h.head = 1;

        h.redo();                   // --head -> 0, get(0) == a2, a2.exists() -> replay

        assertEquals(0, h.head);
        assertEquals(1, a2.redoCount);
        assertEquals(0, a1.redoCount);
        assertEquals(2, h.history.size());
    }

    @Test
    public void redoDisposesAndRemovesAStaleActionInsteadOfReplayingIt() {
        EditorHistory h = new EditorHistory();
        FakeAction a1 = new FakeAction("a1", true);
        FakeAction a2 = new FakeAction("a2", false);
        h.addAction(a1);
        h.addAction(a2);            // history == [a2, a1]
        h.head = 1;

        h.redo();                   // --head -> 0, get(0) == a2, stale -> dispose + remove

        assertEquals(0, a2.redoCount);
        assertEquals(1, a2.disposeCount);
        assertEquals(1, h.history.size());
        assertSame(a1, h.history.getFirst());
        assertEquals(0, h.head);
    }

    // --- removeAction -------------------------------------------------------

    @Test
    public void removeActionDropsTheOldestEntryWithoutDisposingIt() {
        EditorHistory h = new EditorHistory();
        FakeAction a1 = new FakeAction("a1");
        FakeAction a2 = new FakeAction("a2");
        FakeAction a3 = new FakeAction("a3");
        h.addAction(a1);
        h.addAction(a2);
        h.addAction(a3);            // history == [a3, a2, a1]

        h.removeAction();           // pollLast() removes a1

        assertEquals(2, h.history.size());
        assertFalse(h.history.contains(a1));
        assertSame(a3, h.history.getFirst());
        assertSame(a2, h.history.getLast());
        assertEquals(0, a1.disposeCount);   // pollLast does not dispose
    }

    @Test
    public void removeActionOnAnEmptyHistoryIsAHarmlessNoOp() {
        EditorHistory h = new EditorHistory();
        h.removeAction();           // pollLast() on empty returns null, no throw
        assertTrue(h.history.isEmpty());
    }

    // --- dispose ------------------------------------------------------------

    @Test
    public void disposeReleasesEveryActionAndClearsTheHistory() {
        EditorHistory h = new EditorHistory();
        FakeAction a1 = new FakeAction("a1");
        FakeAction a2 = new FakeAction("a2");
        h.addAction(a1);
        h.addAction(a2);

        h.dispose();

        assertEquals(1, a1.disposeCount);
        assertEquals(1, a2.disposeCount);
        assertTrue(h.history.isEmpty());
    }

    @Test
    public void disposeOnAnEmptyHistoryIsAHarmlessNoOp() {
        EditorHistory h = new EditorHistory();
        h.dispose();
        assertTrue(h.history.isEmpty());
    }

    // --- isUndoEnabled: every branch / boundary ----------------------------

    @Test
    public void undoIsDisabledOnAFreshHistory() {
        assertFalse(new EditorHistory().isUndoEnabled());
    }

    @Test
    public void undoStaysDisabledAfterAddingBecauseHeadIsNegative() {
        // Characterization: adding actions never enables undo through the API.
        EditorHistory h = new EditorHistory();
        h.addAction(new FakeAction("a1"));
        assertFalse(h.isUndoEnabled());
    }

    @Test
    public void undoIsEnabledWhenHeadIsInsideTheHistoryRange() {
        EditorHistory h = new EditorHistory();
        h.addAction(new FakeAction("a1"));
        h.head = 0;                 // 0 <= head < size(1)
        assertTrue(h.isUndoEnabled());
    }

    @Test
    public void undoIsDisabledWhenHeadEqualsTheSizeBoundary() {
        EditorHistory h = new EditorHistory();
        h.addAction(new FakeAction("a1"));
        h.head = 1;                 // head < size is false at head == size
        assertFalse(h.isUndoEnabled());
    }

    // --- isRedoEnabled: every branch / boundary ----------------------------

    @Test
    public void redoIsDisabledOnAFreshHistory() {
        assertFalse(new EditorHistory().isRedoEnabled());
    }

    @Test
    public void redoStaysDisabledAfterAddingBecauseHeadIsNotPositive() {
        EditorHistory h = new EditorHistory();
        h.addAction(new FakeAction("a1"));
        assertFalse(h.isRedoEnabled());
    }

    @Test
    public void redoIsDisabledAtTheHeadEqualsZeroBoundary() {
        EditorHistory h = new EditorHistory();
        h.addAction(new FakeAction("a1"));
        h.head = 0;                 // head > 0 is false
        assertFalse(h.isRedoEnabled());
    }

    @Test
    public void redoIsEnabledWhenHeadIsPositiveAndWithinTheSize() {
        EditorHistory h = new EditorHistory();
        h.addAction(new FakeAction("a1"));
        h.addAction(new FakeAction("a2"));
        h.head = 1;                 // 0 < head <= size(2)
        assertTrue(h.isRedoEnabled());
    }

    @Test
    public void redoIsEnabledWhenHeadEqualsTheSizeBoundary() {
        EditorHistory h = new EditorHistory();
        h.addAction(new FakeAction("a1"));
        h.addAction(new FakeAction("a2"));
        h.head = 2;                 // head <= size is true at head == size
        assertTrue(h.isRedoEnabled());
    }

    @Test
    public void redoIsDisabledWhenHeadExceedsTheSize() {
        EditorHistory h = new EditorHistory();
        h.addAction(new FakeAction("a1"));
        h.addAction(new FakeAction("a2"));
        h.head = 3;                 // head <= size is false
        assertFalse(h.isRedoEnabled());
    }
}
