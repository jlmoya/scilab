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

package org.scilab.modules.graphic_objects.compound;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties;
import org.scilab.modules.graphic_objects.graphicObject.GraphicObject.UpdateStatus;

/**
 * Hermetic unit tests for the {@link Compound} graphic object. Compound adds no
 * state of its own, so these tests pin its type and exercise the concrete
 * GraphicObject base behaviour (visibility, tag, children, clone) through it.
 */
public class CompoundTest {

    @Test
    public void typeIsCompound() {
        assertEquals(Integer.valueOf(GraphicObjectProperties.__GO_COMPOUND__), new Compound().getType());
    }

    @Test
    public void inheritedDefaults() {
        Compound c = new Compound();
        assertTrue(c.getVisible());
        assertTrue(c.isValid());
        assertFalse(c.isHidden());
        assertFalse(c.isReferenced());
        assertEquals("", c.getTag());
        assertEquals(Integer.valueOf(0), c.getParent());
        assertEquals(Integer.valueOf(0), c.getIdentifier());
        assertEquals(Integer.valueOf(0), c.getSelectedChild());
        assertEquals(0, c.getChildren().length);
    }

    @Test
    public void visibleReportsSuccessThenNoChange() {
        Compound c = new Compound();
        assertEquals(UpdateStatus.Success, c.setVisible(false));
        assertFalse(c.getVisible());
        assertEquals(UpdateStatus.NoChange, c.setVisible(false));
    }

    @Test
    public void tagReportsSuccessThenNoChange() {
        Compound c = new Compound();
        assertEquals(UpdateStatus.Success, c.setTag("group"));
        assertEquals("group", c.getTag());
        assertEquals(UpdateStatus.NoChange, c.setTag("group"));
    }

    @Test
    public void identifierAndParentRoundTrip() {
        Compound c = new Compound();
        assertEquals(UpdateStatus.Success, c.setIdentifier(17));
        assertEquals(Integer.valueOf(17), c.getIdentifier());
        assertEquals(UpdateStatus.Success, c.setParent(4));
        assertEquals(Integer.valueOf(4), c.getParent());
    }

    @Test
    public void childrenAreInsertedAtHeadAndRemovable() {
        Compound c = new Compound();
        c.addChild(5);
        c.addChild(6);
        // addChild inserts at index 0, so 6 precedes 5.
        assertArrayEquals(new Integer[] {6, 5}, c.getChildren());
        c.removeChild(5);
        assertArrayEquals(new Integer[] {6}, c.getChildren());
    }

    @Test
    public void callbackRoundTrips() {
        Compound c = new Compound();
        assertEquals("", c.getCallbackString());
        c.setCallbackString("disp(1)");
        assertEquals("disp(1)", c.getCallbackString());
    }

    @Test
    public void cloneClearsChildrenParentAndSelectedChild() {
        Compound c = new Compound();
        c.setParent(9);
        c.addChild(3);
        c.setSelectedChild(3);

        Compound copy = (Compound) c.clone();
        assertEquals(Integer.valueOf(0), copy.getParent());
        assertEquals(0, copy.getChildren().length);
        assertEquals(Integer.valueOf(0), copy.getSelectedChild());

        // The source keeps its own wiring.
        assertEquals(Integer.valueOf(9), c.getParent());
        assertArrayEquals(new Integer[] {3}, c.getChildren());
    }
}
