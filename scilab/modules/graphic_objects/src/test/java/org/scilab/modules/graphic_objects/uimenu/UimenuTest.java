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

package org.scilab.modules.graphic_objects.uimenu;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_UIMENU__;
import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_UI_CHECKED__;
import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_UI_FOREGROUNDCOLOR__;
import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_UI_LABEL__;
import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_UI_TOOLTIPSTRING__;
import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_UI_TOOLTIPSTRING_SIZE__;

import org.scilab.modules.graphic_objects.graphicObject.GraphicObject.UpdateStatus;
import org.scilab.modules.graphic_objects.graphicObject.Visitor;

/**
 * Hermetic unit tests for {@link Uimenu}: a GraphicObject-backed menu entry
 * with a broad set of scalar properties, an overridden clone(), and a
 * guard-free setter family (only tooltipString short-circuits on no change).
 */
public class UimenuTest {

    @Test
    public void typeIsUimenu() {
        assertEquals(Integer.valueOf(__GO_UIMENU__), new Uimenu().getType());
    }

    @Test
    public void constructorInitialisesCallback() {
        Uimenu m = new Uimenu();
        assertEquals("", m.getCallbackString());
        assertEquals(Integer.valueOf(0), m.getCallbackType());
    }

    @Test
    public void defaults() {
        Uimenu m = new Uimenu();
        assertFalse(m.getChecked());
        assertTrue(m.getEnable());
        assertArrayEquals(new Double[] {0.0, 0.0, 0.0}, m.getForegroundColor());
        assertEquals("", m.getIcon());
        assertEquals("", m.getLabel());
        assertEquals("", m.getMnemonic());
        assertEquals("", m.getAccelerator());
        assertFalse(m.getSeparator());
        assertArrayEquals(new String[] {""}, m.getTooltipString());
    }

    @Test
    public void scalarSettersStoreAndAlwaysReportSuccess() {
        Uimenu m = new Uimenu();

        assertEquals(UpdateStatus.Success, m.setChecked(Boolean.TRUE));
        assertTrue(m.getChecked());
        // No NoChange guard: setting the same value again is still Success.
        assertEquals(UpdateStatus.Success, m.setChecked(Boolean.TRUE));

        assertEquals(UpdateStatus.Success, m.setEnable(Boolean.FALSE));
        assertFalse(m.getEnable());

        assertEquals(UpdateStatus.Success, m.setIcon("icon.png"));
        assertEquals("icon.png", m.getIcon());

        assertEquals(UpdateStatus.Success, m.setLabel("File"));
        assertEquals("File", m.getLabel());

        assertEquals(UpdateStatus.Success, m.setMnemonic("F"));
        assertEquals("F", m.getMnemonic());

        assertEquals(UpdateStatus.Success, m.setAccelerator("Ctrl+S"));
        assertEquals("Ctrl+S", m.getAccelerator());

        assertEquals(UpdateStatus.Success, m.setSeparator(Boolean.TRUE));
        assertTrue(m.getSeparator());
    }

    @Test
    public void foregroundColorRoundTrips() {
        Uimenu m = new Uimenu();
        Double[] rgb = {1.0, 0.5, 0.25};
        assertEquals(UpdateStatus.Success, m.setForegroundColor(rgb));
        assertArrayEquals(rgb, m.getForegroundColor());
    }

    @Test
    public void tooltipStringIsTheOnlyGuardedSetter() {
        Uimenu m = new Uimenu();
        // Fresh value equals the default {""} -> NoChange.
        assertEquals(UpdateStatus.NoChange, m.setTooltipString(new String[] {""}));

        assertEquals(UpdateStatus.Success, m.setTooltipString(new String[] {"hint"}));
        assertArrayEquals(new String[] {"hint"}, m.getTooltipString());
        // Same content again -> NoChange (Arrays.equals guard).
        assertEquals(UpdateStatus.NoChange, m.setTooltipString(new String[] {"hint"}));
    }

    @Test
    public void propertyDispatchRoundTripsLabelAndChecked() {
        Uimenu m = new Uimenu();

        Object label = m.getPropertyFromName(__GO_UI_LABEL__);
        m.setProperty(label, "Edit");
        assertEquals("Edit", m.getProperty(label));

        Object checked = m.getPropertyFromName(__GO_UI_CHECKED__);
        m.setProperty(checked, Boolean.TRUE);
        assertEquals(Boolean.TRUE, m.getProperty(checked));
    }

    @Test
    public void propertyDispatchRoundTripsForegroundColor() {
        Uimenu m = new Uimenu();
        Object fg = m.getPropertyFromName(__GO_UI_FOREGROUNDCOLOR__);
        Double[] rgb = {0.1, 0.2, 0.3};
        m.setProperty(fg, rgb);
        assertArrayEquals(rgb, (Double[]) m.getProperty(fg));
    }

    @Test
    public void tooltipSizePropertyReportsLength() {
        Uimenu m = new Uimenu();
        m.setTooltipString(new String[] {"a", "b"});
        Object sizeProp = m.getPropertyFromName(__GO_UI_TOOLTIPSTRING_SIZE__);
        assertEquals(Integer.valueOf(2), m.getProperty(sizeProp));

        Object tip = m.getPropertyFromName(__GO_UI_TOOLTIPSTRING__);
        assertArrayEquals(new String[] {"a", "b"}, (String[]) m.getProperty(tip));
    }

    @Test
    public void cloneReturnsIndependentValidUimenu() {
        Uimenu m = new Uimenu();
        m.setLabel("A");
        m.setValid(false);

        Uimenu copy = m.clone();
        assertNotSame(m, copy);
        assertEquals(Integer.valueOf(__GO_UIMENU__), copy.getType());
        // clone() forces the copy back to valid regardless of the source.
        assertTrue(copy.isValid());

        // Reassigning a property on the copy must not disturb the original.
        copy.setLabel("B");
        assertEquals("A", m.getLabel());
        assertEquals("B", copy.getLabel());
    }

    @Test
    public void acceptIsANoOp() {
        assertDoesNotThrow(() -> new Uimenu().accept((Visitor) null));
    }
}
