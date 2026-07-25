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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.scilab.modules.graphic_objects.graphicController.GraphicController;
import org.scilab.modules.graphic_objects.graphicObject.GraphicObject;
import org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties;

/**
 * Hermetic unit tests for {@link LegendHandler}. All lookups run against the
 * pure-Java in-memory model through the {@link GraphicController} singleton
 * (headless, no views) - no OpenGL/native code is touched. The methods that
 * reach the renderer/projection ({@code dragLegend} does pure property
 * arithmetic and is exercised; nothing here calls CallRenderer or AxesDrawer).
 */
class LegendHandlerTest {

    private static final double EPS = 1e-9;
    private static final GraphicController CONTROLLER = GraphicController.getController();

    private static Integer create(GraphicObject.Type type) {
        return CONTROLLER.askObject(type);
    }

    private static void link(Integer parent, Integer child) {
        CONTROLLER.setGraphicObjectRelationship(parent, child);
    }

    /** Builds an axes carrying a legend whose LINKS/TEXT are wired for lookup. */
    private static Integer newLegendWith(Integer axes, Integer[] links, String[] texts) {
        Integer legend = create(GraphicObject.Type.LEGEND);
        link(axes, legend);
        CONTROLLER.setProperty(legend, GraphicObjectProperties.__GO_TEXT_ARRAY_DIMENSIONS__,
                               new Integer[] {texts.length, 1});
        CONTROLLER.setProperty(legend, GraphicObjectProperties.__GO_TEXT_STRINGS__, texts);
        CONTROLLER.setProperty(legend, GraphicObjectProperties.__GO_LINKS__, links);
        return legend;
    }

    @Test
    void searchLegendFindsALegendChildOfTheAxes() {
        Integer axes = create(GraphicObject.Type.AXES);
        Integer legend = create(GraphicObject.Type.LEGEND);
        link(axes, legend);
        assertEquals(legend, LegendHandler.searchLegend(axes));
    }

    @Test
    void searchLegendReturnsNullForNullUid() {
        assertNull(LegendHandler.searchLegend(null));
    }

    @Test
    void searchLegendReturnsNullWhenTheAxesHasNoLegend() {
        Integer axes = create(GraphicObject.Type.AXES);
        link(axes, create(GraphicObject.Type.POLYLINE));
        assertNull(LegendHandler.searchLegend(axes));
    }

    @Test
    void getLinksReturnsNullForNullLegendAndTheStoredLinksOtherwise() {
        assertNull(LegendHandler.getLinks(null));
        Integer axes = create(GraphicObject.Type.AXES);
        Integer p1 = create(GraphicObject.Type.POLYLINE);
        Integer p2 = create(GraphicObject.Type.POLYLINE);
        Integer legend = newLegendWith(axes, new Integer[] {p1, p2}, new String[] {"A", "B"});
        assertArrayEquals(new Integer[] {p1, p2}, LegendHandler.getLinks(legend));
    }

    @Test
    void getTextReturnsNullForNullLegendAndTheStoredTextOtherwise() {
        assertNull(LegendHandler.getText(null));
        Integer axes = create(GraphicObject.Type.AXES);
        Integer p1 = create(GraphicObject.Type.POLYLINE);
        Integer legend = newLegendWith(axes, new Integer[] {p1}, new String[] {"only"});
        assertArrayEquals(new String[] {"only"}, LegendHandler.getText(legend));
    }

    @Test
    void getPositionReturnsNullForNullLegendAndTheStoredPositionOtherwise() {
        assertNull(LegendHandler.getPosition(null));
        Integer legend = create(GraphicObject.Type.LEGEND);
        CONTROLLER.setProperty(legend, GraphicObjectProperties.__GO_POSITION__, new Double[] {0.3, 0.7});
        assertArrayEquals(new Double[] {0.3, 0.7}, LegendHandler.getPosition(legend));
    }

    @Test
    void getLegendTextPairsLinksToTextsInReverseOrder() {
        // Documented behaviour: text for the i-th link is text[links.length-i-1].
        Integer axes = create(GraphicObject.Type.AXES);
        Integer p1 = create(GraphicObject.Type.POLYLINE);
        Integer p2 = create(GraphicObject.Type.POLYLINE);
        newLegendWith(axes, new Integer[] {p1, p2}, new String[] {"first", "second"});

        assertEquals("second", LegendHandler.getLegendText(axes, p1));
        assertEquals("first", LegendHandler.getLegendText(axes, p2));
    }

    @Test
    void getLegendTextReturnsNullForNullArgumentsOrUnknownPolyline() {
        Integer axes = create(GraphicObject.Type.AXES);
        Integer p1 = create(GraphicObject.Type.POLYLINE);
        newLegendWith(axes, new Integer[] {p1}, new String[] {"x"});

        assertNull(LegendHandler.getLegendText(null, p1));
        assertNull(LegendHandler.getLegendText(axes, null));
        // A polyline that is not among the legend's links has no text.
        Integer stranger = create(GraphicObject.Type.POLYLINE);
        assertNull(LegendHandler.getLegendText(axes, stranger));
    }

    @Test
    void getLegendTextReturnsNullWhenThereIsNoLegend() {
        Integer axes = create(GraphicObject.Type.AXES);
        Integer p1 = create(GraphicObject.Type.POLYLINE);
        assertNull(LegendHandler.getLegendText(axes, p1));
    }

    @Test
    void dragLegendShiftsThePositionAndSwitchesToCoordinateMode() {
        Integer figure = create(GraphicObject.Type.FIGURE);
        Integer axes = create(GraphicObject.Type.AXES);
        Integer legend = create(GraphicObject.Type.LEGEND);
        link(figure, axes);
        link(axes, legend);

        CONTROLLER.setProperty(figure, GraphicObjectProperties.__GO_AXES_SIZE__, new Integer[] {100, 100});
        CONTROLLER.setProperty(axes, GraphicObjectProperties.__GO_AXES_BOUNDS__, new Double[] {0.0, 0.0, 0.5, 0.5});
        CONTROLLER.setProperty(legend, GraphicObjectProperties.__GO_POSITION__, new Double[] {0.0, 0.0});

        // diff = (next - pos) / (bounds[2|3] * size) = (20-10)/(0.5*100) = 0.2
        LegendHandler.dragLegend(legend, new Integer[] {10, 10}, new Integer[] {20, 20});

        Double[] pos = (Double[]) CONTROLLER.getProperty(legend, GraphicObjectProperties.__GO_POSITION__);
        assertEquals(0.2, pos[0], EPS);
        assertEquals(0.2, pos[1], EPS);
        // Dragging forces the BY_COORDINATES location (10).
        assertEquals(Integer.valueOf(10),
                     CONTROLLER.getProperty(legend, GraphicObjectProperties.__GO_LEGEND_LOCATION__));
    }

    @Test
    void dragLegendIsANoOpWhenTheTargetIsOutsideTheDrawableArea() {
        Integer figure = create(GraphicObject.Type.FIGURE);
        Integer axes = create(GraphicObject.Type.AXES);
        Integer legend = create(GraphicObject.Type.LEGEND);
        link(figure, axes);
        link(axes, legend);

        CONTROLLER.setProperty(figure, GraphicObjectProperties.__GO_AXES_SIZE__, new Integer[] {100, 100});
        CONTROLLER.setProperty(axes, GraphicObjectProperties.__GO_AXES_BOUNDS__, new Double[] {0.0, 0.0, 0.5, 0.5});
        CONTROLLER.setProperty(legend, GraphicObjectProperties.__GO_POSITION__, new Double[] {0.4, 0.4});

        // next[0] = 95 > size[0]-10 = 90 -> early return, position untouched.
        LegendHandler.dragLegend(legend, new Integer[] {10, 10}, new Integer[] {95, 20});

        Double[] pos = (Double[]) CONTROLLER.getProperty(legend, GraphicObjectProperties.__GO_POSITION__);
        assertArrayEquals(new Double[] {0.4, 0.4}, pos);
    }

    @Test
    void dragLegendWithoutAnAxesOrFigureAncestorDoesNothing() {
        // A legend with no parent chain: searchParent returns null -> early return.
        Integer legend = create(GraphicObject.Type.LEGEND);
        CONTROLLER.setProperty(legend, GraphicObjectProperties.__GO_POSITION__, new Double[] {0.1, 0.2});
        LegendHandler.dragLegend(legend, new Integer[] {0, 0}, new Integer[] {1, 1});
        assertArrayEquals(new Double[] {0.1, 0.2},
                          (Double[]) CONTROLLER.getProperty(legend, GraphicObjectProperties.__GO_POSITION__));
    }
}
