/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Scilab
 *
 * Hermetic JUnit 6 unit tests for the ui_data module.
 * This file is hereby licensed under the terms of the GNU GPL v2.0.
 */

package org.scilab.modules.ui_data.newsfeed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.EventObject;

import org.junit.jupiter.api.Test;

/**
 * Tests {@link NewsFeedEvent}: a plain {@link EventObject} carrying an int event type.
 */
public class NewsFeedEventTest {

    @Test
    public void constantsHaveExpectedDistinctValues() {
        assertEquals(1, NewsFeedEvent.NEWS_CHANGED);
        assertEquals(2, NewsFeedEvent.NEWSFEED_UPDATED);
        assertEquals(3, NewsFeedEvent.NEWSFEED_ERROR);
    }

    @Test
    public void constructorStoresSourceAndEventType() {
        Object source = new Object();
        NewsFeedEvent event = new NewsFeedEvent(source, NewsFeedEvent.NEWS_CHANGED);

        assertSame(source, event.getSource(), "getSource() must return the EventObject source");
        assertEquals(NewsFeedEvent.NEWS_CHANGED, event.getEventType());
    }

    @Test
    public void isAnEventObject() {
        NewsFeedEvent event = new NewsFeedEvent("src", NewsFeedEvent.NEWSFEED_UPDATED);
        assertNotNull(event);
        assertEquals(NewsFeedEvent.NEWSFEED_UPDATED, event.getEventType());
        // EventObject contract: the source is preserved as-is.
        assertEquals("src", event.getSource());
    }

    @Test
    public void arbitraryEventTypeIsStoredVerbatim() {
        // The class does not validate the type against the known constants.
        NewsFeedEvent event = new NewsFeedEvent("s", 999);
        assertEquals(999, event.getEventType());
    }
}
