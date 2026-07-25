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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.scilab.modules.graphic_objects.graphicController.GraphicController;
import org.scilab.modules.graphic_objects.graphicObject.GraphicObject;

/**
 * Hermetic unit tests for the controller-only surface of {@link PolylineHandler}:
 * the {@code getInstance} singleton and {@code deleteAll} tree pruning. Objects
 * are built through the pure-Java {@link GraphicController} (headless, no views).
 * The {@code dragPolyline} method reaches CallRenderer (JNI) and PolylineData and
 * is out of scope.
 */
class PolylineHandlerTest {

    private static final GraphicController CONTROLLER = GraphicController.getController();

    private static Integer create(GraphicObject.Type type) {
        return CONTROLLER.askObject(type);
    }

    private static void link(Integer parent, Integer child) {
        CONTROLLER.setGraphicObjectRelationship(parent, child);
    }

    @Test
    void getInstanceReturnsAStableSingleton() {
        PolylineHandler a = PolylineHandler.getInstance();
        PolylineHandler b = PolylineHandler.getInstance();
        assertNotNull(a);
        assertSame(a, b, "getInstance must return the same instance every time");
    }

    @Test
    void deleteAllRemovesTheDrawableChildrenOfAnAxes() {
        Integer axes = create(GraphicObject.Type.AXES);
        Integer poly = create(GraphicObject.Type.POLYLINE);
        link(axes, poly);
        assertTrue(CommonHandler.objectExists(poly));

        PolylineHandler.getInstance().deleteAll(axes);

        assertFalse(CommonHandler.objectExists(poly), "the polyline child is deleted");
        assertTrue(CommonHandler.objectExists(axes), "the axes itself survives");
    }

    @Test
    void deleteAllRecursesThroughContainersDownToTheAxes() {
        Integer figure = create(GraphicObject.Type.FIGURE);
        Integer axes = create(GraphicObject.Type.AXES);
        Integer poly = create(GraphicObject.Type.POLYLINE);
        link(figure, axes);
        link(axes, poly);

        // deleteAll on a non-axes container recurses until it reaches the axes,
        // whose drawable children are then removed.
        PolylineHandler.getInstance().deleteAll(figure);

        assertFalse(CommonHandler.objectExists(poly), "the nested polyline is deleted");
        assertTrue(CommonHandler.objectExists(axes), "intermediate containers survive");
    }
}
