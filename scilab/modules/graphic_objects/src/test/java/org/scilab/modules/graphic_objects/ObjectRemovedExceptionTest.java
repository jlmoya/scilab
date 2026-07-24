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

package org.scilab.modules.graphic_objects;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Hermetic unit tests for {@link ObjectRemovedException}, a checked exception
 * carrying the id of the object that was deleted from the graphic model.
 */
public class ObjectRemovedExceptionTest {

    @Test
    public void isACheckedException() {
        ObjectRemovedException ex = new ObjectRemovedException(1);
        assertTrue(ex instanceof Exception);
        // It is a checked exception: not on the RuntimeException branch.
        assertFalse(RuntimeException.class.isAssignableFrom(ObjectRemovedException.class));
    }

    /**
     * The message embeds the id via {@code Integer.toString()}. Note the
     * message concatenates the id directly against "has" with no separating
     * space; this test pins the exact current wording.
     */
    @Test
    public void messageEmbedsObjectId() {
        ObjectRemovedException ex = new ObjectRemovedException(42);
        assertEquals("ObjectRemoved Exception: Object 42has been deleted from model",
                     ex.getMessage());
    }

    @Test
    public void messageReflectsDifferentIds() {
        assertEquals("ObjectRemoved Exception: Object 0has been deleted from model",
                     new ObjectRemovedException(0).getMessage());
        assertEquals("ObjectRemoved Exception: Object -7has been deleted from model",
                     new ObjectRemovedException(-7).getMessage());
    }

    @Test
    public void canBeThrownAndCaught() {
        ObjectRemovedException thrown = assertThrows(ObjectRemovedException.class, () -> {
            throw new ObjectRemovedException(99);
        });
        assertTrue(thrown.getMessage().contains("99"));
    }

    /**
     * Characterization: the message builder calls {@code objectId.toString()}
     * unconditionally, so constructing with a null id and asking for the
     * message throws a NullPointerException.
     */
    @Test
    public void nullIdMessageThrows() {
        ObjectRemovedException ex = new ObjectRemovedException(null);
        assertThrows(NullPointerException.class, ex::getMessage);
    }
}
