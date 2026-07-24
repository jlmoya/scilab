/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Jose Moya
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 *
 */

package org.scilab.modules.action_binding.utils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link Signal}, the UID-keyed rendezvous used to make
 * a Java caller block until the interpreter side calls {@code notify(uid)}.
 *
 * Every test uses a distinct index so the shared static waiter map never leaks
 * state between tests, daemon threads so a stuck waiter can never block JVM
 * exit, and {@code assertTimeoutPreemptively} as a hard backstop.
 */
class SignalTest {

    private static Thread daemon(Runnable r, String name) {
        Thread t = new Thread(r, name);
        t.setDaemon(true);
        return t;
    }

    /** A notify() must release a thread blocked in the matching wait(). */
    @Test
    void notifyReleasesWaiter() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            final String key = "sig-round-trip";
            final AtomicBoolean released = new AtomicBoolean(false);
            final CountDownLatch aboutToWait = new CountDownLatch(1);

            Thread waiter = daemon(() -> {
                aboutToWait.countDown();
                Signal.wait(key);
                released.set(true);
            }, "waiter-round-trip");
            waiter.start();

            aboutToWait.await();
            Thread.sleep(200); // let the waiter actually enter Object.wait()
            assertFalse(released.get(), "waiter must still be blocked before notify()");
            assertTrue(waiter.isAlive(), "waiter thread must still be alive before notify()");

            Signal.notify(key);
            waiter.join(3000);
            assertFalse(waiter.isAlive(), "waiter should have returned after notify()");
            assertTrue(released.get(), "waiter should have run past Signal.wait()");
        });
    }

    /**
     * notify() for an index that has no waiter yet must not return: it spins
     * (retrying every 100 ms) until a waiter registers, then releases it.
     */
    @Test
    void notifyWaitsUntilWaiterAppears() {
        assertTimeoutPreemptively(Duration.ofSeconds(8), () -> {
            final String key = "sig-notify-first";
            final AtomicBoolean notifierDone = new AtomicBoolean(false);

            Thread notifier = daemon(() -> {
                Signal.notify(key);
                notifierDone.set(true);
            }, "notifier-first");
            notifier.start();

            Thread.sleep(300); // no waiter registered yet
            assertFalse(notifierDone.get(), "notify() must block while no waiter is registered");
            assertTrue(notifier.isAlive(), "notifier must still be spinning");

            final AtomicBoolean released = new AtomicBoolean(false);
            Thread waiter = daemon(() -> {
                Signal.wait(key);
                released.set(true);
            }, "waiter-late");
            waiter.start();

            notifier.join(4000);
            waiter.join(4000);
            assertFalse(notifier.isAlive(), "notifier should finish once a waiter appears");
            assertFalse(waiter.isAlive(), "the late waiter should have been released");
            assertTrue(notifierDone.get());
            assertTrue(released.get());
        });
    }

    /** notify() removes the index, so the same key can be reused afterwards. */
    @Test
    void keyIsReusableAfterRoundTrip() {
        assertTimeoutPreemptively(Duration.ofSeconds(6), () -> {
            final String key = "sig-reuse";
            for (int i = 0; i < 2; i++) {
                final AtomicBoolean released = new AtomicBoolean(false);
                final CountDownLatch aboutToWait = new CountDownLatch(1);
                Thread waiter = daemon(() -> {
                    aboutToWait.countDown();
                    Signal.wait(key);
                    released.set(true);
                }, "waiter-reuse-" + i);
                waiter.start();

                aboutToWait.await();
                Thread.sleep(150);
                Signal.notify(key);
                waiter.join(2500);
                assertFalse(waiter.isAlive(), "iteration " + i + ": waiter must return");
                assertTrue(released.get(), "iteration " + i + ": waiter must be released");
            }
        });
    }

    /** notify(a) must release only the waiter on index a, never one on index b. */
    @Test
    void notifyTargetsOnlyItsIndex() {
        assertTimeoutPreemptively(Duration.ofSeconds(6), () -> {
            final String a = "sig-a";
            final String b = "sig-b";
            final AtomicBoolean aReleased = new AtomicBoolean(false);
            final AtomicBoolean bReleased = new AtomicBoolean(false);
            final CountDownLatch aAbout = new CountDownLatch(1);
            final CountDownLatch bAbout = new CountDownLatch(1);

            Thread wa = daemon(() -> {
                aAbout.countDown();
                Signal.wait(a);
                aReleased.set(true);
            }, "waiter-a");
            Thread wb = daemon(() -> {
                bAbout.countDown();
                Signal.wait(b);
                bReleased.set(true);
            }, "waiter-b");
            wa.start();
            wb.start();

            aAbout.await();
            bAbout.await();
            Thread.sleep(200);

            Signal.notify(a);
            wa.join(2500);
            assertFalse(wa.isAlive(), "waiter on index a should be released");
            assertTrue(aReleased.get());

            Thread.sleep(150);
            assertTrue(wb.isAlive(), "waiter on index b must remain blocked after notify(a)");
            assertFalse(bReleased.get(), "waiter on index b must not have been released by notify(a)");

            // release the b waiter so no thread is left blocked
            Signal.notify(b);
            wb.join(2500);
            assertFalse(wb.isAlive());
            assertTrue(bReleased.get());
        });
    }
}
