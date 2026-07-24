/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Scilab
 *
 * Hermetic JUnit 6 unit tests for the ui_data module.
 * This file is hereby licensed under the terms of the GNU GPL v2.0.
 */

package org.scilab.modules.ui_data.newsfeed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests {@link NewsFeedErrorEvent}: a {@link NewsFeedEvent} subclass whose event type is
 * always {@link NewsFeedEvent#NEWSFEED_ERROR} and which carries an error message.
 */
public class NewsFeedErrorEventTest {

    @Test
    public void eventTypeIsAlwaysNewsFeedError() {
        NewsFeedErrorEvent event = new NewsFeedErrorEvent(new Object(), "boom");
        assertEquals(NewsFeedEvent.NEWSFEED_ERROR, event.getEventType());
    }

    @Test
    public void constructorStoresSourceAndMessage() {
        Object source = new Object();
        NewsFeedErrorEvent event = new NewsFeedErrorEvent(source, "network down");

        assertSame(source, event.getSource());
        assertEquals("network down", event.getErrorMessage());
    }

    @Test
    public void isASubtypeOfNewsFeedEvent() {
        NewsFeedErrorEvent event = new NewsFeedErrorEvent("s", "err");
        assertTrue(event instanceof NewsFeedEvent);
    }

    @Test
    public void nullMessageIsPreserved() {
        NewsFeedErrorEvent event = new NewsFeedErrorEvent("s", null);
        assertNull(event.getErrorMessage());
        assertEquals(NewsFeedEvent.NEWSFEED_ERROR, event.getEventType());
    }
}
