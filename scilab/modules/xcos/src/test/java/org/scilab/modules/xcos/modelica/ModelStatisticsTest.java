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

package org.scilab.modules.xcos.modelica;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link ModelStatistics}, the mutable counter bag that
 * accumulates the Modelica compiler's model statistics (equations, parameters,
 * variables, states, i/o) and notifies Swing {@link ChangeListener}s.
 *
 * <p>The class depends only on {@code javax.swing.event.*} (an
 * {@code EventListenerList} plus a lazily-created {@link ChangeEvent}); it never
 * touches the Scilab native runtime and needs no display, so its full behaviour
 * is exercised directly here.
 *
 * <p>These tests pin: zero-initialised getters, the two increment forms
 * (no-arg == +1 and explicit signed delta), field independence, the derived
 * {@code getUnknowns()} sum, {@code isEmpty()} / {@code clear()}, and the
 * listener protocol (fire, lazy single-event reuse, multiple/duplicate
 * listeners, removal). One clearly-named defect-characterization test documents
 * that {@code setRelaxedVariables(long)} writes an unrelated,
 * getter-less {@code expectedRelaxedVariables} field rather than the
 * {@code relaxedVariables} field that {@code getRelaxedVariables()} reads.
 */
public class ModelStatisticsTest {

    /* ------------------------------------------------------------------ */
    /* Construction / defaults                                            */
    /* ------------------------------------------------------------------ */

    @Test
    public void freshInstanceHasEveryCounterAtZero() {
        ModelStatistics s = new ModelStatistics();

        assertEquals(0L, s.getEquations());
        assertEquals(0L, s.getFixedParameters());
        assertEquals(0L, s.getRelaxedParameters());
        assertEquals(0L, s.getFixedVariables());
        assertEquals(0L, s.getRelaxedVariables());
        assertEquals(0L, s.getDiscreteStates());
        assertEquals(0L, s.getInputs());
        assertEquals(0L, s.getOutputs());
        assertEquals(0L, s.getDerivativeStates());
        assertEquals(0L, s.getUnknowns());
    }

    @Test
    public void freshInstanceIsEmpty() {
        assertTrue(new ModelStatistics().isEmpty());
    }

    /* ------------------------------------------------------------------ */
    /* Increment behaviour                                                */
    /* ------------------------------------------------------------------ */

    @Test
    public void eachNoArgIncrementAddsExactlyOneToItsOwnFieldOnly() {
        ModelStatistics s = new ModelStatistics();

        s.incEquations();
        s.incFixedParameters();
        s.incRelaxedParameters();
        s.incFixedVariables();
        s.incRelaxedVariables();
        s.incDiscreteStates();
        s.incInputs();
        s.incOutputs();
        s.incDerivativeStates();

        // Every field moved to exactly 1 => the nine increments are wired to
        // nine distinct fields (no cross-talk).
        assertEquals(1L, s.getEquations());
        assertEquals(1L, s.getFixedParameters());
        assertEquals(1L, s.getRelaxedParameters());
        assertEquals(1L, s.getFixedVariables());
        assertEquals(1L, s.getRelaxedVariables());
        assertEquals(1L, s.getDiscreteStates());
        assertEquals(1L, s.getInputs());
        assertEquals(1L, s.getOutputs());
        assertEquals(1L, s.getDerivativeStates());
        // unknowns == relaxedParameters + relaxedVariables == 1 + 1
        assertEquals(2L, s.getUnknowns());
    }

    @Test
    public void explicitIncrementAddsTheGivenAmountAndAccumulates() {
        ModelStatistics s = new ModelStatistics();

        s.incEquations(5);
        assertEquals(5L, s.getEquations());
        s.incEquations(3);
        assertEquals(8L, s.getEquations());
    }

    @Test
    public void incrementAcceptsNegativeDeltas() {
        ModelStatistics s = new ModelStatistics();

        s.incInputs(10);
        s.incInputs(-4);
        assertEquals(6L, s.getInputs());
    }

    @Test
    public void explicitIncrementReachesEveryFieldIndependently() {
        ModelStatistics s = new ModelStatistics();

        s.incEquations(1);
        s.incFixedParameters(2);
        s.incRelaxedParameters(3);
        s.incFixedVariables(4);
        s.incRelaxedVariables(5);
        s.incDiscreteStates(6);
        s.incInputs(7);
        s.incOutputs(8);
        s.incDerivativeStates(9);

        assertEquals(1L, s.getEquations());
        assertEquals(2L, s.getFixedParameters());
        assertEquals(3L, s.getRelaxedParameters());
        assertEquals(4L, s.getFixedVariables());
        assertEquals(5L, s.getRelaxedVariables());
        assertEquals(6L, s.getDiscreteStates());
        assertEquals(7L, s.getInputs());
        assertEquals(8L, s.getOutputs());
        assertEquals(9L, s.getDerivativeStates());
        assertEquals(3L + 5L, s.getUnknowns());
    }

    @Test
    public void incrementOverflowWrapsAroundWithNoSaturationGuard_characterization() {
        // The counters are plain `long +=`; there is no overflow protection, so
        // MAX_VALUE + 1 wraps to MIN_VALUE. Documents the current arithmetic.
        ModelStatistics s = new ModelStatistics();
        s.incEquations(Long.MAX_VALUE);
        s.incEquations(1);
        assertEquals(Long.MIN_VALUE, s.getEquations());
    }

    /* ------------------------------------------------------------------ */
    /* Derived / setter behaviour                                         */
    /* ------------------------------------------------------------------ */

    @Test
    public void unknownsIsTheSumOfRelaxedParametersAndRelaxedVariables() {
        ModelStatistics s = new ModelStatistics();
        s.incRelaxedParameters(30);
        s.incRelaxedVariables(12);
        assertEquals(42L, s.getUnknowns());
    }

    @Test
    public void setEquationsAssignsAnAbsoluteValueObservableEverywhere() {
        ModelStatistics s = new ModelStatistics();

        s.setEquations(7);
        assertEquals(7L, s.getEquations());
        assertFalse(s.isEmpty());

        // it is an assignment, not an accumulation
        s.setEquations(2);
        assertEquals(2L, s.getEquations());

        s.setEquations(0);
        assertEquals(0L, s.getEquations());
        assertTrue(s.isEmpty());
    }

    @Test
    public void setRelaxedVariablesWritesTheGetterlessExpectedField_notRelaxedVariables_defectCharacterization() {
        // DEFECT CHARACTERIZATION: despite its name, setRelaxedVariables(long)
        // assigns the private `expectedRelaxedVariables` field (which has no
        // getter and participates in neither isEmpty() nor getUnknowns()),
        // *not* the `relaxedVariables` field that getRelaxedVariables() reads.
        // The write is therefore invisible through every public accessor.
        ModelStatistics s = new ModelStatistics();

        s.setRelaxedVariables(100);

        assertEquals(0L, s.getRelaxedVariables(), "getRelaxedVariables() reads a different field");
        assertEquals(0L, s.getUnknowns(), "unknowns is unaffected by setRelaxedVariables");
        assertTrue(s.isEmpty(), "isEmpty() does not consider expectedRelaxedVariables");
    }

    @Test
    public void relaxedVariablesFieldIsMovedByIncrementNotBySetter() {
        // Contrast with the defect above: the increment path DOES reach the
        // field that the getter reports.
        ModelStatistics s = new ModelStatistics();
        s.setRelaxedVariables(100);   // no observable effect (see defect test)
        s.incRelaxedVariables(3);     // this is what getRelaxedVariables() sees
        assertEquals(3L, s.getRelaxedVariables());
    }

    /* ------------------------------------------------------------------ */
    /* isEmpty / clear                                                    */
    /* ------------------------------------------------------------------ */

    @Test
    public void anyOfTheNineTrackedCountersFlipsIsEmptyToFalse() {
        List<Consumer<ModelStatistics>> increments = List.of(
                    ModelStatistics::incEquations,
                    ModelStatistics::incFixedParameters,
                    ModelStatistics::incRelaxedParameters,
                    ModelStatistics::incFixedVariables,
                    ModelStatistics::incRelaxedVariables,
                    ModelStatistics::incDiscreteStates,
                    ModelStatistics::incInputs,
                    ModelStatistics::incOutputs,
                    ModelStatistics::incDerivativeStates);

        for (Consumer<ModelStatistics> inc : increments) {
            ModelStatistics s = new ModelStatistics();
            assertTrue(s.isEmpty());
            inc.accept(s);
            assertFalse(s.isEmpty(), "a single non-zero counter must make isEmpty() false");
        }
    }

    @Test
    public void clearResetsEveryTrackedCounterAndRestoresEmptiness() {
        ModelStatistics s = new ModelStatistics();
        s.incEquations(1);
        s.incFixedParameters(2);
        s.incRelaxedParameters(3);
        s.incFixedVariables(4);
        s.incRelaxedVariables(5);
        s.incDiscreteStates(6);
        s.incInputs(7);
        s.incOutputs(8);
        s.incDerivativeStates(9);
        assertFalse(s.isEmpty());

        s.clear();

        assertTrue(s.isEmpty());
        assertEquals(0L, s.getEquations());
        assertEquals(0L, s.getFixedParameters());
        assertEquals(0L, s.getRelaxedParameters());
        assertEquals(0L, s.getFixedVariables());
        assertEquals(0L, s.getRelaxedVariables());
        assertEquals(0L, s.getDiscreteStates());
        assertEquals(0L, s.getInputs());
        assertEquals(0L, s.getOutputs());
        assertEquals(0L, s.getDerivativeStates());
        assertEquals(0L, s.getUnknowns());
    }

    @Test
    public void clearOnAFreshInstanceIsANoOp() {
        ModelStatistics s = new ModelStatistics();
        s.clear();
        assertTrue(s.isEmpty());
    }

    /* ------------------------------------------------------------------ */
    /* ChangeListener support                                             */
    /* ------------------------------------------------------------------ */

    @Test
    public void fireChangeWithNoListenersDoesNotThrow() {
        ModelStatistics s = new ModelStatistics();
        assertDoesNotThrow(s::fireChange);
    }

    @Test
    public void fireChangeNotifiesARegisteredListenerWithThisAsTheEventSource() {
        ModelStatistics s = new ModelStatistics();
        List<ChangeEvent> seen = new ArrayList<>();
        ChangeListener l = seen::add;

        s.addChangeListener(l);
        s.fireChange();

        assertEquals(1, seen.size());
        assertNotNull(seen.get(0));
        assertSame(s, seen.get(0).getSource());
    }

    @Test
    public void fireChangeReusesTheSameLazilyCreatedEventInstanceAcrossFires() {
        ModelStatistics s = new ModelStatistics();
        List<ChangeEvent> seen = new ArrayList<>();
        s.addChangeListener(seen::add);

        s.fireChange();
        s.fireChange();

        assertEquals(2, seen.size());
        // the event is created once (lazily) and cached in a field, so both
        // notifications carry the very same object.
        assertSame(seen.get(0), seen.get(1));
    }

    @Test
    public void everyRegisteredListenerIsNotifiedOnFire() {
        ModelStatistics s = new ModelStatistics();
        int[] a = {0};
        int[] b = {0};
        s.addChangeListener(e -> a[0]++);
        s.addChangeListener(e -> b[0]++);

        s.fireChange();

        assertEquals(1, a[0]);
        assertEquals(1, b[0]);
    }

    @Test
    public void aListenerAddedTwiceIsNotifiedTwicePerFire() {
        // EventListenerList keeps duplicates, so the same instance fires once
        // per registration.
        ModelStatistics s = new ModelStatistics();
        int[] count = {0};
        ChangeListener l = e -> count[0]++;

        s.addChangeListener(l);
        s.addChangeListener(l);
        s.fireChange();

        assertEquals(2, count[0]);
    }

    @Test
    public void removedListenerIsNoLongerNotified() {
        ModelStatistics s = new ModelStatistics();
        int[] count = {0};
        ChangeListener l = e -> count[0]++;

        s.addChangeListener(l);
        s.fireChange();
        assertEquals(1, count[0]);

        s.removeChangeListener(l);
        s.fireChange();
        assertEquals(1, count[0], "a removed listener must not receive further events");
    }

    @Test
    public void removingANeverRegisteredListenerIsHarmless() {
        ModelStatistics s = new ModelStatistics();
        int[] count = {0};
        ChangeListener registered = e -> count[0]++;
        s.addChangeListener(registered);

        // removing a stranger neither throws nor disturbs the real listener
        assertDoesNotThrow(() -> s.removeChangeListener(e -> { }));
        s.fireChange();
        assertEquals(1, count[0]);
    }

    @Test
    public void firingAChangeEventDoesNotMutateTheStatistics() {
        ModelStatistics s = new ModelStatistics();
        s.addChangeListener(e -> { });
        s.fireChange();
        assertTrue(s.isEmpty());
    }
}
