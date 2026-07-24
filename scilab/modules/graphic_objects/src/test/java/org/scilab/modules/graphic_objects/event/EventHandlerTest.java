/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Scilab Enterprises
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 *
 */

package org.scilab.modules.graphic_objects.event;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import org.scilab.modules.graphic_objects.graphicObject.GraphicObject.UpdateStatus;

/**
 * Hermetic unit tests for {@link EventHandler}: a plain holder of an event
 * handler command string and an enabled flag, each with change detection.
 */
public class EventHandlerTest {

    @Test
    public void defaults() {
        EventHandler eh = new EventHandler();
        assertEquals("", eh.getEventHandlerString());
        assertFalse(eh.getEventHandlerEnabled());
    }

    @Test
    public void enabledFlagChangeDetection() {
        EventHandler eh = new EventHandler();
        // Default is false, so setting false again is a no-op.
        assertEquals(UpdateStatus.NoChange, eh.setEventHandlerEnabled(false));
        assertEquals(UpdateStatus.Success, eh.setEventHandlerEnabled(true));
        assertTrue(eh.getEventHandlerEnabled());
        assertEquals(UpdateStatus.NoChange, eh.setEventHandlerEnabled(true));
    }

    @Test
    public void handlerStringChangeDetection() {
        EventHandler eh = new EventHandler();
        assertEquals(UpdateStatus.NoChange, eh.setEventHandlerString(""));
        assertEquals(UpdateStatus.Success, eh.setEventHandlerString("myHandler"));
        assertEquals("myHandler", eh.getEventHandlerString());
        assertEquals(UpdateStatus.NoChange, eh.setEventHandlerString("myHandler"));
    }

    @Test
    public void copyConstructorDuplicatesState() {
        EventHandler src = new EventHandler();
        src.setEventHandlerString("h");
        src.setEventHandlerEnabled(true);

        EventHandler copy = new EventHandler(src);
        assertEquals("h", copy.getEventHandlerString());
        assertTrue(copy.getEventHandlerEnabled());

        // The copy is independent from the source afterwards.
        copy.setEventHandlerString("other");
        assertEquals("h", src.getEventHandlerString());
    }

    /**
     * Characterization: {@code setEventHandlerEnabled} dereferences its own
     * argument for the change-detection comparison, so a null argument throws
     * rather than being stored.
     */
    @Test
    public void setEnabledWithNullThrows() {
        EventHandler eh = new EventHandler();
        assertThrows(NullPointerException.class, () -> eh.setEventHandlerEnabled(null));
    }

    /**
     * Characterization: {@code setEventHandlerString} compares via
     * {@code this.eventHandler.compareTo(arg)}, so a null argument throws.
     */
    @Test
    public void setStringWithNullThrows() {
        EventHandler eh = new EventHandler();
        assertThrows(NullPointerException.class, () -> eh.setEventHandlerString(null));
    }
}
