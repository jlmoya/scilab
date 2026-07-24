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

package org.scilab.modules.graphic_objects.uicontrol.tab;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_UICONTROL__;
import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_UI_TAB__;
import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_UI_TAB_STRING__;
import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_UI_TAB_VALUE__;

import org.scilab.modules.graphic_objects.graphicObject.GraphicObject.UpdateStatus;
import org.scilab.modules.graphic_objects.uicontrol.Uicontrol.UicontrolStyle;
import org.scilab.modules.graphic_objects.utils.LayoutType;

/**
 * Hermetic unit tests for {@link Tab}, a Uicontrol mirroring Layer's tab
 * setters (mutate-yet-NoChange) but reporting the TAB style/type.
 */
public class TabTest {

    @Test
    public void styleIsTab() {
        Tab t = new Tab();
        assertEquals(Integer.valueOf(__GO_UI_TAB__), t.getStyle());
        assertEquals(UicontrolStyle.TAB, t.getStyleAsEnum());
    }

    @Test
    public void typeInheritsGenericUicontrol() {
        assertEquals(Integer.valueOf(__GO_UICONTROL__), new Tab().getType());
    }

    @Test
    public void constructorForcesBorderLayoutAndAlignment() {
        Tab t = new Tab();
        assertEquals(LayoutType.BORDER, t.getLayoutAsEnum());
        assertEquals("left", t.getHorizontalAlignment());
        assertEquals("middle", t.getVerticalAlignment());
    }

    @Test
    public void setUiTabStringMutatesButReportsNoChange() {
        Tab t = new Tab();
        String[] labels = {"one", "two", "three"};
        assertEquals(UpdateStatus.NoChange, t.setUiTabString(labels));
        assertArrayEquals(labels, t.getString());
    }

    @Test
    public void setUiTabValueMutatesButReportsNoChange() {
        Tab t = new Tab();
        Double[] vals = {0.0, 1.0};
        assertEquals(UpdateStatus.NoChange, t.setUiTabValue(vals));
        assertArrayEquals(vals, t.getUiValue());
    }

    @Test
    public void propertyDispatchForTabStringRoundTrips() {
        Tab t = new Tab();
        Object prop = t.getPropertyFromName(__GO_UI_TAB_STRING__);
        assertNotNull(prop);
        assertEquals(UpdateStatus.NoChange, t.setProperty(prop, new String[] {"z"}));
        assertArrayEquals(new String[] {"z"}, t.getString());
    }

    @Test
    public void propertyDispatchForTabValueRoundTrips() {
        Tab t = new Tab();
        Object prop = t.getPropertyFromName(__GO_UI_TAB_VALUE__);
        assertNotNull(prop);
        assertEquals(UpdateStatus.NoChange, t.setProperty(prop, new Double[] {4.0, 5.0}));
        assertArrayEquals(new Double[] {4.0, 5.0}, t.getUiValue());
    }
}
