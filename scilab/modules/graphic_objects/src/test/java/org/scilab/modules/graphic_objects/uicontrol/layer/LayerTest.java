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

package org.scilab.modules.graphic_objects.uicontrol.layer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_UICONTROL__;
import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_UI_LAYER__;
import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_UI_TAB_STRING__;
import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_UI_TAB_VALUE__;

import org.scilab.modules.graphic_objects.graphicObject.GraphicObject.UpdateStatus;
import org.scilab.modules.graphic_objects.uicontrol.Uicontrol.UicontrolStyle;
import org.scilab.modules.graphic_objects.utils.LayoutType;

/**
 * Hermetic unit tests for {@link Layer}, a Uicontrol whose constructor forces a
 * BORDER layout and whose tab setters intentionally report NoChange while still
 * mutating (a behaviour the tests characterise).
 */
public class LayerTest {

    @Test
    public void styleIsLayer() {
        Layer l = new Layer();
        assertEquals(Integer.valueOf(__GO_UI_LAYER__), l.getStyle());
        assertEquals(UicontrolStyle.LAYER, l.getStyleAsEnum());
    }

    @Test
    public void typeInheritsGenericUicontrol() {
        assertEquals(Integer.valueOf(__GO_UICONTROL__), new Layer().getType());
    }

    @Test
    public void constructorForcesBorderLayoutAndAlignment() {
        Layer l = new Layer();
        assertEquals(LayoutType.BORDER, l.getLayoutAsEnum());
        assertEquals(Integer.valueOf(LayoutType.BORDER.ordinal()), l.getLayout());
        assertEquals("left", l.getHorizontalAlignment());
        assertEquals("middle", l.getVerticalAlignment());
    }

    @Test
    public void setUiTabStringMutatesButReportsNoChange() {
        Layer l = new Layer();
        String[] labels = {"a", "b"};
        // Documented quirk: the setter always returns NoChange yet stores.
        assertEquals(UpdateStatus.NoChange, l.setUiTabString(labels));
        assertArrayEquals(labels, l.getString());
    }

    @Test
    public void setUiTabValueMutatesButReportsNoChange() {
        Layer l = new Layer();
        Double[] vals = {1.0, 2.0, 3.0};
        assertEquals(UpdateStatus.NoChange, l.setUiTabValue(vals));
        assertArrayEquals(vals, l.getUiValue());
    }

    @Test
    public void propertyDispatchForTabStringRoundTrips() {
        Layer l = new Layer();
        Object prop = l.getPropertyFromName(__GO_UI_TAB_STRING__);
        assertNotNull(prop);
        // Routed through setUiTabString -> NoChange, but the value is stored.
        assertEquals(UpdateStatus.NoChange, l.setProperty(prop, new String[] {"x", "y"}));
        assertArrayEquals(new String[] {"x", "y"}, l.getString());
    }

    @Test
    public void propertyDispatchForTabValueRoundTrips() {
        Layer l = new Layer();
        Object prop = l.getPropertyFromName(__GO_UI_TAB_VALUE__);
        assertNotNull(prop);
        assertEquals(UpdateStatus.NoChange, l.setProperty(prop, new Double[] {7.0}));
        assertArrayEquals(new Double[] {7.0}, l.getUiValue());
    }

    @Test
    public void unknownPropertyNameDelegatesToSuper() {
        // A property Layer does not special-case must resolve via the parent.
        Layer l = new Layer();
        assertNotNull(l.getPropertyFromName(
                          org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_STYLE__));
    }
}
