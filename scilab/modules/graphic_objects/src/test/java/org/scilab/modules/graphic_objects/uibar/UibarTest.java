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

package org.scilab.modules.graphic_objects.uibar;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_UI_MESSAGE__;
import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_UI_MESSAGE_SIZE__;
import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_UI_VALUE__;

import org.scilab.modules.graphic_objects.graphicObject.GraphicObject.UpdateStatus;
import org.scilab.modules.graphic_objects.graphicObject.Visitor;

/**
 * Hermetic unit tests for the abstract {@link Uibar}. Because Uibar leaves
 * getType() abstract, the tests drive a minimal concrete subclass to exercise
 * the shared message/value state and property dispatch.
 */
public class UibarTest {

    /** Minimal concrete Uibar used purely to instantiate the abstract base. */
    private static final class TestBar extends Uibar {
        static final int TYPE_SENTINEL = 4242;
        @Override
        public Integer getType() {
            return TYPE_SENTINEL;
        }
    }

    @Test
    public void defaultsAreEmptyMessageAndZeroValue() {
        TestBar bar = new TestBar();
        assertArrayEquals(new String[] {""}, bar.getMessage());
        assertEquals(Integer.valueOf(0), bar.getValue());
    }

    @Test
    public void setMessageAlwaysSucceeds() {
        TestBar bar = new TestBar();
        String[] msg = {"loading", "please wait"};
        assertEquals(UpdateStatus.Success, bar.setMessage(msg));
        assertArrayEquals(msg, bar.getMessage());
        // No NoChange guard: re-setting the same array is still Success.
        assertEquals(UpdateStatus.Success, bar.setMessage(msg));
    }

    @Test
    public void setValueAlwaysSucceeds() {
        TestBar bar = new TestBar();
        assertEquals(UpdateStatus.Success, bar.setValue(75));
        assertEquals(Integer.valueOf(75), bar.getValue());
        assertEquals(UpdateStatus.Success, bar.setValue(75));
    }

    @Test
    public void propertyDispatchForMessageRoundTrips() {
        TestBar bar = new TestBar();
        Object prop = bar.getPropertyFromName(__GO_UI_MESSAGE__);
        assertNotNull(prop);
        assertEquals(UpdateStatus.Success, bar.setProperty(prop, new String[] {"a", "b", "c"}));
        assertArrayEquals(new String[] {"a", "b", "c"}, (String[]) bar.getProperty(prop));
    }

    @Test
    public void messageSizePropertyReportsLength() {
        TestBar bar = new TestBar();
        bar.setMessage(new String[] {"x", "y", "z"});
        Object sizeProp = bar.getPropertyFromName(__GO_UI_MESSAGE_SIZE__);
        assertEquals(Integer.valueOf(3), bar.getProperty(sizeProp));
    }

    @Test
    public void propertyDispatchForValueRoundTrips() {
        TestBar bar = new TestBar();
        Object prop = bar.getPropertyFromName(__GO_UI_VALUE__);
        assertNotNull(prop);
        assertEquals(UpdateStatus.Success, bar.setProperty(prop, Integer.valueOf(50)));
        assertEquals(Integer.valueOf(50), bar.getProperty(prop));
    }

    @Test
    public void acceptIsANoOp() {
        TestBar bar = new TestBar();
        // The base visitor hook does nothing and must not throw.
        assertDoesNotThrow(() -> bar.accept((Visitor) null));
    }

    @Test
    public void subclassTypeIsReported() {
        assertEquals(Integer.valueOf(TestBar.TYPE_SENTINEL), new TestBar().getType());
    }
}
