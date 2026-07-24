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

import java.util.Date;

import org.junit.jupiter.api.Test;

/**
 * Tests {@link News}: a news-item value object.
 *
 * NOTE: only the six-argument constructor is exercised. The no-argument
 * {@code new News()} constructor dereferences {@code ScilabConstants.SCI}, whose static
 * initializer performs a native (JNI) call and is therefore NOT hermetic — it is
 * deliberately left untested here.
 */
public class NewsTest {

    @Test
    public void sixArgConstructorStoresEveryField() {
        Date date = new Date(1_000_000L);
        NewsMediaContent media = new NewsMediaContent("u", "10", "10");
        News news = new News("Title", date, "Desc", "Content", media, "https://link");

        assertEquals("Title", news.getTitle());
        assertSame(date, news.getDate());
        assertEquals("Desc", news.getDescription());
        assertEquals("Content", news.getContent());
        assertSame(media, news.getMediaContent());
        assertEquals("https://link", news.getLink());
    }

    @Test
    public void nullArgumentsArePreserved() {
        News news = new News(null, null, null, null, null, null);

        assertNull(news.getTitle());
        assertNull(news.getDate());
        assertNull(news.getDescription());
        assertNull(news.getContent());
        assertNull(news.getMediaContent());
        assertNull(news.getLink());
    }

    @Test
    public void fieldsAreNotCrossWired() {
        News news = new News("t", new Date(0L), "d", "c", null, "l");
        assertEquals("t", news.getTitle());
        assertEquals("d", news.getDescription());
        assertEquals("c", news.getContent());
        assertEquals("l", news.getLink());
    }
}
