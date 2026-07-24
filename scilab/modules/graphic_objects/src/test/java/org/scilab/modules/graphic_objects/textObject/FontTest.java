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

package org.scilab.modules.graphic_objects.textObject;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import org.scilab.modules.graphic_objects.textObject.Font.FontProperty;
import org.scilab.modules.graphic_objects.graphicObject.GraphicObject.UpdateStatus;

/**
 * Hermetic unit tests for {@link Font}: a plain style/size/colour/fractional
 * holder with an enum-keyed getProperty/setProperty dispatch.
 */
public class FontTest {

    private static final double EPS = 1e-12;

    @Test
    public void constructorSetsDocumentedDefaults() {
        Font f = new Font();
        assertEquals(Integer.valueOf(6), f.getStyle());
        assertEquals(1.0, f.getSize().doubleValue(), EPS);
        assertEquals(Integer.valueOf(-1), f.getColor());
        assertTrue(f.getFractional());
    }

    @Test
    public void settersReturnSuccessAndAreReadBack() {
        Font f = new Font();
        assertEquals(UpdateStatus.Success, f.setStyle(2));
        assertEquals(UpdateStatus.Success, f.setSize(14.5));
        assertEquals(UpdateStatus.Success, f.setColor(3));
        assertEquals(UpdateStatus.Success, f.setFractional(false));

        assertEquals(Integer.valueOf(2), f.getStyle());
        assertEquals(14.5, f.getSize().doubleValue(), EPS);
        assertEquals(Integer.valueOf(3), f.getColor());
        assertFalse(f.getFractional());
    }

    @Test
    public void getPropertyDispatchesByEnum() {
        Font f = new Font();
        assertEquals(Integer.valueOf(6), f.getProperty(FontProperty.STYLE));
        assertEquals(Double.valueOf(1.0), f.getProperty(FontProperty.SIZE));
        assertEquals(Integer.valueOf(-1), f.getProperty(FontProperty.COLOR));
        assertEquals(Boolean.TRUE, f.getProperty(FontProperty.FRACTIONAL));
    }

    @Test
    public void getPropertyUnknownIsNull() {
        Font f = new Font();
        assertNull(f.getProperty(FontProperty.UNKNOWNPROPERTY));
        assertNull(f.getProperty("not an enum"));
    }

    @Test
    public void setPropertyDispatchesByEnum() {
        Font f = new Font();
        assertTrue(f.setProperty(FontProperty.STYLE, 9));
        assertTrue(f.setProperty(FontProperty.SIZE, 20.0));
        assertTrue(f.setProperty(FontProperty.COLOR, 42));
        assertTrue(f.setProperty(FontProperty.FRACTIONAL, false));

        assertEquals(Integer.valueOf(9), f.getStyle());
        assertEquals(20.0, f.getSize().doubleValue(), EPS);
        assertEquals(Integer.valueOf(42), f.getColor());
        assertFalse(f.getFractional());
    }

    @Test
    public void setPropertyWithUnknownKeyLeavesStateUnchanged() {
        Font f = new Font();
        assertTrue(f.setProperty(FontProperty.UNKNOWNPROPERTY, 123));
        // Nothing changed from the defaults.
        assertEquals(Integer.valueOf(6), f.getStyle());
        assertEquals(1.0, f.getSize().doubleValue(), EPS);
    }

    @Test
    public void setPropertyWithWrongValueTypeThrowsClassCast() {
        Font f = new Font();
        assertThrows(ClassCastException.class, () -> f.setProperty(FontProperty.STYLE, "not-an-int"));
        assertThrows(ClassCastException.class, () -> f.setProperty(FontProperty.SIZE, 5));
    }

    @Test
    public void copyConstructorDuplicatesStateIndependently() {
        Font src = new Font();
        src.setStyle(11);
        src.setSize(7.25);
        src.setColor(4);
        src.setFractional(false);

        Font copy = new Font(src);
        assertEquals(Integer.valueOf(11), copy.getStyle());
        assertEquals(7.25, copy.getSize().doubleValue(), EPS);
        assertEquals(Integer.valueOf(4), copy.getColor());
        assertFalse(copy.getFractional());

        copy.setStyle(99);
        assertEquals(Integer.valueOf(11), src.getStyle());
    }

    @Test
    public void getPropertyFromNameCurrentlyAlwaysUnknown() {
        // Characterisation of current behaviour: getPropertyFromName compares a
        // String argument against the int __GO_FONT_*__ constants, so no string
        // can ever match and the method always returns UNKNOWNPROPERTY.
        Font f = new Font();
        assertEquals(FontProperty.UNKNOWNPROPERTY, f.getPropertyFromName(""));
        assertEquals(FontProperty.UNKNOWNPROPERTY, f.getPropertyFromName("font_style"));
        assertEquals(FontProperty.UNKNOWNPROPERTY, f.getPropertyFromName("221"));
    }
}
