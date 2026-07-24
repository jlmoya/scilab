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

package org.scilab.modules.graphic_objects.vectfield;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import org.scilab.modules.graphic_objects.graphicObject.GraphicObject.UpdateStatus;

import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.*;

/**
 * Hermetic unit tests for {@link Arrow}: base/direction/arrow-size storage, the
 * copy semantics of its getters, the {@code clone} contract, its public
 * property enum, and the inherited {@code ContouredObject} defaults.
 */
public class ArrowTest {

    @Test
    public void constructorDefaults() {
        Arrow a = new Arrow();
        assertArrayEquals(new Double[] {0.0, 0.0, 0.0}, a.getBase());
        assertArrayEquals(new Double[] {0.0, 0.0, 0.0}, a.getDirection());
        assertEquals(-1.0, a.getArrowSize(), 0.0);
        assertEquals(Integer.valueOf(-1), a.getType());
    }

    @Test
    public void baseRoundTrips() {
        Arrow a = new Arrow();
        assertEquals(UpdateStatus.Success, a.setBase(new Double[] {1.0, 2.0, 3.0}));
        assertArrayEquals(new Double[] {1.0, 2.0, 3.0}, a.getBase());
    }

    @Test
    public void directionRoundTrips() {
        Arrow a = new Arrow();
        assertEquals(UpdateStatus.Success, a.setDirection(new Double[] {4.0, 5.0, 6.0}));
        assertArrayEquals(new Double[] {4.0, 5.0, 6.0}, a.getDirection());
    }

    @Test
    public void arrowSizeRoundTrips() {
        Arrow a = new Arrow();
        assertEquals(UpdateStatus.Success, a.setArrowSize(2.5));
        assertEquals(2.5, a.getArrowSize(), 0.0);
    }

    @Test
    public void gettersReturnDefensiveCopies() {
        Arrow a = new Arrow();
        a.setBase(new Double[] {1.0, 1.0, 1.0});
        Double[] first = a.getBase();
        Double[] second = a.getBase();
        assertNotSame(first, second);
        first[0] = 99.0;
        assertEquals(1.0, a.getBase()[0], 0.0);
    }

    @Test
    public void cloneResetsBaseAndDirectionButKeepsArrowSize() {
        // Defect characterization of the current clone() contract: the geometry
        // (base and direction) is deliberately zeroed, while the scalar arrowSize
        // is carried over by the shallow super.clone().
        Arrow a = new Arrow();
        a.setBase(new Double[] {7.0, 8.0, 9.0});
        a.setDirection(new Double[] {1.0, 2.0, 3.0});
        a.setArrowSize(4.0);

        Arrow copy = a.clone();
        assertArrayEquals(new Double[] {0.0, 0.0, 0.0}, copy.getBase());
        assertArrayEquals(new Double[] {0.0, 0.0, 0.0}, copy.getDirection());
        assertEquals(4.0, copy.getArrowSize(), 0.0);

        // The clone must own separate arrays from the source.
        copy.setBase(new Double[] {5.0, 5.0, 5.0});
        assertArrayEquals(new Double[] {7.0, 8.0, 9.0}, a.getBase());
    }

    @Test
    public void inheritsContouredObjectLineAndMarkDefaults() {
        Arrow a = new Arrow();
        assertEquals(Integer.valueOf(-1), a.getLineColor()); // Line default color
        assertFalse(a.getLineMode());
        assertEquals(Integer.valueOf(1), a.getLineStyle()); // SOLID -> scilab index 1
        assertEquals(1.0, a.getLineThickness(), 0.0);
        assertFalse(a.getMarkMode());
    }

    @Test
    public void propertyEnumGetSetRoundTrips() {
        Arrow a = new Arrow();
        assertEquals(UpdateStatus.Success, a.setProperty(Arrow.ArrowProperty.BASE, new Double[] {1.0, 2.0, 3.0}));
        assertArrayEquals(new Double[] {1.0, 2.0, 3.0}, (Double[]) a.getProperty(Arrow.ArrowProperty.BASE));

        assertEquals(UpdateStatus.Success, a.setProperty(Arrow.ArrowProperty.DIRECTION, new Double[] {4.0, 5.0, 6.0}));
        assertArrayEquals(new Double[] {4.0, 5.0, 6.0}, (Double[]) a.getProperty(Arrow.ArrowProperty.DIRECTION));

        assertEquals(UpdateStatus.Success, a.setProperty(Arrow.ArrowProperty.ARROWSIZE, Double.valueOf(3.0)));
        assertEquals(Double.valueOf(3.0), a.getProperty(Arrow.ArrowProperty.ARROWSIZE));
    }

    @Test
    public void propertyNameLookupMapsToArrowEnum() {
        Arrow a = new Arrow();
        assertEquals(Arrow.ArrowProperty.BASE, a.getPropertyFromName(__GO_BASE__));
        assertEquals(Arrow.ArrowProperty.DIRECTION, a.getPropertyFromName(__GO_DIRECTION__));
        assertEquals(Arrow.ArrowProperty.ARROWSIZE, a.getPropertyFromName(__GO_ARROW_SIZE__));
    }

    @Test
    public void unknownPropertyDelegatesToSuperClass() {
        Arrow a = new Arrow();
        Object visible = a.getPropertyFromName(__GO_VISIBLE__);
        assertNotNull(visible);
        assertEquals(Boolean.TRUE, a.getProperty(visible));
    }
}
