/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Jose Moya
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 *
 */

package org.scilab.modules.renderer.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.scilab.modules.graphic_objects.axes.Axes;
import org.scilab.modules.graphic_objects.graphicController.GraphicController;
import org.scilab.modules.graphic_objects.graphicObject.GraphicObject;
import org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties;

/**
 * Hermetic unit tests for the controller-only surface of {@link AxesHandler}.
 * Objects are built through the pure-Java {@link GraphicController} singleton
 * (headless, no views), so no OpenGL/native projection is involved. The
 * clone/paste helpers reach {@code ScilabNativeView} (JNI) and are out of scope.
 */
class AxesHandlerTest {

    private static final double EPS = 1e-9;
    private static final GraphicController CONTROLLER = GraphicController.getController();

    private static Integer create(GraphicObject.Type type) {
        return CONTROLLER.askObject(type);
    }

    private static void link(Integer parent, Integer child) {
        CONTROLLER.setGraphicObjectRelationship(parent, child);
    }

    @Test
    void axisToEnumHasTheFourExpectedConstantsInOrder() {
        AxesHandler.axisTo[] values = AxesHandler.axisTo.values();
        assertEquals(4, values.length);
        assertEquals(AxesHandler.axisTo.__X__, values[0]);
        assertEquals(AxesHandler.axisTo.__Y__, values[1]);
        assertEquals(AxesHandler.axisTo.__Z__, values[2]);
        assertEquals(AxesHandler.axisTo.__TITLE__, values[3]);
        assertEquals(AxesHandler.axisTo.__TITLE__, AxesHandler.axisTo.valueOf("__TITLE__"));
        assertEquals(3, AxesHandler.axisTo.__TITLE__.ordinal());
    }

    @Test
    void getAxesFromUidReturnsTheModelObjectForThatUid() {
        Integer uid = create(GraphicObject.Type.AXES);
        Axes axes = AxesHandler.getAxesFromUid(uid);
        assertSame(CONTROLLER.getObjectFromId(uid), axes, "returns the very object registered in the model");
        assertTrue(axes instanceof Axes);
    }

    @Test
    void zoomBoxEnabledFlagRoundTripsThroughTheController() {
        Integer axes = create(GraphicObject.Type.AXES);
        CONTROLLER.setProperty(axes, GraphicObjectProperties.__GO_ZOOM_ENABLED__, true);
        assertTrue(AxesHandler.isZoomBoxEnabled(axes));
        CONTROLLER.setProperty(axes, GraphicObjectProperties.__GO_ZOOM_ENABLED__, false);
        assertFalse(AxesHandler.isZoomBoxEnabled(axes));
    }

    @Test
    void isInZoomBoxBoundsTestsThePointAgainstTheStoredZoomBox() {
        Integer axes = create(GraphicObject.Type.AXES);
        // ZOOM_BOX = [xMin, xMax, yMin, yMax]
        CONTROLLER.setProperty(axes, GraphicObjectProperties.__GO_ZOOM_BOX__,
                               new Double[] {0.0, 10.0, 0.0, 10.0});
        assertTrue(AxesHandler.isInZoomBoxBounds(axes, 5.0, 5.0), "interior point");
        assertTrue(AxesHandler.isInZoomBoxBounds(axes, 0.0, 10.0), "corner is inclusive");
        assertFalse(AxesHandler.isInZoomBoxBounds(axes, 15.0, 5.0), "outside on x");
        assertFalse(AxesHandler.isInZoomBoxBounds(axes, 5.0, -1.0), "outside on y");
    }

    @Test
    void setAxesVisibleTurnsOnTheVisibilityRelatedProperties() {
        Integer axes = create(GraphicObject.Type.AXES);
        AxesHandler.setAxesVisible(axes);
        assertEquals(Boolean.TRUE, CONTROLLER.getProperty(axes, GraphicObjectProperties.__GO_VISIBLE__));
        assertEquals(Boolean.TRUE, CONTROLLER.getProperty(axes, GraphicObjectProperties.__GO_FILLED__));
        assertEquals(Integer.valueOf(1), CONTROLLER.getProperty(axes, GraphicObjectProperties.__GO_BOX_TYPE__));
    }

    @Test
    void isAxesEmptyIsFalseOnceANonLabelChildIsAdded() {
        Integer axes = create(GraphicObject.Type.AXES);
        link(axes, create(GraphicObject.Type.POLYLINE));
        assertFalse(AxesHandler.isAxesEmpty(axes), "a polyline child makes the axes non-empty");
    }

    @Test
    void isAxesNotBlankSeesAVisiblePolylineAndIgnoresAnEmptyAxes() {
        Integer figureWithPlot = create(GraphicObject.Type.FIGURE);
        Integer axes = create(GraphicObject.Type.AXES);
        Integer polyline = create(GraphicObject.Type.POLYLINE);
        link(figureWithPlot, axes);
        link(axes, polyline);
        CONTROLLER.setProperty(polyline, GraphicObjectProperties.__GO_VISIBLE__, true);
        assertTrue(AxesHandler.isAxesNotBlank(figureWithPlot), "a visible polyline is not blank");

        Integer emptyFigure = create(GraphicObject.Type.FIGURE);
        link(emptyFigure, create(GraphicObject.Type.AXES));
        assertFalse(AxesHandler.isAxesNotBlank(emptyFigure), "an axes with no drawable children is blank");
    }

    @Test
    void axesBoundMergesTheWidestDataBoundsIntoTheTargetAxes() {
        Integer from = create(GraphicObject.Type.AXES);
        Integer to = create(GraphicObject.Type.AXES);
        CONTROLLER.setProperty(from, GraphicObjectProperties.__GO_DATA_BOUNDS__,
                               new Double[] {0.0, 10.0, 0.0, 10.0, 0.0, 10.0});
        CONTROLLER.setProperty(to, GraphicObjectProperties.__GO_REAL_DATA_BOUNDS__,
                               new Double[] { -5.0, 5.0, 2.0, 8.0, -1.0, 20.0});

        AxesHandler.axesBound(from, to);

        Double[] merged = (Double[]) CONTROLLER.getProperty(to, GraphicObjectProperties.__GO_DATA_BOUNDS__);
        assertEquals(-5.0, merged[0], EPS); // min(0, -5)
        assertEquals(10.0, merged[1], EPS); // max(10, 5)
        assertEquals(0.0, merged[2], EPS);  // min(0, 2)
        assertEquals(10.0, merged[3], EPS); // max(10, 8)
        assertEquals(-1.0, merged[4], EPS); // min(0, -1)
        assertEquals(20.0, merged[5], EPS); // max(10, 20)
    }

    @Test
    void axesBoundIsANoOpWhenSourceAndTargetAreTheSame() {
        Integer axes = create(GraphicObject.Type.AXES);
        CONTROLLER.setProperty(axes, GraphicObjectProperties.__GO_DATA_BOUNDS__,
                               new Double[] {1.0, 2.0, 3.0, 4.0, 5.0, 6.0});
        AxesHandler.axesBound(axes, axes); // must return immediately, no exception
        Double[] bounds = (Double[]) CONTROLLER.getProperty(axes, GraphicObjectProperties.__GO_DATA_BOUNDS__);
        assertEquals(1.0, bounds[0], EPS);
        assertEquals(6.0, bounds[5], EPS);
    }
}
