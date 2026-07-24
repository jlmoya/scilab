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

package org.scilab.modules.scinotes;

import org.junit.jupiter.api.Test;

import java.util.EventListener;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hermetic unit tests for the {@link KeywordListener} interface contract: its two
 * mouse-trigger constants and the {@code getType} / {@code caughtKeyword} callback
 * shape (exercised through a minimal in-test implementation).
 */
public class KeywordListenerTest {

    @Test
    public void triggerConstantsHaveExpectedValues() {
        assertEquals(1, KeywordListener.ONMOUSECLICKED);
        assertEquals(2, KeywordListener.ONMOUSEOVER);
    }

    @Test
    public void triggerConstantsAreDistinct() {
        assertNotEquals(KeywordListener.ONMOUSECLICKED, KeywordListener.ONMOUSEOVER);
    }

    @Test
    public void extendsEventListener() {
        assertTrue(EventListener.class.isAssignableFrom(KeywordListener.class),
                   "KeywordListener must be a java.util.EventListener");
    }

    @Test
    public void implementationReceivesTheCaughtKeyword() {
        final AtomicReference<KeywordEvent> received = new AtomicReference<KeywordEvent>();
        KeywordListener listener = new KeywordListener() {
            public void caughtKeyword(KeywordEvent e) {
                received.set(e);
            }

            public int getType() {
                return ONMOUSEOVER;
            }
        };

        assertEquals(KeywordListener.ONMOUSEOVER, listener.getType());

        KeywordEvent event = new KeywordEvent(new Object(), null, ScilabLexerConstants.ID, 0, 4);
        listener.caughtKeyword(event);
        assertSame(event, received.get(), "the listener received exactly the dispatched event");
    }
}
