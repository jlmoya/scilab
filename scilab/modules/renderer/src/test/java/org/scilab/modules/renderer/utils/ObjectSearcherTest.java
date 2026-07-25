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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.scilab.modules.graphic_objects.graphicController.GraphicController;
import org.scilab.modules.graphic_objects.graphicObject.GraphicObject;
import org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties;

/**
 * Hermetic unit tests for {@link ObjectSearcher}. These exercise the real
 * tree-walking logic against an in-memory graphic model: objects are created
 * and linked through the pure-Java {@link GraphicController} singleton (headless,
 * no views registered), so no OpenGL, Swing event loop or native engine is
 * involved. Each test builds its own disjoint subtree with fresh UIDs, so the
 * shared model does not couple the tests.
 */
class ObjectSearcherTest {

    private static final GraphicController CONTROLLER = GraphicController.getController();

    private static Integer create(GraphicObject.Type type) {
        return CONTROLLER.askObject(type);
    }

    private static void link(Integer parent, Integer child) {
        CONTROLLER.setGraphicObjectRelationship(parent, child);
    }

    private static Set<Integer> setOf(Integer[] a) {
        return a == null ? new HashSet<Integer>() : new HashSet<Integer>(Arrays.asList(a));
    }

    @Test
    void searchReturnsEveryDirectChildOfTheRequestedType() {
        Integer container = create(GraphicObject.Type.COMPOUND);
        Integer p1 = create(GraphicObject.Type.POLYLINE);
        Integer p2 = create(GraphicObject.Type.POLYLINE);
        Integer rect = create(GraphicObject.Type.RECTANGLE);
        link(container, p1);
        link(container, p2);
        link(container, rect);

        Integer[] found = new ObjectSearcher().search(container, GraphicObjectProperties.__GO_POLYLINE__);
        assertEquals(2, found.length, "both polylines are found, the rectangle is not");
        assertEquals(new HashSet<Integer>(Arrays.asList(p1, p2)), setOf(found));
    }

    @Test
    void searchRecursesIntoNonMatchingChildrenButNotIntoMatches() {
        // container -> inner(compound) -> polyline
        Integer container = create(GraphicObject.Type.COMPOUND);
        Integer inner = create(GraphicObject.Type.COMPOUND);
        Integer polyline = create(GraphicObject.Type.POLYLINE);
        link(container, inner);
        link(inner, polyline);

        // Searching for a polyline descends through the inner compound.
        Integer[] polys = new ObjectSearcher().search(container, GraphicObjectProperties.__GO_POLYLINE__);
        assertEquals(1, polys.length);
        assertEquals(polyline, polys[0]);

        // Searching for a compound stops at the first match (does not recurse
        // into it), so only the inner compound - a child of the root - is found.
        Integer[] compounds = new ObjectSearcher().search(container, GraphicObjectProperties.__GO_COMPOUND__);
        assertEquals(1, compounds.length);
        assertEquals(inner, compounds[0]);
    }

    @Test
    void searchReturnsNullWhenNothingMatches() {
        Integer container = create(GraphicObject.Type.COMPOUND);
        link(container, create(GraphicObject.Type.RECTANGLE));
        link(container, create(GraphicObject.Type.ARC));

        assertNull(new ObjectSearcher().search(container, GraphicObjectProperties.__GO_POLYLINE__),
                   "no polyline in the subtree -> null, not an empty array");
    }

    @Test
    void searchOnAChildlessRootReturnsNull() {
        Integer lonely = create(GraphicObject.Type.COMPOUND);
        assertNull(new ObjectSearcher().search(lonely, GraphicObjectProperties.__GO_POLYLINE__));
    }

    @Test
    void searchMultipleFindsAnyOfTheGivenTypes() {
        Integer container = create(GraphicObject.Type.COMPOUND);
        Integer poly = create(GraphicObject.Type.POLYLINE);
        Integer arc = create(GraphicObject.Type.ARC);
        Integer rect = create(GraphicObject.Type.RECTANGLE);
        link(container, poly);
        link(container, arc);
        link(container, rect);

        Integer[] found = new ObjectSearcher().searchMultiple(container,
                          new Integer[] {GraphicObjectProperties.__GO_POLYLINE__, GraphicObjectProperties.__GO_ARC__});
        assertEquals(new HashSet<Integer>(Arrays.asList(poly, arc)), setOf(found),
                     "the polyline and arc match; the rectangle does not");
    }

    @Test
    void searchMultipleReturnsNullWhenNoTypeMatches() {
        Integer container = create(GraphicObject.Type.COMPOUND);
        link(container, create(GraphicObject.Type.RECTANGLE));

        assertNull(new ObjectSearcher().searchMultiple(container,
                   new Integer[] {GraphicObjectProperties.__GO_POLYLINE__, GraphicObjectProperties.__GO_ARC__}));
    }

    @Test
    void searchParentFindsTheNearestAncestorOfAType() {
        Integer figure = create(GraphicObject.Type.FIGURE);
        Integer axes = create(GraphicObject.Type.AXES);
        Integer polyline = create(GraphicObject.Type.POLYLINE);
        link(figure, axes);
        link(axes, polyline);

        ObjectSearcher searcher = new ObjectSearcher();
        assertEquals(axes, searcher.searchParent(polyline, GraphicObjectProperties.__GO_AXES__));
        assertEquals(figure, searcher.searchParent(polyline, GraphicObjectProperties.__GO_FIGURE__));
    }

    @Test
    void searchParentReturnsNullForANullUid() {
        assertNull(new ObjectSearcher().searchParent(null, GraphicObjectProperties.__GO_AXES__));
    }

    @Test
    void searchInDatatipModeCollectsAPolylinesDatatipsDirectly() {
        Integer polyline = create(GraphicObject.Type.POLYLINE);
        Integer tip1 = create(GraphicObject.Type.DATATIP);
        Integer tip2 = create(GraphicObject.Type.DATATIP);
        CONTROLLER.setProperty(polyline, GraphicObjectProperties.__GO_DATATIPS__, new Integer[] {tip1, tip2});

        Integer[] tips = new ObjectSearcher().search(polyline, GraphicObjectProperties.__GO_DATATIP__, true);
        assertEquals(2, tips.length);
        assertEquals(new HashSet<Integer>(Arrays.asList(tip1, tip2)), setOf(tips));
    }

    @Test
    void searchInDatatipModeRecursesThroughContainersToReachPolylines() {
        Integer container = create(GraphicObject.Type.COMPOUND);
        Integer polyline = create(GraphicObject.Type.POLYLINE);
        Integer tip = create(GraphicObject.Type.DATATIP);
        link(container, polyline);
        CONTROLLER.setProperty(polyline, GraphicObjectProperties.__GO_DATATIPS__, new Integer[] {tip});

        Integer[] tips = new ObjectSearcher().search(container, GraphicObjectProperties.__GO_DATATIP__, true);
        assertEquals(1, tips.length);
        assertTrue(setOf(tips).contains(tip));
    }
}
