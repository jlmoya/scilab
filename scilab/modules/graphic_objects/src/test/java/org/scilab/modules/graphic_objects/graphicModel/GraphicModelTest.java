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

package org.scilab.modules.graphic_objects.graphicModel;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import org.scilab.modules.graphic_objects.console.Console;
import org.scilab.modules.graphic_objects.graphicObject.GraphicObject;
import org.scilab.modules.graphic_objects.graphicObject.GraphicObject.Type;
import org.scilab.modules.graphic_objects.graphicObject.GraphicObject.UpdateStatus;
import org.scilab.modules.graphic_objects.rectangle.Rectangle;

import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_CONSOLE__;
import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_TAG__;
import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_TYPE__;

/**
 * Hermetic unit tests for {@link GraphicModel}, the singleton registry mapping
 * ids to {@link GraphicObject}s. Object creation is exercised only with the
 * pure-Java Console type and with unknown types; ids used here are large and
 * distinctive to avoid colliding with the shared map, and created entries are
 * removed afterward.
 */
public class GraphicModelTest {

    private static final Integer UNKNOWN_ID = 900000001;
    private static final Integer BAD_TYPE_ID = 900000002;
    private static final Integer CONSOLE_ID = 900000003;

    @Test
    public void getModelReturnsStableSingleton() {
        GraphicModel m = GraphicModel.getModel();
        assertNotNull(m);
        assertSame(m, GraphicModel.getModel());
    }

    @Test
    public void unknownIdLookupsAreNullOrFail() {
        GraphicModel m = GraphicModel.getModel();
        assertNull(m.getObjectFromId(UNKNOWN_ID));
        assertNull(m.getProperty(UNKNOWN_ID, __GO_TYPE__));
        assertEquals(UpdateStatus.Fail, m.setProperty(UNKNOWN_ID, __GO_TYPE__, 1));
    }

    /**
     * Characterization: types with no case in {@code createTypedObject}
     * (UNKNOWNOBJECT, TABGROUP) yield a null object, so createObject returns 0
     * and nothing is registered.
     */
    @Test
    public void createObjectWithUninstantiableTypeReturnsZero() {
        GraphicModel m = GraphicModel.getModel();
        assertEquals(Integer.valueOf(0), m.createObject(BAD_TYPE_ID, Type.UNKNOWNOBJECT));
        assertEquals(Integer.valueOf(0), m.createObject(BAD_TYPE_ID, Type.TABGROUP));
        assertNull(m.getObjectFromId(BAD_TYPE_ID));
    }

    @Test
    public void createObjectRegistersConsoleAndSupportsPropertyAccess() {
        GraphicModel m = GraphicModel.getModel();
        try {
            Integer created = m.createObject(CONSOLE_ID, Type.CONSOLE);
            assertEquals(CONSOLE_ID, created);

            GraphicObject stored = m.getObjectFromId(CONSOLE_ID);
            assertNotNull(stored);
            assertTrue(stored instanceof Console);
            assertEquals(CONSOLE_ID, stored.getIdentifier());

            // Property read routes name -> enum -> value through the object.
            assertEquals(Integer.valueOf(__GO_CONSOLE__), m.getProperty(CONSOLE_ID, __GO_TYPE__));

            // Property write on a known id must not report Fail, and the value
            // must be observable via a subsequent read.
            assertNotEquals(UpdateStatus.Fail, m.setProperty(CONSOLE_ID, __GO_TAG__, "gmTag"));
            assertEquals("gmTag", m.getProperty(CONSOLE_ID, __GO_TAG__));
        } finally {
            if (GraphicModel.getModel().getObjectFromId(CONSOLE_ID) != null) {
                GraphicModel.getModel().deleteObject(CONSOLE_ID);
            }
        }
        assertNull(m.getObjectFromId(CONSOLE_ID));
    }

    /**
     * Exercises the large {@code createTypedObject} dispatch: every plain,
     * side-effect-free type must instantiate a real {@link GraphicObject},
     * register it under the requested id, and stamp that id as the object's
     * identifier. Model/console singletons are covered separately to avoid
     * mutating shared static state here.
     */
    @Test
    public void createObjectInstantiatesEveryPlainType() {
        GraphicModel m = GraphicModel.getModel();
        Type[] types = {
            Type.ARC, Type.AXES, Type.AXIS, Type.CHAMP, Type.COMPOUND,
            Type.FAC3D, Type.FEC, Type.FIGURE, Type.GRAYPLOT, Type.LABEL,
            Type.LEGEND, Type.MATPLOT, Type.PLOT3D, Type.POLYLINE, Type.RECTANGLE,
            Type.SEGS, Type.TEXT, Type.CHECKBOX, Type.EDIT, Type.SPINNER,
            Type.FRAME, Type.IMAGE, Type.LISTBOX, Type.POPUPMENU, Type.PUSHBUTTON,
            Type.RADIOBUTTON, Type.SLIDER, Type.TABLE, Type.UITEXT, Type.UIMENU,
            Type.UICONTEXTMENU, Type.PROGRESSIONBAR, Type.WAITBAR, Type.LIGHT, Type.DATATIP,
            Type.TAB, Type.LAYER, Type.BORDER, Type.FRAME_SCROLLABLE, Type.BROWSER,
        };
        int base = 900002000;
        for (int i = 0; i < types.length; i++) {
            Integer id = base + i;
            try {
                Integer created = m.createObject(id, types[i]);
                assertEquals(id, created, "createObject should return the id for " + types[i]);
                GraphicObject o = m.getObjectFromId(id);
                assertNotNull(o, "an object should be registered for " + types[i]);
                assertEquals(id, o.getIdentifier(), "identifier should be set for " + types[i]);
            } finally {
                cleanup(m, id);
            }
        }
    }

    @Test
    public void createObjectSpotChecksConcreteRuntimeTypes() {
        GraphicModel m = GraphicModel.getModel();
        Integer id = 900002100;
        try {
            m.createObject(id, Type.RECTANGLE);
            assertTrue(m.getObjectFromId(id) instanceof Rectangle);
        } finally {
            cleanup(m, id);
        }
    }

    /**
     * FIGUREMODEL / AXESMODEL creation publishes the objects through the static
     * {@code getFigureModel} / {@code getAxesModel} accessors and flags them as
     * invalid (they are templates, not live objects).
     */
    @Test
    public void figureAndAxesModelsAreExposedAndFlaggedInvalid() {
        GraphicModel m = GraphicModel.getModel();
        Integer figId = 900002200;
        Integer axId = 900002201;
        try {
            m.createObject(figId, Type.FIGUREMODEL);
            m.createObject(axId, Type.AXESMODEL);

            assertNotNull(GraphicModel.getFigureModel());
            assertNotNull(GraphicModel.getAxesModel());
            assertSame(m.getObjectFromId(figId), GraphicModel.getFigureModel());
            assertSame(m.getObjectFromId(axId), GraphicModel.getAxesModel());
            assertFalse(GraphicModel.getFigureModel().isValid());
            assertFalse(GraphicModel.getAxesModel().isValid());
        } finally {
            cleanup(m, figId, axId);
        }
    }

    @Test
    public void cloneObjectCopiesStateUnderANewIndependentId() {
        GraphicModel m = GraphicModel.getModel();
        Integer srcId = 900002300;
        Integer cloneId = 900002301;
        try {
            m.createObject(srcId, Type.RECTANGLE);
            Rectangle src = (Rectangle) m.getObjectFromId(srcId);
            src.setBackground(12);

            assertEquals(cloneId, m.cloneObject(srcId, cloneId));
            GraphicObject clone = m.getObjectFromId(cloneId);
            assertNotNull(clone);
            assertNotSame(src, clone);
            assertTrue(clone instanceof Rectangle);
            assertEquals(cloneId, clone.getIdentifier());
            assertEquals(Integer.valueOf(12), ((Rectangle) clone).getBackground());

            // Independence: mutating the clone leaves the source untouched.
            ((Rectangle) clone).setBackground(99);
            assertEquals(Integer.valueOf(12), src.getBackground());
        } finally {
            cleanup(m, srcId, cloneId);
        }
    }

    @Test
    public void getNullPropertyReturnsNullForARegisteredObject() {
        GraphicModel m = GraphicModel.getModel();
        Integer id = 900002400;
        try {
            m.createObject(id, Type.RECTANGLE);
            assertNull(m.getNullProperty(id, "anything"));
        } finally {
            cleanup(m, id);
        }
    }

    private static void cleanup(GraphicModel m, Integer... ids) {
        for (Integer id : ids) {
            if (m.getObjectFromId(id) != null) {
                m.deleteObject(id);
            }
        }
    }
}
