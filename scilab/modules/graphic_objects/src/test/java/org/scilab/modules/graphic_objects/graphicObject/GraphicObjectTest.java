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

package org.scilab.modules.graphic_objects.graphicObject;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import org.scilab.modules.graphic_objects.graphicObject.GraphicObject.GraphicObjectPropertyType;
import org.scilab.modules.graphic_objects.graphicObject.GraphicObject.Type;
import org.scilab.modules.graphic_objects.graphicObject.GraphicObject.UpdateStatus;

import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_ARC__;
import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_CALLBACK__;
import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_FIGURE__;
import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_PARENT__;
import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_POLYLINE__;
import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_TAG__;
import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_VISIBLE__;

/**
 * Hermetic unit tests for the abstract {@link GraphicObject} base class,
 * exercised through a minimal concrete stub. Only controller-free code paths
 * are tested (a freshly constructed object has parent == 0 and no children, so
 * the parent-walking / legend-scanning helpers short-circuit before ever
 * touching the native-backed GraphicController).
 */
public class GraphicObjectTest {

    /** Distinctive type id so {@code getType()} results are unambiguous. */
    private static final int STUB_TYPE = -999;

    /** Minimal concrete GraphicObject usable as a plain data holder. */
    private static final class Stub extends GraphicObject {
        public Integer getType() {
            return STUB_TYPE;
        }

        public void accept(Visitor visitor) {
            // no-op
        }
    }

    // ---- construction defaults ------------------------------------------

    @Test
    public void constructorDefaults() {
        Stub o = new Stub();
        assertEquals(Integer.valueOf(0), o.getIdentifier());
        assertEquals(Integer.valueOf(0), o.getParent());
        assertEquals(0, o.getChildren().length);
        assertTrue(o.getVisible());
        assertTrue(o.isValid());
        assertFalse(o.isReferenced());
        assertFalse(o.isHidden());
        assertEquals(Integer.valueOf(0), o.getSelectedChild());
        assertEquals("", o.getTag());
        assertEquals("", o.getCallbackString());
        assertEquals(Integer.valueOf(-1), o.getCallbackType());
    }

    // ---- simple scalar getters/setters ----------------------------------

    @Test
    public void identifierAndParentSetters() {
        Stub o = new Stub();
        assertEquals(UpdateStatus.Success, o.setIdentifier(11));
        assertEquals(Integer.valueOf(11), o.getIdentifier());
        assertEquals(UpdateStatus.Success, o.setParent(22));
        assertEquals(Integer.valueOf(22), o.getParent());
    }

    @Test
    public void tagChangeDetection() {
        Stub o = new Stub();
        // Default tag is "", so setting "" is a no-op.
        assertEquals(UpdateStatus.NoChange, o.setTag(""));
        assertEquals(UpdateStatus.Success, o.setTag("myTag"));
        assertEquals("myTag", o.getTag());
        assertEquals(UpdateStatus.NoChange, o.setTag("myTag"));
    }

    @Test
    public void visibleChangeDetection() {
        Stub o = new Stub();
        assertEquals(UpdateStatus.NoChange, o.setVisible(true)); // already visible
        assertEquals(UpdateStatus.Success, o.setVisible(false));
        assertFalse(o.getVisible());
        assertEquals(UpdateStatus.NoChange, o.setVisible(false));
    }

    @Test
    public void validReferencedHiddenSettersAlwaysSucceed() {
        Stub o = new Stub();
        assertEquals(UpdateStatus.Success, o.setValid(false));
        assertFalse(o.isValid());
        assertEquals(UpdateStatus.Success, o.setReferenced(true));
        assertTrue(o.isReferenced());
        assertEquals(UpdateStatus.Success, o.setHidden(true));
        assertTrue(o.isHidden());
    }

    @Test
    public void selectedChildSetter() {
        Stub o = new Stub();
        assertEquals(UpdateStatus.Success, o.setSelectedChild(5));
        assertEquals(Integer.valueOf(5), o.getSelectedChild());
    }

    @Test
    public void callbackStringAndType() {
        Stub o = new Stub();
        assertEquals(UpdateStatus.Success, o.setCallbackString("plot()"));
        assertEquals("plot()", o.getCallbackString());
        assertEquals(UpdateStatus.Success, o.setCallbackType(3));
        assertEquals(Integer.valueOf(3), o.getCallbackType());
    }

    // ---- children management --------------------------------------------

    @Test
    public void addChildPrependsAndRemoveByValue() {
        Stub o = new Stub();
        o.addChild(10);
        o.addChild(20);
        // addChild inserts at the head, so the most-recent child is first.
        assertArrayEquals(new Integer[] {20, 10}, o.getChildren());

        o.removeChild(10);
        assertArrayEquals(new Integer[] {20}, o.getChildren());
    }

    @Test
    public void setChildrenReplacesList() {
        Stub o = new Stub();
        o.addChild(1);
        assertEquals(UpdateStatus.Success, o.setChildren(new Integer[] {7, 8, 9}));
        assertArrayEquals(new Integer[] {7, 8, 9}, o.getChildren());
    }

    // ---- getPropertyFromName / getTypeFromName --------------------------

    @Test
    public void getPropertyFromNameMapsKnownAndUnknown() {
        Stub o = new Stub();
        assertEquals(GraphicObjectPropertyType.PARENT, o.getPropertyFromName(__GO_PARENT__));
        assertEquals(GraphicObjectPropertyType.TAG, o.getPropertyFromName(__GO_TAG__));
        assertEquals(GraphicObjectPropertyType.CALLBACK, o.getPropertyFromName(__GO_CALLBACK__));
        assertEquals(GraphicObjectPropertyType.VISIBLE, o.getPropertyFromName(__GO_VISIBLE__));
        assertEquals(GraphicObjectPropertyType.UNKNOWNPROPERTY, o.getPropertyFromName(999999));
    }

    @Test
    public void getTypeFromNameMapsKnownAndUnknown() {
        assertEquals(Type.ARC, GraphicObject.getTypeFromName(__GO_ARC__));
        assertEquals(Type.FIGURE, GraphicObject.getTypeFromName(__GO_FIGURE__));
        assertEquals(Type.POLYLINE, GraphicObject.getTypeFromName(__GO_POLYLINE__));
        assertEquals(Type.UNKNOWNOBJECT, GraphicObject.getTypeFromName(999999));
    }

    // ---- generic getProperty / setProperty ------------------------------

    @Test
    public void getPropertyReturnsBackingValues() {
        Stub o = new Stub();
        o.setIdentifier(3);
        o.setSelectedChild(4);
        o.addChild(1);
        o.addChild(2);

        assertEquals(Integer.valueOf(0), o.getProperty(GraphicObjectPropertyType.PARENT));
        assertEquals(Integer.valueOf(2), o.getProperty(GraphicObjectPropertyType.CHILDREN_COUNT));
        assertEquals(Boolean.TRUE, o.getProperty(GraphicObjectPropertyType.VALID));
        assertEquals(Boolean.FALSE, o.getProperty(GraphicObjectPropertyType.HIDDEN));
        assertEquals(Boolean.TRUE, o.getProperty(GraphicObjectPropertyType.VISIBLE));
        assertEquals(Integer.valueOf(4), o.getProperty(GraphicObjectPropertyType.SELECTEDCHILD));
        assertEquals(Integer.valueOf(STUB_TYPE), o.getProperty(GraphicObjectPropertyType.TYPE));
        assertEquals(Integer.valueOf(3), o.getProperty(GraphicObjectPropertyType.DATA));
        assertEquals("", o.getProperty(GraphicObjectPropertyType.TAG));
        assertEquals("", o.getProperty(GraphicObjectPropertyType.CALLBACK));
        assertNull(o.getProperty(GraphicObjectPropertyType.UNKNOWNPROPERTY));
    }

    @Test
    public void getPropertyOnFreshObjectHasNoParentFigureOrAxes() {
        // parent == 0 => these short-circuit to 0 without any controller call.
        Stub o = new Stub();
        assertEquals(Integer.valueOf(0), o.getProperty(GraphicObjectPropertyType.PARENT_FIGURE));
        assertEquals(Integer.valueOf(0), o.getProperty(GraphicObjectPropertyType.PARENT_AXES));
        assertEquals(Boolean.FALSE, o.getProperty(GraphicObjectPropertyType.HASLEGENDCHILD));
        assertEquals(Integer.valueOf(0), o.getProperty(GraphicObjectPropertyType.LEGENDCHILD));
    }

    @Test
    public void getPropertyWithNonEnumKeyIsNull() {
        Stub o = new Stub();
        assertNull(o.getProperty("not a property enum"));
    }

    @Test
    public void setPropertyRoundTrips() {
        Stub o = new Stub();
        assertEquals(UpdateStatus.Success, o.setProperty(GraphicObjectPropertyType.TAG, "hello"));
        assertEquals("hello", o.getTag());

        assertEquals(UpdateStatus.Success, o.setProperty(GraphicObjectPropertyType.PARENT, 42));
        assertEquals(Integer.valueOf(42), o.getParent());

        assertEquals(UpdateStatus.Success, o.setProperty(GraphicObjectPropertyType.VISIBLE, false));
        assertFalse(o.getVisible());

        assertEquals(UpdateStatus.Success, o.setProperty(GraphicObjectPropertyType.VALID, false));
        assertFalse(o.isValid());

        assertEquals(UpdateStatus.Success, o.setProperty(GraphicObjectPropertyType.SELECTEDCHILD, 7));
        assertEquals(Integer.valueOf(7), o.getSelectedChild());

        assertEquals(UpdateStatus.Success,
                     o.setProperty(GraphicObjectPropertyType.CHILDREN, new Integer[] {3, 4}));
        assertArrayEquals(new Integer[] {3, 4}, o.getChildren());

        assertEquals(UpdateStatus.Success, o.setProperty(GraphicObjectPropertyType.CALLBACK, "cb"));
        assertEquals("cb", o.getCallbackString());

        assertEquals(UpdateStatus.Success, o.setProperty(GraphicObjectPropertyType.CALLBACKTYPE, 2));
        assertEquals(Integer.valueOf(2), o.getCallbackType());
    }

    @Test
    public void setPropertyDataIsANoOpButSucceeds() {
        Stub o = new Stub();
        o.setIdentifier(55);
        assertEquals(UpdateStatus.Success, o.setProperty(GraphicObjectPropertyType.DATA, 12345));
        // DATA maps to the read-only identifier; setting it does not change it.
        assertEquals(Integer.valueOf(55), o.getIdentifier());
    }

    @Test
    public void setPropertyUnknownFailsButNonEnumSucceeds() {
        Stub o = new Stub();
        assertEquals(UpdateStatus.Fail,
                     o.setProperty(GraphicObjectPropertyType.UNKNOWNPROPERTY, 1));
        // Characterization: a non-GraphicObjectPropertyType key is silently
        // accepted (returns Success) even though nothing is written.
        assertEquals(UpdateStatus.Success, o.setProperty("bogus", 1));
    }

    // ---- misc null/void accessors ---------------------------------------

    @Test
    public void nullAndVoidAccessors() {
        Stub o = new Stub();
        assertNull(o.getNullProperty("anything"));
        assertNull(o.getPropertyVoid("anything"));
        // setPropertyVoid is a documented no-op; it must not throw.
        assertDoesNotThrow(() -> o.setPropertyVoid("anything", "value"));
    }

    // ---- clone ----------------------------------------------------------

    @Test
    public void cloneResetsHierarchyButCopiesScalars() {
        Stub o = new Stub();
        o.setParent(9);
        o.addChild(1);
        o.addChild(2);
        o.setSelectedChild(3);
        o.setTag("t");
        o.setVisible(false);
        o.setValid(false);
        o.setHidden(true);

        GraphicObject clone = o.clone();
        assertNotSame(o, clone);
        // Hierarchy fields are reset on clone.
        assertEquals(Integer.valueOf(0), clone.getParent());
        assertEquals(0, clone.getChildren().length);
        assertEquals(Integer.valueOf(0), clone.getSelectedChild());
        // Scalar state is copied.
        assertEquals("t", clone.getTag());
        assertFalse(clone.getVisible());
        assertFalse(clone.isValid());
        assertTrue(clone.isHidden());
    }

    @Test
    public void cloneHasIndependentChildrenList() {
        Stub o = new Stub();
        o.addChild(1);
        GraphicObject clone = o.clone();
        // Adding to the original does not leak into the clone's fresh list.
        o.addChild(2);
        assertEquals(0, clone.getChildren().length);
        assertArrayEquals(new Integer[] {2, 1}, o.getChildren());
    }

    /**
     * Characterization: {@link GraphicObject#clone()} is a shallow copy that
     * does not duplicate the CallBack, so the clone and the original share the
     * same callback object and observe each other's command changes.
     */
    @Test
    public void cloneSharesCallbackObject() {
        Stub o = new Stub();
        o.setCallbackString("orig");
        GraphicObject clone = o.clone();
        o.setCallbackString("changed");
        assertEquals("changed", clone.getCallbackString());
    }
}
