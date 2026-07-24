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

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hermetic unit tests for {@link KeywordAdapter} and its two convenience
 * subclasses, {@link KeywordAdapter.MouseClickedAdapter} and
 * {@link KeywordAdapter.MouseOverAdapter}. Verifies the {@code type} plumbing and
 * that the concrete adapters bind the matching {@link KeywordListener} trigger.
 */
public class KeywordAdapterTest {

    /** A trivial concrete adapter that records the last event it caught. */
    private static final class RecordingAdapter extends KeywordAdapter {
        final AtomicReference<KeywordEvent> last = new AtomicReference<KeywordEvent>();

        RecordingAdapter(int type) {
            super(type);
        }

        public void caughtKeyword(KeywordEvent e) {
            last.set(e);
        }
    }

    @Test
    public void getTypeReturnsConstructorType() {
        KeywordAdapter adapter = new RecordingAdapter(42);
        assertEquals(42, adapter.getType());
    }

    @Test
    public void adapterIsAKeywordListener() {
        KeywordAdapter adapter = new RecordingAdapter(KeywordListener.ONMOUSECLICKED);
        assertTrue(adapter instanceof KeywordListener,
                   "KeywordAdapter implements KeywordListener");
        assertEquals(KeywordListener.ONMOUSECLICKED, adapter.getType());
    }

    @Test
    public void caughtKeywordIsDispatchedToSubclass() {
        RecordingAdapter adapter = new RecordingAdapter(KeywordListener.ONMOUSEOVER);
        KeywordEvent event = new KeywordEvent(new Object(), null, ScilabLexerConstants.MACROS, 7, 3);
        adapter.caughtKeyword(event);
        assertSame(event, adapter.last.get());
    }

    @Test
    public void mouseClickedAdapterBindsOnMouseClicked() {
        KeywordAdapter.MouseClickedAdapter adapter = new KeywordAdapter.MouseClickedAdapter() {
            public void caughtKeyword(KeywordEvent e) {
                // no-op
            }
        };
        assertEquals(KeywordListener.ONMOUSECLICKED, adapter.getType());
        assertTrue(adapter instanceof KeywordAdapter);
    }

    @Test
    public void mouseOverAdapterBindsOnMouseOver() {
        KeywordAdapter.MouseOverAdapter adapter = new KeywordAdapter.MouseOverAdapter() {
            public void caughtKeyword(KeywordEvent e) {
                // no-op
            }
        };
        assertEquals(KeywordListener.ONMOUSEOVER, adapter.getType());
        assertTrue(adapter instanceof KeywordAdapter);
    }

    @Test
    public void theTwoConvenienceAdaptersReportDifferentTypes() {
        KeywordAdapter clicked = new KeywordAdapter.MouseClickedAdapter() {
            public void caughtKeyword(KeywordEvent e) { }
        };
        KeywordAdapter over = new KeywordAdapter.MouseOverAdapter() {
            public void caughtKeyword(KeywordEvent e) { }
        };
        assertTrue(clicked.getType() != over.getType(),
                   "the click and hover adapters must not share a trigger type");
    }
}
