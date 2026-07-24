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

import java.util.EventObject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hermetic unit tests for {@link KeywordEvent}, the immutable value object carried
 * when a keyword is hit. Only the constructor / accessor contract is exercised (the
 * {@code toString} path is intentionally left out, as it casts the source to a Swing
 * editor pane which is out of scope for a hermetic test).
 */
public class KeywordEventTest {

    @Test
    public void accessorsReturnConstructorArguments() {
        Object source = new Object();
        EventObject inner = new EventObject(source);
        KeywordEvent e = new KeywordEvent(source, inner, ScilabLexerConstants.MACROS, 12, 5);

        assertEquals(ScilabLexerConstants.MACROS, e.getType());
        assertEquals(12, e.getStart());
        assertEquals(5, e.getLength());
        assertSame(source, e.getSource(), "getSource returns the EventObject source");
        assertSame(inner, e.getEvent(), "getEvent returns the wrapped triggering event");
    }

    @Test
    public void isAnEventObject() {
        KeywordEvent e = new KeywordEvent(new Object(), null, 0, 0, 0);
        assertTrue(e instanceof EventObject, "KeywordEvent extends java.util.EventObject");
    }

    @Test
    public void wrappedEventMayBeNull() {
        KeywordEvent e = new KeywordEvent(new Object(), null, ScilabLexerConstants.URL, 3, 4);
        assertNull(e.getEvent());
        assertEquals(ScilabLexerConstants.URL, e.getType());
    }

    @Test
    public void storesZeroAndNegativeValuesVerbatim() {
        // The constructor performs no validation on type / start / length.
        KeywordEvent e = new KeywordEvent(new Object(), null, -7, 0, 0);
        assertEquals(-7, e.getType());
        assertEquals(0, e.getStart());
        assertEquals(0, e.getLength());
    }

    @Test
    public void distinctFieldsAreNotConflated() {
        KeywordEvent e = new KeywordEvent(new Object(), null, 1, 100, 200);
        assertEquals(100, e.getStart());
        assertEquals(200, e.getLength());
        assertEquals(1, e.getType());
    }

    @Test
    public void nullSourceIsRejectedByEventObject() {
        // java.util.EventObject forbids a null source; the super() call must reject it.
        assertThrows(IllegalArgumentException.class,
                     () -> new KeywordEvent(null, null, 0, 0, 0));
    }
}
