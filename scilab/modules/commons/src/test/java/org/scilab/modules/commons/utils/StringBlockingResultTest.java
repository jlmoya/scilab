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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link StringBlockingResult}, the lazily-created singleton
 * specialisation of {@link BlockingResult} over {@code String}.
 */
public class StringBlockingResultTest {

    @Test
    public void getInstanceReturnsANonNullSingleton() {
        StringBlockingResult a = StringBlockingResult.getInstance();
        StringBlockingResult b = StringBlockingResult.getInstance();
        assertNotNull(a);
        assertSame(a, b);
    }

    @Test
    public void theSingletonIsABlockingResult() {
        assertInstanceOf(BlockingResult.class, StringBlockingResult.getInstance());
    }

    @Test
    public void theInheritedHandOffDeliversAString() {
        final String[] got = new String[1];
        final StringBlockingResult sbr = StringBlockingResult.getInstance();
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            Thread consumer = new Thread(() -> got[0] = sbr.getResult());
            consumer.start();
            while (consumer.getState() != Thread.State.WAITING) {
                Thread.onSpinWait();
            }
            sbr.setResult("user-input");
            consumer.join();
        });
        assertEquals("user-input", got[0]);
    }
}
