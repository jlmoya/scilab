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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.scilab.modules.graphic_objects.graphicController.GraphicController;
import org.scilab.modules.graphic_objects.graphicObject.GraphicObject;
import org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties;

/**
 * Hermetic unit tests for {@link CommonHandler}. Two families are covered:
 * the pure log-scaling helpers (plain arithmetic, no engine) and the editor
 * helpers that go through the {@link GraphicController} - exercised here
 * against the pure-Java in-memory model (headless singleton, no views), so no
 * OpenGL or native engine is involved. The data-model helpers that reach JNI
 * ({@code duplicate}/{@code computeIntersection} via PolylineData) are out of
 * scope.
 */
class CommonHandlerTest {

    private static final double EPS = 1e-9;
    private static final GraphicController CONTROLLER = GraphicController.getController();

    private static Integer create(GraphicObject.Type type) {
        return CONTROLLER.askObject(type);
    }

    @Test
    void scalarLogScaleOnlyAppliesWhenFlagged() {
        assertEquals(2.0, CommonHandler.logScale(100.0, true), EPS);
        assertEquals(100.0, CommonHandler.logScale(100.0, false), EPS);
    }

    @Test
    void scalarInverseLogScaleOnlyAppliesWhenFlagged() {
        assertEquals(100.0, CommonHandler.InverseLogScale(2.0, true), 1e-6);
        assertEquals(2.0, CommonHandler.InverseLogScale(2.0, false), EPS);
    }

    @Test
    void scalarLogAndInverseAreMutualInverses() {
        double v = 37.5;
        assertEquals(v, CommonHandler.InverseLogScale(CommonHandler.logScale(v, true), true), 1e-6);
    }

    @Test
    void arrayToLogScaleReturnsANewArrayWhenScalingAndLeavesInputUntouched() {
        double[] input = {10.0, 100.0, 1000.0};
        double[] out = CommonHandler.toLogScale(input, true);
        assertNotSame(input, out, "scaling must not mutate the caller's array");
        assertArrayEquals(new double[] {1.0, 2.0, 3.0}, out, EPS);
        assertArrayEquals(new double[] {10.0, 100.0, 1000.0}, input, EPS);
    }

    @Test
    void arrayToLogScaleReturnsSameArrayWhenNotScaling() {
        double[] input = {10.0, 100.0, 1000.0};
        double[] out = CommonHandler.toLogScale(input, false);
        assertSame(input, out, "no-op scaling should pass the array straight through");
    }

    @Test
    void arrayToInverseLogScaleRoundTripsWithToLogScale() {
        double[] input = {5.0, 50.0, 500.0};
        double[] logged = CommonHandler.toLogScale(input, true);
        double[] back = CommonHandler.toInverseLogScale(logged, true);
        assertArrayEquals(input, back, 1e-6);
    }

    @Test
    void arrayToInverseLogScaleReturnsSameArrayWhenNotScaling() {
        double[] input = {1.0, 2.0, 3.0};
        assertSame(input, CommonHandler.toInverseLogScale(input, false));
    }

    @Test
    void tripletToLogScaleAppliesPerAxisFlagsInPlace() {
        // Two xyz points; log x and z, keep y.
        double[] data = {10.0, 100.0, 1000.0, 10.0, 100.0, 1000.0};
        CommonHandler.toLogScale(data, new boolean[] {true, false, true});
        assertArrayEquals(new double[] {1.0, 100.0, 3.0, 1.0, 100.0, 3.0}, data, EPS);
    }

    @Test
    void tripletToLogScaleWithNoFlagsIsIdentity() {
        double[] data = {10.0, 100.0, 1000.0};
        CommonHandler.toLogScale(data, new boolean[] {false, false, false});
        assertArrayEquals(new double[] {10.0, 100.0, 1000.0}, data, EPS);
    }

    @Test
    void tripletInverseUndoesTripletLog() {
        double[] data = {10.0, 7.0, 1000.0};
        boolean[] flags = {true, false, true};
        CommonHandler.toLogScale(data, flags);
        CommonHandler.toInverseLogScale(data, flags);
        assertArrayEquals(new double[] {10.0, 7.0, 1000.0}, data, 1e-6);
    }

    /* ---- controller-backed editor helpers (in-memory model) ---- */

    @Test
    void objectExistsIsTrueForARegisteredUidAndFalseOtherwise() {
        Integer uid = create(GraphicObject.Type.POLYLINE);
        assertTrue(CommonHandler.objectExists(uid));
        assertFalse(CommonHandler.objectExists(null), "null uid does not exist");
        assertFalse(CommonHandler.objectExists(Integer.valueOf(-987654321)), "unknown uid does not exist");
    }

    @Test
    void visibilityRoundTripsThroughSetAndIsVisible() {
        Integer poly = create(GraphicObject.Type.POLYLINE);
        CommonHandler.setVisible(poly, false);
        assertFalse(CommonHandler.isVisible(poly));
        CommonHandler.setVisible(poly, true);
        assertTrue(CommonHandler.isVisible(poly));
    }

    @Test
    void lineAndMarkModeReadBackTheModelFlags() {
        Integer poly = create(GraphicObject.Type.POLYLINE);
        CONTROLLER.setProperty(poly, GraphicObjectProperties.__GO_LINE_MODE__, true);
        CONTROLLER.setProperty(poly, GraphicObjectProperties.__GO_MARK_MODE__, false);
        assertTrue(CommonHandler.isLineEnabled(poly));
        assertFalse(CommonHandler.isMarkEnabled(poly));

        CONTROLLER.setProperty(poly, GraphicObjectProperties.__GO_LINE_MODE__, false);
        CONTROLLER.setProperty(poly, GraphicObjectProperties.__GO_MARK_MODE__, true);
        assertFalse(CommonHandler.isLineEnabled(poly));
        assertTrue(CommonHandler.isMarkEnabled(poly));
    }

    @Test
    void styleAndMarkGettersReadBackTheModelValues() {
        Integer poly = create(GraphicObject.Type.POLYLINE);
        CONTROLLER.setProperty(poly, GraphicObjectProperties.__GO_POLYLINE_STYLE__, 5);
        CONTROLLER.setProperty(poly, GraphicObjectProperties.__GO_MARK_STYLE__, 3);
        CONTROLLER.setProperty(poly, GraphicObjectProperties.__GO_MARK_SIZE__, 7);
        assertEquals(5, CommonHandler.getStyle(poly));
        assertEquals(Integer.valueOf(3), CommonHandler.getMarkStyle(poly));
        assertEquals(Integer.valueOf(7), CommonHandler.getMarkSize(poly));
    }

    @Test
    void setSelectedIsANullSafeNoOpAndOtherwiseSetsTheFlag() {
        // Null uid must not throw.
        CommonHandler.setSelected(null, true);

        Integer poly = create(GraphicObject.Type.POLYLINE);
        CommonHandler.setSelected(poly, true);
        assertEquals(Boolean.TRUE, CONTROLLER.getProperty(poly, GraphicObjectProperties.__GO_SELECTED__));
        CommonHandler.setSelected(poly, false);
        assertEquals(Boolean.FALSE, CONTROLLER.getProperty(poly, GraphicObjectProperties.__GO_SELECTED__));
    }

    @Test
    void getParentReturnsTheLinkedParentUid() {
        Integer axes = create(GraphicObject.Type.AXES);
        Integer poly = create(GraphicObject.Type.POLYLINE);
        CONTROLLER.setGraphicObjectRelationship(axes, poly);
        assertEquals(axes, CommonHandler.getParent(poly));
    }

    @Test
    void backgroundGetterReadsBackTheStoredColorIndex() {
        Integer poly = create(GraphicObject.Type.POLYLINE);
        CONTROLLER.setProperty(poly, GraphicObjectProperties.__GO_BACKGROUND__, 4);
        assertEquals(Integer.valueOf(4), CommonHandler.getBackground(poly));
    }

    @Test
    void colorMapCompareAndCloneWorkAcrossFigures() {
        Integer f1 = create(GraphicObject.Type.FIGURE);
        Integer f2 = create(GraphicObject.Type.FIGURE);
        Double[] cmA = {0.0, 0.0, 0.0, 1.0, 1.0, 1.0};
        Double[] cmB = {0.0, 0.0, 0.0, 0.5, 0.5, 0.5};
        CONTROLLER.setProperty(f1, GraphicObjectProperties.__GO_COLORMAP__, cmA);
        CONTROLLER.setProperty(f2, GraphicObjectProperties.__GO_COLORMAP__, cmB);

        assertArrayEquals(cmA, CommonHandler.getColorMap(f1));
        assertFalse(CommonHandler.cmpColorMap(f1, f2), "different colormaps compare unequal");

        // Cloning f1's colormap onto f2 makes them compare equal.
        CommonHandler.cloneColorMap(f1, f2);
        assertTrue(CommonHandler.cmpColorMap(f1, f2), "after cloning they are equal");
    }

    @Test
    void cutDetachesAnObjectFromItsParent() {
        Integer axes = create(GraphicObject.Type.AXES);
        Integer poly = create(GraphicObject.Type.POLYLINE);
        CONTROLLER.setGraphicObjectRelationship(axes, poly);
        assertEquals(axes, CommonHandler.getParent(poly));

        CommonHandler.cut(poly);
        assertEquals(Integer.valueOf(0), CommonHandler.getParent(poly), "cut reparents to the root (0)");
    }

    @Test
    void deleteRemovesTheObjectFromTheModel() {
        Integer poly = create(GraphicObject.Type.POLYLINE);
        assertTrue(CommonHandler.objectExists(poly));
        CommonHandler.delete(poly);
        assertFalse(CommonHandler.objectExists(poly), "a deleted object no longer exists");
    }
}
