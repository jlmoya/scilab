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

package org.scilab.modules.graphic_objects.figure;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import org.scilab.modules.graphic_objects.figure.Figure.BarType;
import org.scilab.modules.graphic_objects.figure.Figure.CanvasProperty;
import org.scilab.modules.graphic_objects.figure.Figure.FigureNameProperty;
import org.scilab.modules.graphic_objects.figure.Figure.PixelDrawingMode;
import org.scilab.modules.graphic_objects.figure.Figure.RenderingModeProperty;
import org.scilab.modules.graphic_objects.figure.Figure.RotationType;
import org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties;
import org.scilab.modules.graphic_objects.graphicObject.GraphicObject.UpdateStatus;

import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_BACKGROUND__;

/**
 * Hermetic unit tests for {@link Figure}: the top-level figure object with its
 * many change-tracking getters/setters and nested rendering / canvas state.
 */
public class FigureTest {

    @Test
    public void constructorDefaults() {
        Figure f = new Figure();
        assertEquals(Integer.valueOf(0), f.getBackground());
        assertEquals("", f.getName());
        assertEquals(Integer.valueOf(0), f.getId());
        assertEquals("", f.getInfoMessage());
        assertEquals("", f.getIcon());
        assertTrue(f.getResize());
        assertTrue(f.getDockable());
        assertTrue(f.getToolbarVisible());
        assertTrue(f.getMenubarVisible());
        assertTrue(f.getInfobarVisible());
        assertTrue(f.hasDefaultAxes());
        assertFalse(f.getAutoResize());
        assertEquals(Integer.valueOf(1), f.getToolbar());  // FIGURE ordinal
        assertEquals(Integer.valueOf(1), f.getMenubar());  // FIGURE ordinal
        assertEquals(Integer.valueOf(0), f.getRotation()); // UNARY ordinal
        assertEquals(Integer.valueOf(3), f.getAntialiasing());
        assertTrue(f.getImmediateDrawing());
        assertEquals(Integer.valueOf(3), f.getPixelDrawingMode()); // COPY ordinal
        assertEquals(Integer.valueOf(0), f.getLayout());   // NONE ordinal
        assertTrue(f.isLayoutSettable());
    }

    @Test
    public void typeIsFigure() {
        assertEquals(GraphicObjectProperties.__GO_FIGURE__, new Figure().getType());
    }

    @Test
    public void backgroundTracksChange() {
        Figure f = new Figure();
        assertEquals(UpdateStatus.NoChange, f.setBackground(0));
        assertEquals(UpdateStatus.Success, f.setBackground(7));
        assertEquals(Integer.valueOf(7), f.getBackground());
        assertEquals(UpdateStatus.NoChange, f.setBackground(7));
    }

    @Test
    public void nameAndIdBehaviour() {
        Figure f = new Figure();
        assertEquals(UpdateStatus.Success, f.setName("plot"));
        assertEquals("plot", f.getName());
        assertEquals(UpdateStatus.NoChange, f.setName("plot"));
        // setId always reports Success (it must always update views).
        assertEquals(UpdateStatus.Success, f.setId(5));
        assertEquals(UpdateStatus.Success, f.setId(5));
        assertEquals(Integer.valueOf(5), f.getId());
    }

    @Test
    public void positionAndSizeTrackChange() {
        Figure f = new Figure();
        assertEquals(UpdateStatus.Success, f.setPosition(new Integer[] {10, 20}));
        assertArrayEquals(new Integer[] {10, 20}, f.getPosition());
        assertEquals(UpdateStatus.NoChange, f.setPosition(new Integer[] {10, 20}));

        assertEquals(UpdateStatus.Success, f.setSize(new Integer[] {640, 480}));
        assertArrayEquals(new Integer[] {640, 480}, f.getSize());
        assertEquals(UpdateStatus.NoChange, f.setSize(new Integer[] {640, 480}));
    }

    @Test
    public void axesSizeAlwaysReportsSuccessEvenWhenUnchanged() {
        // Characterisation: setAxesSize deliberately returns Success even when the
        // value is unchanged, so the information can be re-broadcast.
        Figure f = new Figure();
        f.setAxesSize(new Integer[] {100, 100});
        assertEquals(UpdateStatus.Success, f.setAxesSize(new Integer[] {100, 100}));
        assertArrayEquals(new Integer[] {100, 100}, f.getAxesSize());
    }

    @Test
    public void viewportTracksChange() {
        Figure f = new Figure();
        assertEquals(UpdateStatus.Success, f.setViewport(new Integer[] {1, 2}));
        assertArrayEquals(new Integer[] {1, 2}, f.getViewport());
        assertEquals(UpdateStatus.NoChange, f.setViewport(new Integer[] {1, 2}));
    }

    @Test
    public void rotationIntSetterGuardsAgainstInvalidValues() {
        Figure f = new Figure();
        assertEquals(UpdateStatus.Success, f.setRotation(1)); // MULTIPLE
        assertEquals(Integer.valueOf(1), f.getRotation());
        assertEquals(RotationType.MULTIPLE, f.getRotationAsEnum());
        // Out-of-range int -> intToEnum null -> guarded to NoChange (no corruption).
        assertEquals(UpdateStatus.NoChange, f.setRotation(99));
        assertEquals(RotationType.MULTIPLE, f.getRotationAsEnum());
    }

    @Test
    public void rotationTypeConverters() {
        assertEquals(RotationType.UNARY, RotationType.intToEnum(0));
        assertEquals(RotationType.MULTIPLE, RotationType.intToEnum(1));
        assertNull(RotationType.intToEnum(2));
        assertEquals(RotationType.MULTIPLE, RotationType.stringToEnum("multiple"));
        assertEquals(RotationType.UNARY, RotationType.stringToEnum("anything"));
        assertEquals("multiple", RotationType.enumToString(RotationType.MULTIPLE));
        assertEquals("unary", RotationType.enumToString(RotationType.UNARY));
    }

    @Test
    public void barTypeConverters() {
        assertEquals(BarType.NONE, BarType.intToEnum(0));
        assertEquals(BarType.FIGURE, BarType.intToEnum(1));
        // Unknown ints fall through the switch default to NONE.
        assertEquals(BarType.NONE, BarType.intToEnum(2));
        assertEquals(BarType.FIGURE, BarType.stringToEnum("figure"));
        assertEquals(BarType.NONE, BarType.stringToEnum("x"));
        assertEquals("figure", BarType.enumToString(BarType.FIGURE));
        assertEquals("none", BarType.enumToString(BarType.NONE));
    }

    @Test
    public void toolbarAndMenubarSetters() {
        Figure f = new Figure();
        assertEquals(UpdateStatus.Success, f.setToolbar(0)); // NONE
        assertEquals(Integer.valueOf(0), f.getToolbar());
        assertEquals(UpdateStatus.NoChange, f.setToolbar(0));

        assertEquals(UpdateStatus.Success, f.setMenubar(0));
        assertEquals(BarType.NONE, f.getMenubarAsEnum());
    }

    @Test
    public void pixelDrawingModeConvertersAndSetter() {
        assertEquals(PixelDrawingMode.CLEAR, PixelDrawingMode.intToEnum(0));
        assertEquals(PixelDrawingMode.COPY, PixelDrawingMode.intToEnum(3));
        assertEquals(PixelDrawingMode.SET, PixelDrawingMode.intToEnum(15));
        assertNull(PixelDrawingMode.intToEnum(16));
        assertNull(PixelDrawingMode.intToEnum(-1));

        Figure f = new Figure();
        // Default is COPY(3): setting 3 is a no-op, 6 (XOR) is a change.
        assertEquals(UpdateStatus.NoChange, f.setPixelDrawingMode(3));
        assertEquals(UpdateStatus.Success, f.setPixelDrawingMode(6));
        assertEquals(Integer.valueOf(6), f.getPixelDrawingMode());
    }

    @Test
    public void antialiasingAndImmediateDrawingTrackChange() {
        Figure f = new Figure();
        assertEquals(UpdateStatus.NoChange, f.setAntialiasing(3));
        assertEquals(UpdateStatus.Success, f.setAntialiasing(0));
        assertEquals(Integer.valueOf(0), f.getAntialiasing());

        assertEquals(UpdateStatus.NoChange, f.setImmediateDrawing(true));
        assertEquals(UpdateStatus.Success, f.setImmediateDrawing(false));
        assertFalse(f.getImmediateDrawing());
    }

    @Test
    public void layoutCanBeSetOnceThenIsLocked() {
        Figure f = new Figure();
        // From NONE to NONE is a no-op.
        assertEquals(UpdateStatus.NoChange, f.setLayout(0));
        // From NONE to a real layout succeeds and locks further changes.
        assertEquals(UpdateStatus.Success, f.setLayout(1)); // GRIDBAG
        assertEquals(Integer.valueOf(1), f.getLayout());
        assertFalse(f.isLayoutSettable());
        // A second attempt to change the layout fails.
        assertEquals(UpdateStatus.Fail, f.setLayout(2));
        assertEquals(Integer.valueOf(1), f.getLayout());
    }

    @Test
    public void gridAndBorderPaddingValidateLengthAndTrackChange() {
        Figure f = new Figure();
        assertEquals(UpdateStatus.Success, f.setGridOptGrid(new Integer[] {2, 3}));
        assertArrayEquals(new Integer[] {2, 3}, f.getGridOptGrid());
        assertEquals(UpdateStatus.NoChange, f.setGridOptGrid(new Integer[] {2, 3}));
        // Wrong length -> Fail.
        assertEquals(UpdateStatus.Fail, f.setGridOptGrid(new Integer[] {1}));

        assertEquals(UpdateStatus.Success, f.setBorderOptPadding(new Integer[] {4, 5}));
        assertArrayEquals(new Integer[] {4, 5}, f.getBorderOptPadding());
    }

    @Test
    public void boolFlagSettersTrackChange() {
        Figure f = new Figure();
        assertEquals(UpdateStatus.NoChange, f.setResize(true));
        assertEquals(UpdateStatus.Success, f.setResize(false));
        assertFalse(f.getResize());

        assertEquals(UpdateStatus.NoChange, f.setDockable(true));
        assertEquals(UpdateStatus.Success, f.setDockable(false));

        assertEquals(UpdateStatus.NoChange, f.setDefaultAxes(true));
        assertEquals(UpdateStatus.Success, f.setDefaultAxes(false));
        assertFalse(f.hasDefaultAxes());

        assertEquals(UpdateStatus.NoChange, f.setAutoResize(false));
        assertEquals(UpdateStatus.Success, f.setAutoResize(true));
    }

    @Test
    public void stringCallbackAndIconSetters() {
        Figure f = new Figure();
        assertEquals(UpdateStatus.Success, f.setResizeFcn("onResize"));
        assertEquals("onResize", f.getResizeFcn());
        assertEquals(UpdateStatus.NoChange, f.setResizeFcn("onResize"));

        assertEquals(UpdateStatus.Success, f.setCloseRequestFcn("onClose"));
        assertEquals("onClose", f.getCloseRequestFcn());

        assertEquals(UpdateStatus.Success, f.setInfoMessage("hi"));
        assertEquals("hi", f.getInfoMessage());

        assertEquals(UpdateStatus.Success, f.setIcon("icon.png"));
        assertEquals("icon.png", f.getIcon());
        assertEquals(UpdateStatus.NoChange, f.setIcon("icon.png"));
    }

    @Test
    public void eventHandlerStringAndEnable() {
        Figure f = new Figure();
        assertEquals("", f.getEventHandlerString());
        assertFalse(f.getEventHandlerEnable());
        assertEquals(UpdateStatus.Success, f.setEventHandlerString("handler"));
        assertEquals("handler", f.getEventHandlerString());
        assertEquals(UpdateStatus.NoChange, f.setEventHandlerString("handler"));
        assertEquals(UpdateStatus.Success, f.setEventHandlerEnable(true));
        assertTrue(f.getEventHandlerEnable());
    }

    @Test
    public void publicEnumPropertyDispatchRoundTrips() {
        Figure f = new Figure();
        // Canvas / name / rendering / dimensions all use public property enums.
        assertEquals(UpdateStatus.Success, f.setProperty(CanvasProperty.AUTORESIZE, Boolean.TRUE));
        assertEquals(Boolean.TRUE, f.getProperty(CanvasProperty.AUTORESIZE));

        f.setProperty(FigureNameProperty.NAME, "abc");
        assertEquals("abc", f.getProperty(FigureNameProperty.NAME));

        f.setProperty(RenderingModeProperty.ANTIALIASING, Integer.valueOf(1));
        assertEquals(Integer.valueOf(1), f.getProperty(RenderingModeProperty.ANTIALIASING));
    }

    @Test
    public void privateFigurePropertyDispatchViaNameRoundTrips() {
        Figure f = new Figure();
        // FigureProperty is private; reach it through getPropertyFromName.
        Object bg = f.getPropertyFromName(__GO_BACKGROUND__);
        assertEquals(UpdateStatus.Success, f.setProperty(bg, Integer.valueOf(9)));
        assertEquals(Integer.valueOf(9), f.getProperty(bg));
    }

    @Test
    public void cloneProducesIndependentValidCopy() {
        Figure f = new Figure();
        f.setBackground(42);
        f.setName("orig");

        Figure copy = f.clone();
        assertNotSame(f, copy);
        assertEquals(GraphicObjectProperties.__GO_FIGURE__, copy.getType());
        assertEquals(Integer.valueOf(42), copy.getBackground());
        // clone() forces the copy valid.
        assertTrue(copy.isValid());

        // The copied figure name is a distinct object: mutating it leaves the
        // original untouched.
        copy.setName("changed");
        assertEquals("orig", f.getName());
    }
}
