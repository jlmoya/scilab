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

package org.scilab.modules.graphic_objects.uicontrol;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.awt.Font;

import org.scilab.modules.graphic_objects.console.Console;
import org.scilab.modules.graphic_objects.graphicObject.GraphicObject.UpdateStatus;
import org.scilab.modules.graphic_objects.uicontrol.Uicontrol.AnchorType;
import org.scilab.modules.graphic_objects.uicontrol.Uicontrol.BorderLayoutType;
import org.scilab.modules.graphic_objects.uicontrol.Uicontrol.FillType;
import org.scilab.modules.graphic_objects.uicontrol.Uicontrol.TitlePositionType;
import org.scilab.modules.graphic_objects.uicontrol.Uicontrol.UicontrolStyle;
import org.scilab.modules.graphic_objects.utils.LayoutType;

import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_POSITION__;
import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_TAG__;
import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_UICONTROL__;
import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_UI_CHECKBOX__;
import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_UI_EDIT__;
import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_UI_ENABLE__;
import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_UI_LISTBOX__;
import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_UI_MAX__;
import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_UI_RELIEF__;
import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_UI_STRING__;
import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_UI_STRING_SIZE__;
import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_UI_UNITS__;

/**
 * Hermetic unit tests for {@link Uicontrol}. The base uicontrol constructor is
 * pure Java (the Swing/UIManager code path lives only in the concrete
 * subclasses), so a bare {@code Uicontrol} can be exercised without any native
 * runtime or display.
 *
 * <p>The single global dependency is the {@link Console} singleton read in the
 * constructor via {@code getUseDeprecatedLF()}. Each test forces it to
 * {@code false} (the field default) and restores the prior value afterwards so
 * default-state assertions are deterministic and no global state leaks to other
 * test classes.
 */
public class UicontrolTest {

    private boolean savedLF;

    @BeforeEach
    public void forceModernLookAndFeel() {
        savedLF = Console.getConsole().getUseDeprecatedLF();
        Console.getConsole().setUseDeprecatedLF(false);
    }

    @AfterEach
    public void restoreLookAndFeel() {
        Console.getConsole().setUseDeprecatedLF(savedLF);
    }

    // ---------------------------------------------------------------- basics

    @Test
    public void typeIsUicontrol() {
        assertEquals(__GO_UICONTROL__, new Uicontrol().getType().intValue());
    }

    @Test
    public void constructorDefaultsWithModernLookAndFeel() {
        Uicontrol u = new Uicontrol();
        assertTrue(u.getEnable());
        assertEquals(1.0, u.getMax().doubleValue(), 0.0);
        assertEquals(0.0, u.getMin().doubleValue(), 0.0);
        assertEquals("pixels", u.getUnits());
        assertEquals("points", u.getFontUnits());
        assertEquals("default", u.getRelief());
        assertEquals(0.0, u.getFontSize(), 0.0);
        assertEquals("", u.getFontName());
        assertEquals("", u.getFontAngle());
        assertEquals("", u.getFontWeight());
        assertEquals("", u.getHorizontalAlignment());
        assertEquals("", u.getVerticalAlignment());
        assertFalse(u.getScrollable());
        assertFalse(u.getSnapToTicks());
        assertFalse(u.getTitleScroll());
        assertFalse(u.getDebug());
        assertEquals("", u.getData());
        assertEquals("", u.getIcon());
        assertEquals("", u.getGroupName());
        assertEquals(1, u.getStringColNb());
        // The modern (non-deprecated) look and feel leaves colors at the sentinel.
        assertArrayEquals(new Double[] { -1.0, -1.0, -1.0}, u.getBackgroundColor());
        assertArrayEquals(new Double[] { -1.0, -1.0, -1.0}, u.getForegroundColor());
    }

    @Test
    public void constructorDefaultArraysAndSizes() {
        Uicontrol u = new Uicontrol();
        assertArrayEquals(new Double[] {20.0, 40.0, 40.0, 20.0}, u.getUiPosition());
        assertArrayEquals(new Double[] {0.01, 0.1}, u.getSliderStep());
        assertArrayEquals(new String[] {""}, u.getTooltipString());
        assertEquals(0, u.getString().length);
        // value / listboxTop are never initialised on a bare uicontrol.
        assertNull(u.getUiValue());
        assertNull(u.getListboxTop());
        assertEquals(0, u.getUiValueSize().intValue());
        assertEquals(0, u.getListboxTopSize().intValue());
        // layout defaults
        assertEquals(LayoutType.NONE, u.getLayoutAsEnum());
        assertEquals(0, u.getLayout().intValue());
        assertTrue(u.isLayoutSettable());
        assertArrayEquals(new Double[] {0.0, 0.0, 0.0, 0.0}, u.getMargins());
        // gridbag / border defaults
        assertArrayEquals(new Integer[] { -1, -1, 1, 1}, u.getGridBagGrid());
        assertArrayEquals(new Double[] {0.0, 0.0}, u.getGridBagWeight());
        assertArrayEquals(new Integer[] {0, 0}, u.getGridBagPadding());
        assertArrayEquals(new Integer[] { -1, -1}, u.getGridBagPreferredSize());
        assertArrayEquals(new Integer[] { -1, -1}, u.getBorderPreferredSize());
        assertArrayEquals(new Integer[] {0, 0}, u.getGridOptGrid());
        assertArrayEquals(new Integer[] {0, 0}, u.getGridOptPadding());
        assertArrayEquals(new Integer[] {0, 0}, u.getBorderOptPadding());
        assertEquals(FillType.NONE, u.getGridBagFillAsEnum());
        assertEquals(AnchorType.CENTER, u.getGridBagAnchorAsEnum());
        assertEquals(BorderLayoutType.CENTER, u.getBorderPositionAsEnum());
        assertEquals(BorderLayoutType.CENTER.ordinal(), u.getBorderPosition().intValue());
        assertEquals(TitlePositionType.TOP, u.getTitlePositionAsEnum());
        assertEquals(0, u.getFrameBorder().intValue());
    }

    @Test
    public void freshUicontrolHasNoStyle() {
        Uicontrol u = new Uicontrol();
        assertNull(u.getStyleAsEnum());
    }

    // ------------------------------------------------ UpdateStatus semantics

    @Test
    public void scalarSettersReportChangeThenNoChange() {
        Uicontrol u = new Uicontrol();

        assertEquals(UpdateStatus.Success, u.setEnable(false));
        assertEquals(UpdateStatus.NoChange, u.setEnable(false));
        assertFalse(u.getEnable());

        assertEquals(UpdateStatus.Success, u.setMax(5.0));
        assertEquals(UpdateStatus.NoChange, u.setMax(5.0));
        assertEquals(5.0, u.getMax().doubleValue(), 0.0);

        assertEquals(UpdateStatus.Success, u.setMin(-2.0));
        assertEquals(UpdateStatus.NoChange, u.setMin(-2.0));
        assertEquals(-2.0, u.getMin().doubleValue(), 0.0);

        assertEquals(UpdateStatus.Success, u.setFontSize(12.0));
        assertEquals(UpdateStatus.NoChange, u.setFontSize(12.0));
        assertEquals(12.0, u.getFontSize(), 0.0);

        assertEquals(UpdateStatus.Success, u.setStringColNb(3));
        assertEquals(UpdateStatus.NoChange, u.setStringColNb(3));
        assertEquals(3, u.getStringColNb());
    }

    @Test
    public void stringSettersReportChangeThenNoChange() {
        Uicontrol u = new Uicontrol();
        assertEquals(UpdateStatus.Success, u.setRelief("raised"));
        assertEquals(UpdateStatus.NoChange, u.setRelief("raised"));
        assertEquals("raised", u.getRelief());

        assertEquals(UpdateStatus.Success, u.setUnits("points"));
        assertEquals(UpdateStatus.NoChange, u.setUnits("points"));

        assertEquals(UpdateStatus.Success, u.setFontName("Arial"));
        assertEquals(UpdateStatus.NoChange, u.setFontName("Arial"));

        assertEquals(UpdateStatus.Success, u.setHorizontalAlignment("left"));
        assertEquals(UpdateStatus.NoChange, u.setHorizontalAlignment("left"));

        assertEquals(UpdateStatus.Success, u.setVerticalAlignment("top"));
        assertEquals(UpdateStatus.NoChange, u.setVerticalAlignment("top"));

        assertEquals(UpdateStatus.Success, u.setGroupName("g1"));
        assertEquals(UpdateStatus.NoChange, u.setGroupName("g1"));

        assertEquals(UpdateStatus.Success, u.setIcon("icon.png"));
        assertEquals(UpdateStatus.NoChange, u.setIcon("icon.png"));
    }

    @Test
    public void booleanSettersReportChangeThenNoChange() {
        Uicontrol u = new Uicontrol();
        assertEquals(UpdateStatus.Success, u.setSnapToTicks(true));
        assertEquals(UpdateStatus.NoChange, u.setSnapToTicks(true));
        assertTrue(u.getSnapToTicks());

        assertEquals(UpdateStatus.Success, u.setScrollable(true));
        assertEquals(UpdateStatus.NoChange, u.setScrollable(true));
        assertTrue(u.getScrollable());

        assertEquals(UpdateStatus.Success, u.setTitleScroll(true));
        assertEquals(UpdateStatus.NoChange, u.setTitleScroll(true));
        assertTrue(u.getTitleScroll());

        assertEquals(UpdateStatus.Success, u.setDebug(true));
        assertEquals(UpdateStatus.NoChange, u.setDebug(true));
        assertTrue(u.getDebug());
    }

    @Test
    public void arraySettersUseValueEquality() {
        Uicontrol u = new Uicontrol();

        assertEquals(UpdateStatus.Success, u.setBackgroundColor(new Double[] {0.1, 0.2, 0.3}));
        // A different array instance carrying equal values is NOT a change.
        assertEquals(UpdateStatus.NoChange, u.setBackgroundColor(new Double[] {0.1, 0.2, 0.3}));
        assertArrayEquals(new Double[] {0.1, 0.2, 0.3}, u.getBackgroundColor());

        assertEquals(UpdateStatus.Success, u.setForegroundColor(new Double[] {0.4, 0.5, 0.6}));
        assertEquals(UpdateStatus.NoChange, u.setForegroundColor(new Double[] {0.4, 0.5, 0.6}));

        assertEquals(UpdateStatus.Success, u.setUiPosition(new Double[] {1.0, 2.0, 3.0, 4.0}));
        assertEquals(UpdateStatus.NoChange, u.setUiPosition(new Double[] {1.0, 2.0, 3.0, 4.0}));

        assertEquals(UpdateStatus.Success, u.setSliderStep(new Double[] {0.2, 0.5}));
        assertEquals(UpdateStatus.NoChange, u.setSliderStep(new Double[] {0.2, 0.5}));

        assertEquals(UpdateStatus.Success, u.setTooltipString(new String[] {"tip"}));
        assertEquals(UpdateStatus.NoChange, u.setTooltipString(new String[] {"tip"}));
    }

    @Test
    public void listboxTopAndValueTrackSize() {
        Uicontrol u = new Uicontrol();
        assertEquals(UpdateStatus.Success, u.setListboxTop(new Integer[] {1, 2, 3}));
        assertEquals(3, u.getListboxTopSize().intValue());
        assertEquals(UpdateStatus.NoChange, u.setListboxTop(new Integer[] {1, 2, 3}));

        assertEquals(UpdateStatus.Success, u.setUiValue(new Double[] {7.0, 8.0}));
        assertEquals(2, u.getUiValueSize().intValue());
        assertEquals(UpdateStatus.NoChange, u.setUiValue(new Double[] {7.0, 8.0}));
    }

    // -------------------------------------------------------------- setStyle

    @Test
    public void setStyleRoundTripsAndReportsNoChange() {
        Uicontrol u = new Uicontrol();
        assertEquals(UpdateStatus.Success, u.setStyle(__GO_UI_CHECKBOX__));
        assertEquals(UicontrolStyle.CHECKBOX, u.getStyleAsEnum());
        assertEquals(__GO_UI_CHECKBOX__, u.getStyle().intValue());
        assertEquals(UpdateStatus.NoChange, u.setStyle(__GO_UI_CHECKBOX__));

        assertEquals(UpdateStatus.Success, u.setStyle(__GO_UI_EDIT__));
        assertEquals(UicontrolStyle.EDIT, u.getStyleAsEnum());
        assertEquals(__GO_UI_EDIT__, u.getStyle().intValue());
    }

    @Test
    public void getStyleOnUnstyledControlThrowsNpe_defectCharacterization() {
        // A freshly built Uicontrol never gets a style assigned, leaving the
        // internal enum null; styleEnumToInt() switches on it, so getStyle()
        // throws NPE rather than returning the -1 "unknown" sentinel.
        Uicontrol u = new Uicontrol();
        assertNull(u.getStyleAsEnum());
        assertThrows(NullPointerException.class, () -> u.getStyle());
    }

    // -------------------------------------------------------------- setString

    @Test
    public void setStringEmptyThenAppendReportsChanges() {
        Uicontrol u = new Uicontrol();
        // Equal empty content (fresh array) is not a change.
        assertEquals(UpdateStatus.NoChange, u.setString(new String[] {}));
        assertEquals(UpdateStatus.Success, u.setString(new String[] {"a"}));
        assertArrayEquals(new String[] {"a"}, u.getString());
        // Equal single-element content in a different array is not a change.
        assertEquals(UpdateStatus.NoChange, u.setString(new String[] {"a"}));
    }

    @Test
    public void setStringSameReferenceIsNoChange() {
        Uicontrol u = new Uicontrol();
        String[] s = {"x", "y"};
        assertEquals(UpdateStatus.Success, u.setString(s));
        assertEquals(UpdateStatus.NoChange, u.setString(s));
    }

    @Test
    public void listboxStyleSplitsPipeSeparatedString() {
        Uicontrol u = new Uicontrol();
        u.setStyle(__GO_UI_LISTBOX__);
        // A single element containing '|' is tokenised for listbox/popupmenu.
        assertEquals(UpdateStatus.Success, u.setString(new String[] {"a|b|c"}));
        assertArrayEquals(new String[] {"a", "b", "c"}, u.getString());
    }

    @Test
    public void nonListStyleDoesNotSplitPipeSeparatedString() {
        Uicontrol u = new Uicontrol();
        // Default (no) style keeps a pipe-bearing element verbatim.
        assertEquals(UpdateStatus.Success, u.setString(new String[] {"a|b|c"}));
        assertArrayEquals(new String[] {"a|b|c"}, u.getString());
    }

    @Test
    public void listboxSplitDropsEmptyTokens() {
        // StringTokenizer skips empty tokens, so consecutive/edge separators
        // collapse; this documents that behavior.
        Uicontrol u = new Uicontrol();
        u.setStyle(__GO_UI_LISTBOX__);
        assertEquals(UpdateStatus.Success, u.setString(new String[] {"|a||b|"}));
        assertArrayEquals(new String[] {"a", "b"}, u.getString());
    }

    // ------------------------------------------------------------ setData bug

    @Test
    public void setDataAlwaysReportsSuccess_defectCharacterization() {
        // The NoChange short-circuit in setData is commented out, so even an
        // identical value reports Success.
        Uicontrol u = new Uicontrol();
        assertEquals(UpdateStatus.Success, u.setData("payload"));
        assertEquals("payload", u.getData());
        assertEquals(UpdateStatus.Success, u.setData("payload"));
    }

    // --------------------------------------------------------------- layout

    @Test
    public void layoutCanBeSetOnceThenIsLocked() {
        Uicontrol u = new Uicontrol();
        assertTrue(u.isLayoutSettable());
        // Setting NONE onto NONE is a no-op.
        assertEquals(UpdateStatus.NoChange, u.setLayout(LayoutType.NONE));
        assertEquals(UpdateStatus.Success, u.setLayout(LayoutType.GRIDBAG));
        assertEquals(LayoutType.GRIDBAG, u.getLayoutAsEnum());
        assertEquals(LayoutType.GRIDBAG.ordinal(), u.getLayout().intValue());
        assertFalse(u.isLayoutSettable());
        // A second attempt fails: layout is write-once.
        assertEquals(UpdateStatus.Fail, u.setLayout(LayoutType.GRID));
        assertEquals(LayoutType.GRIDBAG, u.getLayoutAsEnum());
    }

    @Test
    public void setLayoutFromIntegerMapsThroughEnum() {
        Uicontrol u = new Uicontrol();
        assertEquals(UpdateStatus.Success, u.setLayout(Integer.valueOf(3)));
        assertEquals(LayoutType.BORDER, u.getLayoutAsEnum());
    }

    // ------------------------------------------------ fixed-length collections

    @Test
    public void setMarginsRequiresLengthFour() {
        Uicontrol u = new Uicontrol();
        assertEquals(UpdateStatus.Fail, u.setMargins(new Double[] {1.0, 2.0}));
        assertEquals(UpdateStatus.Success, u.setMargins(new Double[] {1.0, 2.0, 3.0, 4.0}));
        assertArrayEquals(new Double[] {1.0, 2.0, 3.0, 4.0}, u.getMargins());
        assertEquals(UpdateStatus.NoChange, u.setMargins(new Double[] {1.0, 2.0, 3.0, 4.0}));
    }

    @Test
    public void gridBagSettersRejectWrongLengthAndDetectChanges() {
        Uicontrol u = new Uicontrol();
        // Wrong length is a hard Fail.
        assertEquals(UpdateStatus.Fail, u.setGridBagGrid(new Integer[] {1, 2, 3}));
        assertEquals(UpdateStatus.Success, u.setGridBagGrid(new Integer[] {0, 0, 2, 2}));
        assertArrayEquals(new Integer[] {0, 0, 2, 2}, u.getGridBagGrid());
        assertEquals(UpdateStatus.NoChange, u.setGridBagGrid(new Integer[] {0, 0, 2, 2}));

        assertEquals(UpdateStatus.Fail, u.setGridBagWeight(new Double[] {1.0}));
        assertEquals(UpdateStatus.Success, u.setGridBagWeight(new Double[] {0.5, 0.5}));
        assertEquals(UpdateStatus.NoChange, u.setGridBagWeight(new Double[] {0.5, 0.5}));

        assertEquals(UpdateStatus.Fail, u.setGridBagPadding(new Integer[] {1}));
        assertEquals(UpdateStatus.Success, u.setGridBagPadding(new Integer[] {3, 4}));
        assertEquals(UpdateStatus.NoChange, u.setGridBagPadding(new Integer[] {3, 4}));

        assertEquals(UpdateStatus.Fail, u.setGridBagPreferredSize(new Integer[] {1}));
        assertEquals(UpdateStatus.Success, u.setGridBagPreferredSize(new Integer[] {10, 20}));
        assertEquals(UpdateStatus.NoChange, u.setGridBagPreferredSize(new Integer[] {10, 20}));
    }

    @Test
    public void borderAndGridOptSettersRejectWrongLength() {
        Uicontrol u = new Uicontrol();
        assertEquals(UpdateStatus.Fail, u.setBorderPreferredSize(new Integer[] {1}));
        assertEquals(UpdateStatus.Success, u.setBorderPreferredSize(new Integer[] {5, 6}));

        assertEquals(UpdateStatus.Fail, u.setGridOptGrid(new Integer[] {1}));
        assertEquals(UpdateStatus.Success, u.setGridOptGrid(new Integer[] {2, 3}));

        assertEquals(UpdateStatus.Fail, u.setGridOptPadding(new Integer[] {1}));
        assertEquals(UpdateStatus.Success, u.setGridOptPadding(new Integer[] {4, 5}));

        assertEquals(UpdateStatus.Fail, u.setBorderOptPadding(new Integer[] {1}));
        assertEquals(UpdateStatus.Success, u.setBorderOptPadding(new Integer[] {6, 7}));
    }

    // --------------------------------------------------- enum-backed setters

    @Test
    public void borderPositionAndGridBagEnumSettersRoundTrip() {
        Uicontrol u = new Uicontrol();
        assertEquals(UpdateStatus.Success, u.setBorderPosition(Integer.valueOf(1)));
        assertEquals(BorderLayoutType.TOP, u.getBorderPositionAsEnum());
        assertEquals(UpdateStatus.NoChange, u.setBorderPosition(BorderLayoutType.TOP));

        assertEquals(UpdateStatus.Success, u.setGridBagAnchor(Integer.valueOf(3)));
        assertEquals(AnchorType.RIGHT, u.getGridBagAnchorAsEnum());
        assertEquals(UpdateStatus.NoChange, u.setGridBagAnchor(AnchorType.RIGHT));

        assertEquals(UpdateStatus.Success, u.setGridBagFill(Integer.valueOf(3)));
        assertEquals(FillType.BOTH, u.getGridBagFillAsEnum());
        assertEquals(UpdateStatus.NoChange, u.setGridBagFill(FillType.BOTH));

        assertEquals(UpdateStatus.Success, u.setTitlePosition(Integer.valueOf(2)));
        assertEquals(TitlePositionType.BOTTOM, u.getTitlePositionAsEnum());
        assertEquals(UpdateStatus.NoChange, u.setTitlePosition(TitlePositionType.BOTTOM));

        assertEquals(UpdateStatus.Success, u.setFrameBorder(4));
        assertEquals(4, u.getFrameBorder().intValue());
        assertEquals(UpdateStatus.NoChange, u.setFrameBorder(4));
    }

    // --------------------------------------------------------- event handler

    @Test
    public void eventHandlerDefaultsAndSetters() {
        Uicontrol u = new Uicontrol();
        assertEquals("", u.getEventHandler());
        assertFalse(u.getEventHandlerEnable());

        assertEquals(UpdateStatus.Success, u.setEventHandler("myCallback"));
        assertEquals("myCallback", u.getEventHandler());
        assertEquals(UpdateStatus.NoChange, u.setEventHandler("myCallback"));

        assertEquals(UpdateStatus.Success, u.setEventHandlerEnable(true));
        assertTrue(u.getEventHandlerEnable());
        assertEquals(UpdateStatus.NoChange, u.setEventHandlerEnable(true));
    }

    // ------------------------------------------------------------- setFont

    @Test
    public void setFontDerivesStyleFromAwtFontWhenNoDefaults() {
        // Isolate the JVM-wide static font defaults so the font's own metrics
        // drive the outcome, and restore them afterwards.
        String savedName = Uicontrol.getDefaultFontName();
        String savedWeight = Uicontrol.getDefaultFontWeight();
        String savedAngle = Uicontrol.getDefaultFontAngle();
        String savedUnits = Uicontrol.getDefaultFontUnits();
        double savedSize = Uicontrol.getDefaultFontSize();
        try {
            Uicontrol.setDefaultFontName("");
            Uicontrol.setDefaultFontWeight("");
            Uicontrol.setDefaultFontAngle("");
            Uicontrol.setDefaultFontUnits("");
            Uicontrol.setDefaultFontSize(0);

            Uicontrol u = new Uicontrol();
            // null font is a no-op.
            u.setFont(null);
            assertEquals("", u.getFontName());

            u.setFont(new Font("Dialog", Font.BOLD | Font.ITALIC, 14));
            assertEquals("Dialog", u.getFontName());
            assertEquals(14.0, u.getFontSize(), 0.0);
            assertEquals("italic", u.getFontAngle());
            assertEquals("bold", u.getFontWeight());

            Uicontrol plain = new Uicontrol();
            plain.setFont(new Font("Serif", Font.PLAIN, 10));
            assertEquals("normal", plain.getFontAngle());
            assertEquals("normal", plain.getFontWeight());
        } finally {
            Uicontrol.setDefaultFontName(savedName);
            Uicontrol.setDefaultFontWeight(savedWeight);
            Uicontrol.setDefaultFontAngle(savedAngle);
            Uicontrol.setDefaultFontUnits(savedUnits);
            Uicontrol.setDefaultFontSize(savedSize);
        }
    }

    @Test
    public void defaultFontStaticsRoundTrip() {
        String savedName = Uicontrol.getDefaultFontName();
        String savedWeight = Uicontrol.getDefaultFontWeight();
        String savedAngle = Uicontrol.getDefaultFontAngle();
        String savedUnits = Uicontrol.getDefaultFontUnits();
        double savedSize = Uicontrol.getDefaultFontSize();
        try {
            Uicontrol.setDefaultFontName("Times");
            Uicontrol.setDefaultFontWeight("bold");
            Uicontrol.setDefaultFontAngle("italic");
            Uicontrol.setDefaultFontUnits("pixels");
            Uicontrol.setDefaultFontSize(18);
            assertEquals("Times", Uicontrol.getDefaultFontName());
            assertEquals("bold", Uicontrol.getDefaultFontWeight());
            assertEquals("italic", Uicontrol.getDefaultFontAngle());
            assertEquals("pixels", Uicontrol.getDefaultFontUnits());
            assertEquals(18.0, Uicontrol.getDefaultFontSize(), 0.0);
        } finally {
            Uicontrol.setDefaultFontName(savedName);
            Uicontrol.setDefaultFontWeight(savedWeight);
            Uicontrol.setDefaultFontAngle(savedAngle);
            Uicontrol.setDefaultFontUnits(savedUnits);
            Uicontrol.setDefaultFontSize(savedSize);
        }
    }

    // ------------------------------------------------- property dispatch API

    @Test
    public void propertyDispatchRoundTripsScalarProperties() {
        Uicontrol u = new Uicontrol();

        Object pEnable = u.getPropertyFromName(__GO_UI_ENABLE__);
        assertEquals(UpdateStatus.Success, u.setProperty(pEnable, Boolean.FALSE));
        assertEquals(Boolean.FALSE, u.getProperty(pEnable));
        assertEquals(UpdateStatus.NoChange, u.setProperty(pEnable, Boolean.FALSE));

        Object pMax = u.getPropertyFromName(__GO_UI_MAX__);
        assertEquals(UpdateStatus.Success, u.setProperty(pMax, 7.0));
        assertEquals(7.0, ((Double) u.getProperty(pMax)).doubleValue(), 0.0);

        Object pRelief = u.getPropertyFromName(__GO_UI_RELIEF__);
        assertEquals(UpdateStatus.Success, u.setProperty(pRelief, "sunken"));
        assertEquals("sunken", u.getProperty(pRelief));

        Object pUnits = u.getPropertyFromName(__GO_UI_UNITS__);
        assertEquals(UpdateStatus.Success, u.setProperty(pUnits, "points"));
        assertEquals("points", u.getProperty(pUnits));
    }

    @Test
    public void propertyDispatchHandlesArrayAndDerivedProperties() {
        Uicontrol u = new Uicontrol();

        Object pPos = u.getPropertyFromName(__GO_POSITION__);
        assertEquals(UpdateStatus.Success, u.setProperty(pPos, new Double[] {1.0, 2.0, 3.0, 4.0}));
        assertArrayEquals(new Double[] {1.0, 2.0, 3.0, 4.0}, (Double[]) u.getProperty(pPos));

        Object pString = u.getPropertyFromName(__GO_UI_STRING__);
        assertEquals(UpdateStatus.Success, u.setProperty(pString, new String[] {"a", "b"}));
        assertArrayEquals(new String[] {"a", "b"}, (String[]) u.getProperty(pString));

        // STRING_SIZE is a read-derived property reporting the element count.
        Object pStringSize = u.getPropertyFromName(__GO_UI_STRING_SIZE__);
        assertEquals(2, ((Integer) u.getProperty(pStringSize)).intValue());
    }

    @Test
    public void unknownPropertyNameDelegatesToSuper() {
        // A property owned by GraphicObject (TAG) still resolves and round-trips.
        Uicontrol u = new Uicontrol();
        Object pTag = u.getPropertyFromName(__GO_TAG__);
        assertNotNull(pTag);
        u.setProperty(pTag, "hello");
        assertEquals("hello", u.getProperty(pTag));
    }

    // ------------------------------------------------------- nested enums

    @Test
    public void titlePositionEnumConversions() {
        assertEquals(TitlePositionType.TOP, TitlePositionType.intToEnum(0));
        assertEquals(TitlePositionType.LEFT, TitlePositionType.intToEnum(1));
        assertEquals(TitlePositionType.BOTTOM, TitlePositionType.intToEnum(2));
        assertEquals(TitlePositionType.RIGHT, TitlePositionType.intToEnum(3));
        assertEquals(TitlePositionType.TOP, TitlePositionType.intToEnum(99));

        assertEquals(TitlePositionType.LEFT, TitlePositionType.stringToEnum("left"));
        assertEquals(TitlePositionType.BOTTOM, TitlePositionType.stringToEnum("bottom"));
        assertEquals(TitlePositionType.RIGHT, TitlePositionType.stringToEnum("right"));
        assertEquals(TitlePositionType.TOP, TitlePositionType.stringToEnum("top"));
        assertEquals(TitlePositionType.TOP, TitlePositionType.stringToEnum("nonsense"));

        assertEquals("top", TitlePositionType.enumToString(TitlePositionType.TOP));
        assertEquals("left", TitlePositionType.enumToString(TitlePositionType.LEFT));
        assertEquals("bottom", TitlePositionType.enumToString(TitlePositionType.BOTTOM));
        assertEquals("right", TitlePositionType.enumToString(TitlePositionType.RIGHT));
    }

    @Test
    public void borderLayoutEnumConversions() {
        assertEquals(BorderLayoutType.BOTTOM, BorderLayoutType.intToEnum(0));
        assertEquals(BorderLayoutType.TOP, BorderLayoutType.intToEnum(1));
        assertEquals(BorderLayoutType.CENTER, BorderLayoutType.intToEnum(2));
        assertEquals(BorderLayoutType.LEFT, BorderLayoutType.intToEnum(3));
        assertEquals(BorderLayoutType.RIGHT, BorderLayoutType.intToEnum(4));
        assertEquals(BorderLayoutType.CENTER, BorderLayoutType.intToEnum(42));

        // Compass-letter driven variant.
        assertEquals(BorderLayoutType.CENTER, BorderLayoutType.stringToEnum2(null));
        assertEquals(BorderLayoutType.CENTER, BorderLayoutType.stringToEnum2(""));
        assertEquals(BorderLayoutType.TOP, BorderLayoutType.stringToEnum2("north"));
        assertEquals(BorderLayoutType.RIGHT, BorderLayoutType.stringToEnum2("East"));
        assertEquals(BorderLayoutType.BOTTOM, BorderLayoutType.stringToEnum2("south"));
        assertEquals(BorderLayoutType.LEFT, BorderLayoutType.stringToEnum2("West"));
        assertEquals(BorderLayoutType.CENTER, BorderLayoutType.stringToEnum2("center"));

        // Exact-name variant.
        assertEquals(BorderLayoutType.BOTTOM, BorderLayoutType.stringToEnum("bottom"));
        assertEquals(BorderLayoutType.LEFT, BorderLayoutType.stringToEnum("left"));
        assertEquals(BorderLayoutType.RIGHT, BorderLayoutType.stringToEnum("right"));
        assertEquals(BorderLayoutType.TOP, BorderLayoutType.stringToEnum("top"));
        assertEquals(BorderLayoutType.CENTER, BorderLayoutType.stringToEnum("whatever"));

        assertEquals("bottom", BorderLayoutType.enumToString(BorderLayoutType.BOTTOM));
        assertEquals("center", BorderLayoutType.enumToString(BorderLayoutType.CENTER));
        assertEquals("left", BorderLayoutType.enumToString(BorderLayoutType.LEFT));
        assertEquals("right", BorderLayoutType.enumToString(BorderLayoutType.RIGHT));
        assertEquals("top", BorderLayoutType.enumToString(BorderLayoutType.TOP));
    }

    @Test
    public void fillTypeEnumConversions() {
        assertEquals(FillType.NONE, FillType.intToEnum(0));
        assertEquals(FillType.VERTICAL, FillType.intToEnum(1));
        assertEquals(FillType.HORIZONTAL, FillType.intToEnum(2));
        assertEquals(FillType.BOTH, FillType.intToEnum(3));
        assertEquals(FillType.NONE, FillType.intToEnum(7));

        assertEquals(FillType.NONE, FillType.stringToEnum(null));
        assertEquals(FillType.NONE, FillType.stringToEnum(""));
        assertEquals(FillType.VERTICAL, FillType.stringToEnum("vertical"));
        assertEquals(FillType.HORIZONTAL, FillType.stringToEnum("Horizontal"));
        assertEquals(FillType.BOTH, FillType.stringToEnum("both"));
        assertEquals(FillType.NONE, FillType.stringToEnum("none"));

        assertEquals("both", FillType.enumToString(FillType.BOTH));
        assertEquals("horizontal", FillType.enumToString(FillType.HORIZONTAL));
        assertEquals("none", FillType.enumToString(FillType.NONE));
        assertEquals("vertical", FillType.enumToString(FillType.VERTICAL));
    }

    @Test
    public void anchorTypeIntAndStringConversions() {
        assertEquals(AnchorType.CENTER, AnchorType.intToEnum(0));
        assertEquals(AnchorType.UPPER, AnchorType.intToEnum(1));
        assertEquals(AnchorType.LOWER, AnchorType.intToEnum(2));
        assertEquals(AnchorType.RIGHT, AnchorType.intToEnum(3));
        assertEquals(AnchorType.LEFT, AnchorType.intToEnum(4));
        assertEquals(AnchorType.UPPER_RIGHT, AnchorType.intToEnum(5));
        assertEquals(AnchorType.UPPER_LEFT, AnchorType.intToEnum(6));
        assertEquals(AnchorType.LOWER_RIGHT, AnchorType.intToEnum(7));
        assertEquals(AnchorType.LOWER_LEFT, AnchorType.intToEnum(8));
        assertEquals(AnchorType.CENTER, AnchorType.intToEnum(99));

        // Exact-name variant.
        assertEquals(AnchorType.LEFT, AnchorType.stringToEnum("left"));
        assertEquals(AnchorType.LOWER, AnchorType.stringToEnum("lower"));
        assertEquals(AnchorType.LOWER_LEFT, AnchorType.stringToEnum("lower_left"));
        assertEquals(AnchorType.LOWER_RIGHT, AnchorType.stringToEnum("lower_right"));
        assertEquals(AnchorType.RIGHT, AnchorType.stringToEnum("right"));
        assertEquals(AnchorType.UPPER, AnchorType.stringToEnum("upper"));
        assertEquals(AnchorType.UPPER_LEFT, AnchorType.stringToEnum("upper_left"));
        assertEquals(AnchorType.UPPER_RIGHT, AnchorType.stringToEnum("upper_right"));
        assertEquals(AnchorType.CENTER, AnchorType.stringToEnum("mystery"));
    }

    @Test
    public void anchorTypeCompassConversions() {
        assertEquals(AnchorType.CENTER, AnchorType.stringToEnum2(null));
        assertEquals(AnchorType.CENTER, AnchorType.stringToEnum2(""));
        assertEquals(AnchorType.RIGHT, AnchorType.stringToEnum2("east"));
        assertEquals(AnchorType.LEFT, AnchorType.stringToEnum2("west"));
        assertEquals(AnchorType.UPPER, AnchorType.stringToEnum2("n"));
        assertEquals(AnchorType.UPPER, AnchorType.stringToEnum2("north"));
        assertEquals(AnchorType.UPPER_RIGHT, AnchorType.stringToEnum2("ne"));
        assertEquals(AnchorType.UPPER_LEFT, AnchorType.stringToEnum2("nw"));
        assertEquals(AnchorType.LOWER, AnchorType.stringToEnum2("s"));
        assertEquals(AnchorType.LOWER, AnchorType.stringToEnum2("south"));
        assertEquals(AnchorType.LOWER_RIGHT, AnchorType.stringToEnum2("se"));
        assertEquals(AnchorType.LOWER_LEFT, AnchorType.stringToEnum2("sw"));
        assertEquals(AnchorType.CENTER, AnchorType.stringToEnum2("center"));
    }

    @Test
    public void anchorTypeEnumToString() {
        assertEquals("center", AnchorType.enumToString(AnchorType.CENTER));
        assertEquals("left", AnchorType.enumToString(AnchorType.LEFT));
        assertEquals("lower", AnchorType.enumToString(AnchorType.LOWER));
        assertEquals("lower_left", AnchorType.enumToString(AnchorType.LOWER_LEFT));
        assertEquals("lower_right", AnchorType.enumToString(AnchorType.LOWER_RIGHT));
        assertEquals("right", AnchorType.enumToString(AnchorType.RIGHT));
        assertEquals("upper", AnchorType.enumToString(AnchorType.UPPER));
        assertEquals("upper_left", AnchorType.enumToString(AnchorType.UPPER_LEFT));
        assertEquals("upper_right", AnchorType.enumToString(AnchorType.UPPER_RIGHT));
    }
}
