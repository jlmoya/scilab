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

package org.scilab.modules.graphic_objects.axes;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import org.scilab.modules.graphic_objects.axes.Axes.GridPosition;
import org.scilab.modules.graphic_objects.axes.AxisProperty.AxisLocation;
import org.scilab.modules.graphic_objects.axes.Camera.ViewType;
import org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties;
import org.scilab.modules.graphic_objects.graphicObject.GraphicObject.UpdateStatus;

import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.*;

/**
 * Hermetic unit tests for the {@link Axes} graphic object. These drive the
 * plain Java model directly (no GraphicController), covering construction
 * defaults, the sub-object (axis/box/camera) delegation surface, and clone
 * independence. The controller-dependent path (setRotationAngles) is
 * deliberately avoided.
 */
public class AxesTest {

    @Test
    public void gridPositionIntToEnum() {
        assertEquals(GridPosition.BACKGROUND, GridPosition.intToEnum(0));
        assertEquals(GridPosition.FOREGROUND, GridPosition.intToEnum(1));
        assertNull(GridPosition.intToEnum(2));
        assertNull(GridPosition.intToEnum(-1));
    }

    @Test
    public void constructorDefaults() {
        Axes a = new Axes();
        assertEquals(GridPosition.FOREGROUND, a.getGridPositionAsEnum());
        assertEquals(Integer.valueOf(1), a.getGridPosition());
        assertFalse(a.getAutoClear());
        assertFalse(a.getFilled());
        assertEquals(Integer.valueOf(0), a.getBackground());
        assertTrue(a.getAutoMargins());
        assertEquals(Integer.valueOf(0), a.getTitle());
        assertEquals(Integer.valueOf(0), a.getView()); // VIEW_2D
        assertEquals(Integer.valueOf(0), a.getBoxType()); // OFF
        assertEquals(Integer.valueOf(1), a.getArcDrawingMethod()); // LINES
        assertNotNull(a.getColorMap());
    }

    @Test
    public void typeIsAxes() {
        assertEquals(Integer.valueOf(GraphicObjectProperties.__GO_AXES__), new Axes().getType());
    }

    @Test
    public void holdsThreeDistinctAxisProperties() {
        Axes a = new Axes();
        assertEquals(3, a.getAxes().length);
        assertNotNull(a.getXAxis());
        assertNotNull(a.getYAxis());
        assertNotNull(a.getZAxis());
        assertSame(a.getXAxis(), a.getAxes()[0]);
        assertSame(a.getYAxis(), a.getAxes()[1]);
        assertSame(a.getZAxis(), a.getAxes()[2]);
        assertNotSame(a.getXAxis(), a.getYAxis());
    }

    @Test
    public void xAxisVisibilityDelegatesToFirstAxis() {
        Axes a = new Axes();
        assertFalse(a.getXAxisVisible());
        assertEquals(UpdateStatus.Success, a.setXAxisVisible(true));
        assertTrue(a.getXAxisVisible());
        // The delegate carries state: the X axis object now reads visible too.
        assertTrue(a.getXAxis().getVisible());
    }

    @Test
    public void xAxisLocationConvertsThroughEnum() {
        Axes a = new Axes();
        // Default axis location is ORIGIN (ordinal 3).
        assertEquals(Integer.valueOf(3), a.getXAxisLocation());
        assertEquals(UpdateStatus.Success, a.setXAxisLocation(1)); // TOP
        assertEquals(AxisLocation.TOP, a.getXAxisLocationAsEnum());
        assertEquals(Integer.valueOf(1), a.getXAxisLocation());
    }

    @Test
    public void gridPositionSetterRoundTrips() {
        Axes a = new Axes();
        assertEquals(UpdateStatus.Success, a.setGridPosition(0)); // BACKGROUND
        assertEquals(GridPosition.BACKGROUND, a.getGridPositionAsEnum());
        assertEquals(Integer.valueOf(0), a.getGridPosition());
        assertEquals(UpdateStatus.NoChange, a.setGridPosition(0));
    }

    @Test
    public void boxTypeAndViewDelegate() {
        Axes a = new Axes();
        assertEquals(UpdateStatus.Success, a.setBoxType(1)); // ON
        assertEquals(Integer.valueOf(1), a.getBoxType());

        assertEquals(UpdateStatus.Success, a.setView(1)); // VIEW_3D
        assertEquals(ViewType.VIEW_3D, a.getViewAsEnum());
        assertEquals(Integer.valueOf(1), a.getView());
    }

    @Test
    public void dataBoundsDelegateToBox() {
        Axes a = new Axes();
        assertArrayEquals(new Double[] {0.0, 1.0, 0.0, 1.0, -1.0, 1.0}, a.getDataBounds());
        Double[] bounds = new Double[] {1.0, 2.0, 3.0, 4.0, 5.0, 6.0};
        assertEquals(UpdateStatus.Success, a.setDataBounds(bounds));
        assertArrayEquals(bounds, a.getDataBounds());
    }

    @Test
    public void marginsRoundTripAndReportNoChange() {
        Axes a = new Axes();
        assertArrayEquals(new Double[] {0.0, 0.0, 0.0, 0.0}, a.getMargins());
        Double[] margins = new Double[] {0.1, 0.2, 0.3, 0.4};
        assertEquals(UpdateStatus.Success, a.setMargins(margins));
        assertArrayEquals(margins, a.getMargins());
        assertEquals(UpdateStatus.NoChange, a.setMargins(margins));
    }

    @Test
    public void axesBoundsRoundTripAndReportNoChange() {
        Axes a = new Axes();
        Double[] bounds = new Double[] {0.0, 0.0, 1.0, 1.0};
        assertEquals(UpdateStatus.Success, a.setAxesBounds(bounds));
        assertArrayEquals(bounds, a.getAxesBounds());
        assertEquals(UpdateStatus.NoChange, a.setAxesBounds(bounds));
    }

    @Test
    public void scalarFlagsRoundTrip() {
        Axes a = new Axes();
        assertEquals(UpdateStatus.Success, a.setAutoClear(true));
        assertEquals(UpdateStatus.NoChange, a.setAutoClear(true));
        assertEquals(UpdateStatus.Success, a.setFilled(true));
        assertEquals(UpdateStatus.NoChange, a.setFilled(true));
        assertEquals(UpdateStatus.Success, a.setBackground(9));
        assertEquals(Integer.valueOf(9), a.getBackground());
        assertEquals(UpdateStatus.NoChange, a.setBackground(9));
        assertEquals(UpdateStatus.Success, a.setAutoMargins(false));
        assertFalse(a.getAutoMargins());
        assertEquals(UpdateStatus.NoChange, a.setAutoMargins(false));
    }

    @Test
    public void arcDrawingMethodRoundTrips() {
        Axes a = new Axes();
        assertEquals(UpdateStatus.Success, a.setArcDrawingMethod(0)); // NURBS
        assertEquals(Integer.valueOf(0), a.getArcDrawingMethod());
        assertEquals(UpdateStatus.NoChange, a.setArcDrawingMethod(0));
    }

    @Test
    public void scaleRoundTrips() {
        Axes a = new Axes();
        assertEquals(UpdateStatus.Success, a.setScale(1.0, 2.0, 3.0));
        assertArrayEquals(new double[] {1.0, 2.0, 3.0}, a.getScale());
    }

    @Test
    public void titleReferenceEqualityIsMaskedForCachedIntegers() {
        // Small Integer values are cached, so the reference "!=" check behaves
        // like value equality here: the second identical set is a NoChange.
        Axes a = new Axes();
        assertEquals(UpdateStatus.Success, a.setTitle(50));
        assertEquals(Integer.valueOf(50), a.getTitle());
        assertEquals(UpdateStatus.NoChange, a.setTitle(50));
    }

    @Test
    public void titleReferenceEqualityBugIsVisibleForLargeIntegers() {
        // Values outside the Integer cache box to distinct objects, exposing
        // setTitle's reference ("!=") comparison: an identical re-set is wrongly
        // reported as a change. This documents current behaviour.
        Axes a = new Axes();
        assertEquals(UpdateStatus.Success, a.setTitle(100000));
        assertEquals(UpdateStatus.Success, a.setTitle(100000));
    }

    @Test
    public void cloneCopiesStateResetsTitleAndKeepsAxesIndependent() {
        Axes a = new Axes();
        a.setTitle(5);
        a.setFilled(true);
        a.setBackground(9);
        a.setGridPositionAsEnum(GridPosition.BACKGROUND);
        a.setXAxisVisible(true);

        Axes copy = a.clone();
        // Clone resets the title UID and marks itself valid.
        assertEquals(Integer.valueOf(0), copy.getTitle());
        assertTrue(copy.isValid());
        // Scalar/enum state is carried over.
        assertTrue(copy.getFilled());
        assertEquals(Integer.valueOf(9), copy.getBackground());
        assertEquals(GridPosition.BACKGROUND, copy.getGridPositionAsEnum());
        assertTrue(copy.getXAxisVisible());

        // The axes array is deep-copied: mutating the clone leaves the source.
        copy.setXAxisVisible(false);
        assertTrue(a.getXAxisVisible());
    }

    /* ---------------- getPropertyFromName resolution ---------------- */

    // Asserts a property id resolves to the enum constant of the given name.
    // The Axes.AxesProperty enum is private, so identity is checked through the
    // public Enum#toString (which is the constant name) rather than by reference.
    private static void assertResolves(int propertyId, String expectedName) {
        Object p = new Axes().getPropertyFromName(propertyId);
        assertNotNull(p, "id " + propertyId + " resolved to null");
        assertTrue(p.getClass().isEnum(), "id " + propertyId + " did not resolve to an enum");
        assertEquals(expectedName, p.toString(), "id " + propertyId + " resolved to the wrong constant");
    }

    @Test
    public void getPropertyFromNameResolvesTheAxisFamilies() {
        // X axis.
        assertResolves(__GO_X_AXIS_VISIBLE__, "XAXISVISIBLE");
        assertResolves(__GO_X_AXIS_REVERSE__, "XAXISREVERSE");
        assertResolves(__GO_X_AXIS_GRID_COLOR__, "XAXISGRIDCOLOR");
        assertResolves(__GO_X_AXIS_GRID_THICKNESS__, "XAXISGRIDTHICKNESS");
        assertResolves(__GO_X_AXIS_GRID_STYLE__, "XAXISGRIDSTYLE");
        assertResolves(__GO_X_AXIS_LABEL__, "XAXISLABEL");
        assertResolves(__GO_X_AXIS_LOCATION__, "XAXISLOCATION");
        assertResolves(__GO_X_AXIS_LOG_FLAG__, "XAXISLOGFLAG");
        assertResolves(__GO_X_AXIS_TICKS__, "XAXISTICKS");
        assertResolves(__GO_X_AXIS_FORMAT__, "XAXISFORMAT");
        assertResolves(__GO_X_AXIS_ST_FACTORS__, "XAXISSTFACTORS");
        assertResolves(__GO_X_AXIS_AUTO_TICKS__, "XAXISAUTOTICKS");
        assertResolves(__GO_X_AXIS_NUMBER_TICKS__, "XAXISNUMBERTICKS");
        assertResolves(__GO_X_AXIS_SUBTICKS__, "XAXISSUBTICKS");
        // Y axis (spot sample).
        assertResolves(__GO_Y_AXIS_VISIBLE__, "YAXISVISIBLE");
        assertResolves(__GO_Y_AXIS_GRID_COLOR__, "YAXISGRIDCOLOR");
        assertResolves(__GO_Y_AXIS_LOCATION__, "YAXISLOCATION");
        assertResolves(__GO_Y_AXIS_LOG_FLAG__, "YAXISLOGFLAG");
        // Z axis (spot sample).
        assertResolves(__GO_Z_AXIS_VISIBLE__, "ZAXISVISIBLE");
        assertResolves(__GO_Z_AXIS_GRID_COLOR__, "ZAXISGRIDCOLOR");
        assertResolves(__GO_Z_AXIS_LOCATION__, "ZAXISLOCATION");
        assertResolves(__GO_Z_AXIS_LOG_FLAG__, "ZAXISLOGFLAG");
    }

    @Test
    public void getPropertyFromNameResolvesTheGeneralAxesProperties() {
        assertResolves(__GO_GRID_POSITION__, "GRIDPOSITION");
        assertResolves(__GO_TITLE__, "TITLE");
        assertResolves(__GO_AUTO_CLEAR__, "AUTOCLEAR");
        assertResolves(__GO_FILLED__, "FILLED");
        assertResolves(__GO_BACKGROUND__, "BACKGROUND");
        assertResolves(__GO_MARGINS__, "MARGINS");
        assertResolves(__GO_AUTO_MARGINS__, "AUTO_MARGINS");
        assertResolves(__GO_AXES_BOUNDS__, "AXESBOUNDS");
        assertResolves(__GO_HIDDEN_COLOR__, "HIDDENCOLOR");
        assertResolves(__GO_COLORMAP__, "COLORMAP");
        assertResolves(__GO_COLORMAP_SIZE__, "COLORMAPSIZE");
        assertResolves(__GO_AUTO_SUBTICKS__, "AUTOSUBTICKS");
        assertResolves(__GO_FONT_STYLE__, "FONT_STYLE");
        assertResolves(__GO_FONT_SIZE__, "FONT_SIZE");
        assertResolves(__GO_FONT_COLOR__, "FONT_COLOR");
        assertResolves(__GO_FONT_FRACTIONAL__, "FONT_FRACTIONAL");
    }

    @Test
    public void getPropertyFromNameRoutesToSubObjectEnums() {
        // Camera-owned view properties.
        assertResolves(__GO_VIEW__, "VIEW");
        assertResolves(__GO_ISOVIEW__, "ISOVIEW");
        assertResolves(__GO_CUBE_SCALING__, "CUBESCALING");
        // Box-owned geometry properties.
        assertResolves(__GO_BOX_TYPE__, "BOX");
        assertResolves(__GO_DATA_BOUNDS__, "DATABOUNDS");
        assertResolves(__GO_X_TIGHT_LIMITS__, "XTIGHTLIMITS");
        assertResolves(__GO_AUTO_SCALE__, "AUTOSCALE");
        // Line / mark / clippable / arc.
        assertResolves(__GO_LINE_MODE__, "MODE");
        assertResolves(__GO_MARK_STYLE__, "STYLE");
        assertResolves(__GO_CLIP_STATE__, "CLIPSTATE");
        assertResolves(__GO_ARC_DRAWING_METHOD__, "ARCDRAWINGMETHOD");
    }

    @Test
    public void getPropertyFromNameFallsThroughToGraphicObjectForGenericProperties() {
        // A non-Axes property is delegated to the GraphicObject superclass.
        Object p = new Axes().getPropertyFromName(__GO_VISIBLE__);
        assertNotNull(p);
        assertEquals("VISIBLE", p.toString());
    }

    @Test
    public void getPropertyFromNameIsStableAndDistinct() {
        Axes a = new Axes();
        // Same id resolves to the same singleton enum constant every time.
        assertSame(a.getPropertyFromName(__GO_FILLED__), a.getPropertyFromName(__GO_FILLED__));
        // Different ids never collapse to the same constant.
        assertNotSame(a.getPropertyFromName(__GO_FILLED__), a.getPropertyFromName(__GO_BACKGROUND__));
    }

    /* ---------- getProperty / setProperty dispatch round-trips ---------- */

    // Resolves a property by id and round-trips a scalar through the generic
    // setProperty/getProperty dispatch, exercising the big switch arms that the
    // typed setters above reach only directly.
    private void assertScalarRoundTrip(int propertyId, Object value) {
        Axes a = new Axes();
        Object prop = a.getPropertyFromName(propertyId);
        a.setProperty(prop, value);
        assertEquals(value, a.getProperty(prop), "round-trip mismatch for id " + propertyId);
    }

    @Test
    public void booleanPropertiesRoundTripThroughGenericDispatch() {
        assertScalarRoundTrip(__GO_X_AXIS_VISIBLE__, Boolean.TRUE);
        assertScalarRoundTrip(__GO_X_AXIS_REVERSE__, Boolean.TRUE);
        assertScalarRoundTrip(__GO_X_AXIS_LOG_FLAG__, Boolean.TRUE);
        assertScalarRoundTrip(__GO_Y_AXIS_VISIBLE__, Boolean.TRUE);
        assertScalarRoundTrip(__GO_Z_AXIS_VISIBLE__, Boolean.TRUE);
        assertScalarRoundTrip(__GO_FILLED__, Boolean.TRUE);
        assertScalarRoundTrip(__GO_AUTO_CLEAR__, Boolean.TRUE);
        assertScalarRoundTrip(__GO_AUTO_MARGINS__, Boolean.FALSE);
    }

    @Test
    public void integerPropertiesRoundTripThroughGenericDispatch() {
        assertScalarRoundTrip(__GO_X_AXIS_GRID_COLOR__, Integer.valueOf(7));
        assertScalarRoundTrip(__GO_BACKGROUND__, Integer.valueOf(9));
        assertScalarRoundTrip(__GO_GRID_POSITION__, Integer.valueOf(0));
        assertScalarRoundTrip(__GO_X_AXIS_LOCATION__, Integer.valueOf(1));
        // Box- and Camera- and Arc-routed integer properties.
        assertScalarRoundTrip(__GO_BOX_TYPE__, Integer.valueOf(1));
        assertScalarRoundTrip(__GO_VIEW__, Integer.valueOf(1));
        assertScalarRoundTrip(__GO_ARC_DRAWING_METHOD__, Integer.valueOf(0));
    }

    @Test
    public void doublePropertyRoundTripsThroughGenericDispatch() {
        assertScalarRoundTrip(__GO_X_AXIS_GRID_THICKNESS__, Double.valueOf(2.5));
    }

    @Test
    public void arrayPropertiesRoundTripThroughGenericDispatch() {
        Axes a = new Axes();

        Object margins = a.getPropertyFromName(__GO_MARGINS__);
        Double[] m = {0.1, 0.2, 0.3, 0.4};
        a.setProperty(margins, m);
        assertArrayEquals(m, (Double[]) a.getProperty(margins));

        Object axesBounds = a.getPropertyFromName(__GO_AXES_BOUNDS__);
        Double[] ab = {0.0, 0.0, 1.0, 1.0};
        a.setProperty(axesBounds, ab);
        assertArrayEquals(ab, (Double[]) a.getProperty(axesBounds));

        Object dataBounds = a.getPropertyFromName(__GO_DATA_BOUNDS__);
        Double[] db = {1.0, 2.0, 3.0, 4.0, 5.0, 6.0};
        a.setProperty(dataBounds, db);
        assertArrayEquals(db, (Double[]) a.getProperty(dataBounds));
    }

    /* ---- exhaustive generic-dispatch round-trips over the pure surface ---- */

    // Round-trips a Double[] array property through the generic dispatch: the
    // Object overload of assertScalarRoundTrip cannot be reused because array
    // equality there is by reference.
    private void assertArrayRoundTrip(int propertyId, Double[] value) {
        Axes a = new Axes();
        Object prop = a.getPropertyFromName(propertyId);
        a.setProperty(prop, value);
        assertArrayEquals(value, (Double[]) a.getProperty(prop),
                          "array round-trip mismatch for id " + propertyId);
    }

    @Test
    public void everyBooleanAxisPropertyRoundTripsThroughGenericDispatch() {
        // Per-axis flags not already covered above.
        assertScalarRoundTrip(__GO_Y_AXIS_REVERSE__, Boolean.TRUE);
        assertScalarRoundTrip(__GO_Z_AXIS_REVERSE__, Boolean.TRUE);
        assertScalarRoundTrip(__GO_Y_AXIS_LOG_FLAG__, Boolean.TRUE);
        assertScalarRoundTrip(__GO_Z_AXIS_LOG_FLAG__, Boolean.TRUE);
        assertScalarRoundTrip(__GO_X_AXIS_AUTO_TICKS__, Boolean.TRUE);
        assertScalarRoundTrip(__GO_Y_AXIS_AUTO_TICKS__, Boolean.TRUE);
        assertScalarRoundTrip(__GO_Z_AXIS_AUTO_TICKS__, Boolean.TRUE);
        assertScalarRoundTrip(__GO_AUTO_SUBTICKS__, Boolean.TRUE);
        // Camera-owned flags.
        assertScalarRoundTrip(__GO_ISOVIEW__, Boolean.TRUE);
        assertScalarRoundTrip(__GO_CUBE_SCALING__, Boolean.TRUE);
        // Box-owned flags.
        assertScalarRoundTrip(__GO_X_TIGHT_LIMITS__, Boolean.TRUE);
        assertScalarRoundTrip(__GO_Y_TIGHT_LIMITS__, Boolean.TRUE);
        assertScalarRoundTrip(__GO_Z_TIGHT_LIMITS__, Boolean.TRUE);
        assertScalarRoundTrip(__GO_ZOOM_ENABLED__, Boolean.TRUE);
        assertScalarRoundTrip(__GO_AUTO_SCALE__, Boolean.TRUE);
        assertScalarRoundTrip(__GO_AUTO_STRETCH__, Boolean.TRUE);
        assertScalarRoundTrip(__GO_FIRST_PLOT__, Boolean.FALSE);
        // Inherited line / mark / clip flags, routed through the Axes switch.
        assertScalarRoundTrip(__GO_LINE_MODE__, Boolean.TRUE);
        assertScalarRoundTrip(__GO_MARK_MODE__, Boolean.TRUE);
        assertScalarRoundTrip(__GO_CLIP_BOX_SET__, Boolean.TRUE);
    }

    @Test
    public void everyIntegerAxisPropertyRoundTripsThroughGenericDispatch() {
        // Y/Z grid colours (X covered above).
        assertScalarRoundTrip(__GO_Y_AXIS_GRID_COLOR__, Integer.valueOf(8));
        assertScalarRoundTrip(__GO_Z_AXIS_GRID_COLOR__, Integer.valueOf(9));
        // Per-axis label UIDs.
        assertScalarRoundTrip(__GO_X_AXIS_LABEL__, Integer.valueOf(11));
        assertScalarRoundTrip(__GO_Y_AXIS_LABEL__, Integer.valueOf(12));
        assertScalarRoundTrip(__GO_Z_AXIS_LABEL__, Integer.valueOf(13));
        // Per-axis subtick counts.
        assertScalarRoundTrip(__GO_X_AXIS_SUBTICKS__, Integer.valueOf(4));
        assertScalarRoundTrip(__GO_Y_AXIS_SUBTICKS__, Integer.valueOf(5));
        assertScalarRoundTrip(__GO_Z_AXIS_SUBTICKS__, Integer.valueOf(6));
        // Grid styles: Scilab line-style index 1 == SOLID round-trips cleanly.
        assertScalarRoundTrip(__GO_X_AXIS_GRID_STYLE__, Integer.valueOf(1));
        assertScalarRoundTrip(__GO_Y_AXIS_GRID_STYLE__, Integer.valueOf(1));
        assertScalarRoundTrip(__GO_Z_AXIS_GRID_STYLE__, Integer.valueOf(1));
        // Y/Z axis locations (ordinals 0..5; X covered above).
        assertScalarRoundTrip(__GO_Y_AXIS_LOCATION__, Integer.valueOf(2)); // MIDDLE
        assertScalarRoundTrip(__GO_Z_AXIS_LOCATION__, Integer.valueOf(4)); // LEFT
        // Axes / Box colours.
        assertScalarRoundTrip(__GO_HIDDEN_COLOR__, Integer.valueOf(6));
        assertScalarRoundTrip(__GO_HIDDEN_AXIS_COLOR__, Integer.valueOf(4));
        // Inherited line / mark integers.
        assertScalarRoundTrip(__GO_LINE_STYLE__, Integer.valueOf(7));       // DOT
        assertScalarRoundTrip(__GO_LINE_COLOR__, Integer.valueOf(7));
        assertScalarRoundTrip(__GO_MARK_STYLE__, Integer.valueOf(4));
        assertScalarRoundTrip(__GO_MARK_SIZE__, Integer.valueOf(6));
        assertScalarRoundTrip(__GO_MARK_SIZE_UNIT__, Integer.valueOf(1));   // TABULATED
        assertScalarRoundTrip(__GO_MARK_FOREGROUND__, Integer.valueOf(2));
        assertScalarRoundTrip(__GO_MARK_BACKGROUND__, Integer.valueOf(1));
        // Clip state: CLIPGRF (1) round-trips; ON (2) would be demoted with no box.
        assertScalarRoundTrip(__GO_CLIP_STATE__, Integer.valueOf(1));
    }

    @Test
    public void everyDoubleAxisPropertyRoundTripsThroughGenericDispatch() {
        assertScalarRoundTrip(__GO_Y_AXIS_GRID_THICKNESS__, Double.valueOf(3.5));
        assertScalarRoundTrip(__GO_Z_AXIS_GRID_THICKNESS__, Double.valueOf(4.5));
        assertScalarRoundTrip(__GO_LINE_THICKNESS__, Double.valueOf(3.0));
    }

    @Test
    public void everyStringAxisPropertyRoundTripsThroughGenericDispatch() {
        assertScalarRoundTrip(__GO_X_AXIS_FORMAT__, "%.2f");
        assertScalarRoundTrip(__GO_Y_AXIS_FORMAT__, "%.3f");
        assertScalarRoundTrip(__GO_Z_AXIS_FORMAT__, "%.4f");
    }

    @Test
    public void everyArrayAxisPropertyRoundTripsThroughGenericDispatch() {
        // Scale/translate factor pairs.
        assertArrayRoundTrip(__GO_X_AXIS_ST_FACTORS__, new Double[] {2.0, 3.0});
        assertArrayRoundTrip(__GO_Y_AXIS_ST_FACTORS__, new Double[] {4.0, 5.0});
        assertArrayRoundTrip(__GO_Z_AXIS_ST_FACTORS__, new Double[] {6.0, 7.0});
        // User tick locations (empty by default; setting resizes the active set).
        assertArrayRoundTrip(__GO_X_AXIS_TICKS_LOCATIONS__, new Double[] {0.0, 0.5, 1.0});
        assertArrayRoundTrip(__GO_Y_AXIS_TICKS_LOCATIONS__, new Double[] {0.0, 1.0});
        assertArrayRoundTrip(__GO_Z_AXIS_TICKS_LOCATIONS__, new Double[] {0.0, 2.0, 4.0, 6.0});
        // Box bounds/boxes are length-6.
        assertArrayRoundTrip(__GO_REAL_DATA_BOUNDS__, new Double[] {1.0, 2.0, 3.0, 4.0, 5.0, 6.0});
        assertArrayRoundTrip(__GO_ZOOM_BOX__, new Double[] {1.0, 2.0, 3.0, 4.0, 5.0, 6.0});
        // Inherited clip box is length-4.
        assertArrayRoundTrip(__GO_CLIP_BOX__, new Double[] {0.0, 0.0, 1.0, 1.0});
    }

    @Test
    public void tickLabelsAndInterpretersRoundTripThroughGenericDispatch() {
        Axes a = new Axes();
        // Labels line up with locations, so the locations must exist first.
        a.setXAxisTicksLocations(new Double[] {0.0, 1.0, 2.0});

        Object labelsProp = a.getPropertyFromName(__GO_X_AXIS_TICKS_LABELS__);
        assertEquals(UpdateStatus.Success,
                     a.setProperty(labelsProp, new String[] {"a", "b", "c"}));
        assertArrayEquals(new String[] {"a", "b", "c"}, (String[]) a.getProperty(labelsProp));

        Object interpProp = a.getPropertyFromName(__GO_X_AXIS_TICKS_INTERPRETERS__);
        assertEquals(UpdateStatus.Success,
                     a.setProperty(interpProp, new String[] {"latex", "latex", "latex"}));
        assertArrayEquals(new String[] {"latex", "latex", "latex"},
                          (String[]) a.getProperty(interpProp));
    }

    @Test
    public void mismatchedTickLabelLengthIsRejectedAsNoChange() {
        Axes a = new Axes();
        a.setXAxisTicksLocations(new Double[] {0.0, 1.0, 2.0});
        // Fewer labels than locations: the active ticks set refuses the change.
        assertEquals(UpdateStatus.NoChange, a.setXAxisTicksLabels(new String[] {"only-one"}));
    }

    @Test
    public void tickHolderCanBeReplacedThroughGenericDispatch() {
        Axes a = new Axes();
        Object prop = a.getPropertyFromName(__GO_X_AXIS_TICKS__);
        TicksProperty tp = new TicksProperty();
        assertEquals(UpdateStatus.Success, a.setProperty(prop, tp));
        // The holder is stored and returned by reference.
        assertSame(tp, a.getProperty(prop));
    }

    @Test
    public void readOnlyTickCountsAreReachableThroughGenericGet() {
        Axes a = new Axes();
        // A fresh (non-automatic) axis has an empty user ticks set.
        assertEquals(Integer.valueOf(0),
                     a.getProperty(a.getPropertyFromName(__GO_X_AXIS_NUMBER_TICKS__)));
        assertEquals(Integer.valueOf(0),
                     a.getProperty(a.getPropertyFromName(__GO_Y_AXIS_NUMBER_TICKS__)));
        assertEquals(Integer.valueOf(0),
                     a.getProperty(a.getPropertyFromName(__GO_Z_AXIS_NUMBER_TICKS__)));
    }

    @Test
    public void colormapPropertiesAreReachableThroughGenericDispatch() {
        Axes a = new Axes();
        Object sizeProp = a.getPropertyFromName(__GO_COLORMAP_SIZE__);
        assertNotNull(a.getProperty(sizeProp));
        // Colormap size is read-only through setProperty: always NoChange.
        assertEquals(UpdateStatus.NoChange, a.setProperty(sizeProp, Integer.valueOf(99)));
        // The colormap data array is reachable through the generic getter.
        assertNotNull(a.getProperty(a.getPropertyFromName(__GO_COLORMAP__)));
    }
}
