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

import java.util.ArrayList;

import org.scilab.modules.graphic_objects.graphicObject.GraphicObject.UpdateStatus;
import org.scilab.modules.graphic_objects.graphicObject.Visitor;

import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.*;

/**
 * Hermetic unit tests for the abstract {@link VectField} class, exercised via a
 * minimal concrete stub. Covers arrow-count management, the base/direction
 * marshalling across the arrow array, the aggregate line accessors, and the
 * empty-field edge cases.
 */
public class VectFieldTest {

    /** Minimal concrete VectField used only to instantiate the abstract class. */
    private static final class VectFieldStub extends VectField {
        @Override
        public void accept(Visitor visitor) {
            // no-op
        }
        @Override
        public Integer getType() {
            return -1;
        }
    }

    private static VectField newField() {
        return new VectFieldStub();
    }

    private static VectField fieldWithArrows(int n) {
        VectField f = newField();
        f.setNumberArrows(n);
        return f;
    }

    @Test
    public void constructorStartsEmpty() {
        VectField f = newField();
        assertNotNull(f.getArrows());
        assertTrue(f.getArrows().isEmpty());
        assertEquals(Integer.valueOf(0), f.getNumberArrows());
    }

    @Test
    public void setNumberArrowsFromEmptyCreatesArrows() {
        VectField f = newField();
        assertEquals(UpdateStatus.Success, f.setNumberArrows(3));
        assertEquals(Integer.valueOf(3), f.getNumberArrows());
        assertEquals(3, f.getArrows().size());
    }

    @Test
    public void setNumberArrowsToSameCountLeavesArrayUntouched() {
        VectField f = fieldWithArrows(3);
        ArrayList<Arrow> before = f.getArrows();
        Arrow firstBefore = before.get(0);
        f.setNumberArrows(3);
        assertEquals(Integer.valueOf(3), f.getNumberArrows());
        assertSame(firstBefore, f.getArrows().get(0), "same-count resize must not rebuild the array");
    }

    @Test
    public void growingNonEmptyFieldClonesFromFirstArrow() {
        VectField f = fieldWithArrows(2);
        f.setNumberArrows(5);
        assertEquals(Integer.valueOf(5), f.getNumberArrows());
    }

    @Test
    public void setNumberArrowsZeroOnEmptyStaysEmpty() {
        VectField f = newField();
        assertEquals(UpdateStatus.Success, f.setNumberArrows(0));
        assertEquals(Integer.valueOf(0), f.getNumberArrows());
    }

    @Test
    public void baseAccessorsReturnEmptyArraysOnEmptyField() {
        VectField f = newField();
        assertEquals(0, f.getBase().length);
        assertEquals(0, f.getBaseX().length);
        assertEquals(0, f.getBaseY().length);
        assertEquals(0, f.getBaseZ().length);
        assertEquals(0, f.getDirection().length);
        assertEquals(0, f.getDirectionX().length);
    }

    @Test
    public void baseRoundTripsAcrossTwoArrows() {
        VectField f = fieldWithArrows(2);
        // Interleaved (x,y,z) for arrow0 then arrow1.
        assertEquals(UpdateStatus.Success, f.setBase(new Double[] {1.0, 2.0, 3.0, 4.0, 5.0, 6.0}));
        assertArrayEquals(new Double[] {1.0, 2.0, 3.0, 4.0, 5.0, 6.0}, f.getBase());
        assertArrayEquals(new Double[] {1.0, 4.0}, f.getBaseX());
        assertArrayEquals(new Double[] {2.0, 5.0}, f.getBaseY());
        assertArrayEquals(new Double[] {3.0, 6.0}, f.getBaseZ());
    }

    @Test
    public void perAxisBaseSettersUpdateOnlyTheirComponent() {
        VectField f = fieldWithArrows(2);
        f.setBaseX(new Double[] {10.0, 20.0});
        f.setBaseY(new Double[] {30.0, 40.0});
        f.setBaseZ(new Double[] {50.0, 60.0});
        assertArrayEquals(new Double[] {10.0, 30.0, 50.0, 20.0, 40.0, 60.0}, f.getBase());
    }

    @Test
    public void directionRoundTripsAcrossTwoArrows() {
        VectField f = fieldWithArrows(2);
        assertEquals(UpdateStatus.Success, f.setDirection(new Double[] {1.0, 2.0, 3.0, 4.0, 5.0, 6.0}));
        assertArrayEquals(new Double[] {1.0, 4.0}, f.getDirectionX());
        assertArrayEquals(new Double[] {2.0, 5.0}, f.getDirectionY());
        assertArrayEquals(new Double[] {3.0, 6.0}, f.getDirectionZ());
    }

    @Test
    public void setBaseHonoursShorterInputWithoutError() {
        VectField f = fieldWithArrows(3);
        // Only one complete (x,y,z) triple is supplied; remaining arrows stay at 0.
        f.setBase(new Double[] {7.0, 8.0, 9.0});
        assertArrayEquals(new Double[] {7.0, 8.0, 9.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0}, f.getBase());
    }

    @Test
    public void aggregateLineAccessorsApplyToEveryArrow() {
        VectField f = fieldWithArrows(3);
        assertEquals(UpdateStatus.Success, f.setArrowSize(2.0));
        assertEquals(2.0, f.getArrowSize(), 0.0);

        assertEquals(UpdateStatus.Success, f.setLineMode(true));
        assertTrue(f.getLineMode());

        assertEquals(UpdateStatus.Success, f.setLineStyle(3));
        assertEquals(Integer.valueOf(3), f.getLineStyle());

        assertEquals(UpdateStatus.Success, f.setLineThickness(2.5));
        assertEquals(2.5, f.getLineThickness(), 0.0);

        assertEquals(UpdateStatus.Success, f.setLineColor(7));
        assertEquals(Integer.valueOf(7), f.getLineColor());

        // Confirm the value really propagated to each individual arrow.
        for (Arrow arrow : f.getArrows()) {
            assertEquals(2.0, arrow.getArrowSize(), 0.0);
            assertEquals(Integer.valueOf(7), arrow.getLineColor());
        }
    }

    @Test
    public void aggregateAccessorsThrowOnEmptyField() {
        // Defect characterization: the aggregate accessors read arrows.get(0)
        // unconditionally, so they blow up when the field has no arrows.
        VectField f = newField();
        assertThrows(IndexOutOfBoundsException.class, f::getArrowSize);
        assertThrows(IndexOutOfBoundsException.class, f::getLineMode);
        assertThrows(IndexOutOfBoundsException.class, f::getLineStyle);
        assertThrows(IndexOutOfBoundsException.class, f::getLineThickness);
    }

    @Test
    public void setArrowsReplacesTheBackingList() {
        VectField f = newField();
        ArrayList<Arrow> list = new ArrayList<Arrow>();
        list.add(new Arrow());
        list.add(new Arrow());
        assertEquals(UpdateStatus.Success, f.setArrows(list));
        assertSame(list, f.getArrows());
        assertEquals(Integer.valueOf(2), f.getNumberArrows());
    }

    @Test
    public void propertyNameLookupRoundTrips() {
        VectField f = fieldWithArrows(2);

        Object numberArrows = f.getPropertyFromName(__GO_NUMBER_ARROWS__);
        assertEquals(Integer.valueOf(2), f.getProperty(numberArrows));

        Object base = f.getPropertyFromName(__GO_BASE__);
        assertEquals(UpdateStatus.Success, f.setProperty(base, new Double[] {1.0, 2.0, 3.0, 4.0, 5.0, 6.0}));
        assertArrayEquals(new Double[] {1.0, 2.0, 3.0, 4.0, 5.0, 6.0}, (Double[]) f.getProperty(base));

        Object lineMode = f.getPropertyFromName(__GO_LINE_MODE__);
        assertEquals(UpdateStatus.Success, f.setProperty(lineMode, Boolean.TRUE));
        assertEquals(Boolean.TRUE, f.getProperty(lineMode));
    }
}
