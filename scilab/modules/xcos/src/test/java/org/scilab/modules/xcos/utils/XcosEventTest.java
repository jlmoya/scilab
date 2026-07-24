/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Scilab test coverage
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 *
 */

package org.scilab.modules.xcos.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for the {@link XcosEvent} constant holder.
 */
public class XcosEventTest {

    @Test
    public void updateBlockConstantValue() {
        assertEquals("updateBlock", XcosEvent.UPDATE_BLOCK);
    }

    @Test
    public void blockConstantValue() {
        assertEquals("block", XcosEvent.BLOCK);
    }

    @Test
    public void constantsAreDistinct() {
        assertNotEquals(XcosEvent.UPDATE_BLOCK, XcosEvent.BLOCK);
    }

    @Test
    public void classIsFinal() {
        assertTrue(Modifier.isFinal(XcosEvent.class.getModifiers()));
    }

    /**
     * Both exposed constants must be {@code public static final String}, since
     * they are the stable event/property keys other modules compare against.
     */
    @Test
    public void constantsArePublicStaticFinalStrings() throws NoSuchFieldException {
        for (String fieldName : new String[] {"UPDATE_BLOCK", "BLOCK"}) {
            Field field = XcosEvent.class.getField(fieldName);
            int modifiers = field.getModifiers();
            assertTrue(Modifier.isPublic(modifiers), fieldName + " must be public");
            assertTrue(Modifier.isStatic(modifiers), fieldName + " must be static");
            assertTrue(Modifier.isFinal(modifiers), fieldName + " must be final");
            assertEquals(String.class, field.getType(), fieldName + " must be a String");
        }
    }
}
