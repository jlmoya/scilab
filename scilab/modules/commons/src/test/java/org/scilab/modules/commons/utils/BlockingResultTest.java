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

package org.scilab.modules.commons.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link BlockingResult}, the wait/notify hand-off used by
 * modal dialogs.
 *
 * <p>{@code getResult()} parks in {@code Object.wait()}, so each test spins a consumer
 * thread and only calls {@code setResult()} once the consumer is observably WAITING —
 * otherwise the single {@code notify()} would be lost. Every assertion runs on the main
 * thread after {@code join()} (an assertion thrown inside the consumer would otherwise be
 * silently swallowed), and the whole hand-off is wrapped in a preemptive timeout so a
 * regression that never notifies fails fast instead of hanging the suite.
 */
public class BlockingResultTest {

    @Test
    public void getResultReturnsTheValuePassedToSetResult() {
        final String[] got = new String[1];
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            BlockingResult<String> br = new BlockingResult<>();
            Thread consumer = new Thread(() -> got[0] = br.getResult());
            consumer.start();
            awaitWaiting(consumer);
            br.setResult("hello");
            consumer.join();
        });
        assertEquals("hello", got[0]);
    }

    @Test
    public void carriesItsGenericTypeThrough() {
        final Integer[] got = new Integer[1];
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            BlockingResult<Integer> br = new BlockingResult<>();
            Thread consumer = new Thread(() -> got[0] = br.getResult());
            consumer.start();
            awaitWaiting(consumer);
            br.setResult(42);
            consumer.join();
        });
        assertEquals(Integer.valueOf(42), got[0]);
    }

    @Test
    public void deliversANullResult() {
        final Object[] got = {new Object()};
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            BlockingResult<String> br = new BlockingResult<>();
            Thread consumer = new Thread(() -> got[0] = br.getResult());
            consumer.start();
            awaitWaiting(consumer);
            br.setResult(null);
            consumer.join();
        });
        assertNull(got[0]);
    }

    /**
     * Spin until the consumer has actually parked inside {@code getResult()}'s
     * {@code lock.wait()}. {@code setResult()}'s {@code notify()} is lost if it runs
     * before the wait, so the hand-off must observe {@link Thread.State#WAITING} first.
     */
    private static void awaitWaiting(Thread t) {
        while (t.getState() != Thread.State.WAITING) {
            Thread.onSpinWait();
        }
    }
}
