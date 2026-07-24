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

package org.scilab.modules.graphic_objects.uicontrol.frame.border;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_LINE_THICKNESS__;
import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_POSITION__;
import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_TITLE__;
import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_UI_FRAME_BORDER__;
import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_UI_FRAME_BORDER_COLOR__;
import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_UI_FRAME_BORDER_TYPE__;

import org.scilab.modules.graphic_objects.graphicObject.GraphicObject.UpdateStatus;
import org.scilab.modules.graphic_objects.uicontrol.frame.border.FrameBorder.BorderType;
import org.scilab.modules.graphic_objects.uicontrol.frame.border.FrameBorder.JustificationType;
import org.scilab.modules.graphic_objects.uicontrol.frame.border.FrameBorder.TitlePositionType;

/**
 * Hermetic unit tests for {@link FrameBorder}: a pure {@code GraphicObject}
 * data holder (empty constructor, no native runtime) plus its three public
 * enum converters (BorderType, JustificationType, TitlePositionType).
 */
public class FrameBorderTest {

    // ---- type / identity -------------------------------------------------

    @Test
    public void typeIsFrameBorder() {
        assertEquals(Integer.valueOf(__GO_UI_FRAME_BORDER__), new FrameBorder().getType());
    }

    // ---- defaults --------------------------------------------------------

    @Test
    public void freshBorderHasMostlyNullProperties() {
        FrameBorder fb = new FrameBorder();
        assertNull(fb.getBorderType());
        assertNull(fb.getStyle());
        assertNull(fb.getColor());
        assertNull(fb.getTitle());
        assertNull(fb.getThickness());
        assertNull(fb.getRounded());
        assertNull(fb.getPosition());
        assertNull(fb.getJustification());
        assertNull(fb.getFontName());
        assertNull(fb.getFontSize());
    }

    @Test
    public void titlePositionDefaultsToTopNotNull() {
        // Unlike the other enum-backed props, titlePosition is initialised
        // to TOP, so its int accessor returns 0 rather than null.
        FrameBorder fb = new FrameBorder();
        assertEquals(Integer.valueOf(0), fb.getTitlePosition());
        assertEquals(TitlePositionType.TOP, fb.getTitlePositionAsEnum());
    }

    // ---- string setters: Success then NoChange ---------------------------

    @Test
    public void stringSettersSucceedThenNoChange() {
        FrameBorder fb = new FrameBorder();

        assertEquals(UpdateStatus.Success, fb.setColor("red"));
        assertEquals("red", fb.getColor());
        assertEquals(UpdateStatus.NoChange, fb.setColor("red"));

        assertEquals(UpdateStatus.Success, fb.setTitle("hello"));
        assertEquals("hello", fb.getTitle());
        assertEquals(UpdateStatus.NoChange, fb.setTitle("hello"));

        assertEquals(UpdateStatus.Success, fb.setFontName("Serif"));
        assertEquals(UpdateStatus.NoChange, fb.setFontName("Serif"));

        assertEquals(UpdateStatus.Success, fb.setHlIn("h1"));
        assertEquals(UpdateStatus.Success, fb.setHlOut("h2"));
        assertEquals(UpdateStatus.Success, fb.setShadowIn("s1"));
        assertEquals(UpdateStatus.Success, fb.setShadowOut("s2"));
        assertEquals("h1", fb.getHlIn());
        assertEquals("h2", fb.getHlOut());
        assertEquals("s1", fb.getShadowIn());
        assertEquals("s2", fb.getShadowOut());
    }

    @Test
    public void integerSettersSucceedThenNoChange() {
        FrameBorder fb = new FrameBorder();

        assertEquals(UpdateStatus.Success, fb.setThickness(3));
        assertEquals(Integer.valueOf(3), fb.getThickness());
        assertEquals(UpdateStatus.NoChange, fb.setThickness(3));

        assertEquals(UpdateStatus.Success, fb.setFontSize(12));
        assertEquals(Integer.valueOf(12), fb.getFontSize());
        assertEquals(UpdateStatus.NoChange, fb.setFontSize(12));

        assertEquals(UpdateStatus.Success, fb.setInBorder(5));
        assertEquals(UpdateStatus.Success, fb.setOutBorder(6));
        assertEquals(UpdateStatus.Success, fb.setTitleBorder(7));
        assertEquals(Integer.valueOf(5), fb.getInBorder());
        assertEquals(Integer.valueOf(6), fb.getOutBorder());
        assertEquals(Integer.valueOf(7), fb.getTitleBorder());
    }

    @Test
    public void roundedBooleanSetter() {
        FrameBorder fb = new FrameBorder();
        assertEquals(UpdateStatus.Success, fb.setRounded(Boolean.TRUE));
        assertTrue(fb.getRounded());
        assertEquals(UpdateStatus.NoChange, fb.setRounded(Boolean.TRUE));
        assertEquals(UpdateStatus.Success, fb.setRounded(Boolean.FALSE));
        assertFalse(fb.getRounded());
    }

    @Test
    public void positionUsesArrayContentEquality() {
        FrameBorder fb = new FrameBorder();
        Double[] tlbr = {1.0, 2.0, 3.0, 4.0};
        assertEquals(UpdateStatus.Success, fb.setPosition(tlbr));
        assertArrayEquals(tlbr, fb.getPosition());
        // A distinct array instance with identical content is a NoChange.
        assertEquals(UpdateStatus.NoChange, fb.setPosition(new Double[] {1.0, 2.0, 3.0, 4.0}));
        assertEquals(UpdateStatus.Success, fb.setPosition(new Double[] {9.0, 9.0, 9.0, 9.0}));
    }

    // ---- enum-backed int setters via FrameBorder -------------------------

    @Test
    public void borderTypeIntRoundTrip() {
        FrameBorder fb = new FrameBorder();
        assertEquals(UpdateStatus.Success, fb.setBorderType(1)); // LOWERED
        assertEquals(Integer.valueOf(1), fb.getBorderType());
        assertEquals(BorderType.LOWERED, fb.getBorderTypeAsEnum());
        assertEquals(UpdateStatus.NoChange, fb.setBorderType(1));
        assertEquals(UpdateStatus.Success, fb.setBorderType(0)); // RAISED
        assertEquals(Integer.valueOf(0), fb.getBorderType());
    }

    @Test
    public void styleIntMapsThroughFrameBorderType() {
        FrameBorder fb = new FrameBorder();
        assertEquals(UpdateStatus.Success, fb.setStyle(1)); // LINE (ordinal 1)
        assertEquals(Integer.valueOf(1), fb.getStyle());
        assertEquals(FrameBorderType.LINE, fb.getStyleAsEnum());
        assertEquals(UpdateStatus.NoChange, fb.setStyle(1));
        assertEquals(UpdateStatus.Success, fb.setStyle(4)); // ETCHED
        assertEquals(FrameBorderType.ETCHED, fb.getStyleAsEnum());
    }

    @Test
    public void justificationIntRoundTrip() {
        FrameBorder fb = new FrameBorder();
        assertEquals(UpdateStatus.Success, fb.setJustification(2)); // CENTER
        assertEquals(Integer.valueOf(2), fb.getJustification());
        assertEquals(JustificationType.CENTER, fb.getJustificationAsEnum());
        assertEquals(UpdateStatus.NoChange, fb.setJustification(2));
    }

    @Test
    public void titlePositionIntRoundTrip() {
        FrameBorder fb = new FrameBorder();
        assertEquals(UpdateStatus.Success, fb.setTitlePosition(3)); // BOTTOM
        assertEquals(Integer.valueOf(3), fb.getTitlePosition());
        assertEquals(TitlePositionType.BOTTOM, fb.getTitlePositionAsEnum());
        // Setting back to the initial TOP (0) from a fresh object is NoChange.
        assertEquals(UpdateStatus.NoChange, new FrameBorder().setTitlePosition(0));
    }

    // ---- generic property dispatch (getPropertyFromName -> get/set) ------

    @Test
    public void propertyDispatchRoundTripsStringColor() {
        FrameBorder fb = new FrameBorder();
        Object prop = fb.getPropertyFromName(__GO_UI_FRAME_BORDER_COLOR__);
        assertNotNull(prop);
        assertEquals(UpdateStatus.Success, fb.setProperty(prop, "blue"));
        assertEquals("blue", fb.getProperty(prop));
    }

    @Test
    public void propertyDispatchRoundTripsTitleAndType() {
        FrameBorder fb = new FrameBorder();

        Object title = fb.getPropertyFromName(__GO_TITLE__);
        fb.setProperty(title, "T");
        assertEquals("T", fb.getProperty(title));

        Object type = fb.getPropertyFromName(__GO_UI_FRAME_BORDER_TYPE__);
        fb.setProperty(type, Integer.valueOf(1));
        assertEquals(Integer.valueOf(1), fb.getProperty(type));
    }

    @Test
    public void propertyDispatchRoundTripsPosition() {
        FrameBorder fb = new FrameBorder();
        Object pos = fb.getPropertyFromName(__GO_POSITION__);
        Double[] tlbr = {0.0, 1.0, 2.0, 3.0};
        fb.setProperty(pos, tlbr);
        assertArrayEquals(tlbr, (Double[]) fb.getProperty(pos));
    }

    @Test
    public void thicknessPropertyMapsToLineThickness() {
        FrameBorder fb = new FrameBorder();
        Object thick = fb.getPropertyFromName(__GO_LINE_THICKNESS__);
        fb.setProperty(thick, Integer.valueOf(8));
        assertEquals(Integer.valueOf(8), fb.getProperty(thick));
    }

    // ---- BorderType enum -------------------------------------------------

    @Test
    public void borderTypeIntToEnum() {
        assertEquals(BorderType.RAISED, BorderType.intToEnum(0));
        assertEquals(BorderType.LOWERED, BorderType.intToEnum(1));
        assertEquals(BorderType.RAISED, BorderType.intToEnum(2));   // default
        assertEquals(BorderType.RAISED, BorderType.intToEnum(-1));  // default
    }

    @Test
    public void borderTypeIntToEnumNullThrows() {
        // switch on a null Integer unboxes -> NullPointerException.
        assertThrows(NullPointerException.class, () -> BorderType.intToEnum(null));
    }

    @Test
    public void borderTypeStringToEnum() {
        assertNull(BorderType.stringToEnum(null));
        assertNull(BorderType.stringToEnum(""));
        assertEquals(BorderType.LOWERED, BorderType.stringToEnum("lowered"));
        assertEquals(BorderType.LOWERED, BorderType.stringToEnum("L"));
        assertEquals(BorderType.RAISED, BorderType.stringToEnum("raised"));
        assertEquals(BorderType.RAISED, BorderType.stringToEnum("xyz"));
    }

    // ---- JustificationType enum -----------------------------------------

    @Test
    public void justificationIntToEnum() {
        assertEquals(JustificationType.LEADING, JustificationType.intToEnum(0));
        assertEquals(JustificationType.LEFT, JustificationType.intToEnum(1));
        assertEquals(JustificationType.CENTER, JustificationType.intToEnum(2));
        assertEquals(JustificationType.RIGHT, JustificationType.intToEnum(3));
        assertEquals(JustificationType.TRAILING, JustificationType.intToEnum(4));
        assertEquals(JustificationType.LEADING, JustificationType.intToEnum(99)); // default
    }

    @Test
    public void justificationStringToEnumFirstLetterBranches() {
        assertNull(JustificationType.stringToEnum(null));
        assertNull(JustificationType.stringToEnum(""));
        assertEquals(JustificationType.CENTER, JustificationType.stringToEnum("center"));
        assertEquals(JustificationType.RIGHT, JustificationType.stringToEnum("right"));
        assertEquals(JustificationType.TRAILING, JustificationType.stringToEnum("trailing"));
        assertEquals(JustificationType.LEADING, JustificationType.stringToEnum("xyz"));
    }

    @Test
    public void justificationStringToEnumLBranchInspectsFourthChar() {
        // For an 'l'-initial string the 4th char decides LEFT vs LEADING.
        // "left" has 't' at index 3, so it maps to LEADING, not LEFT.
        assertEquals(JustificationType.LEADING, JustificationType.stringToEnum("left"));
        assertEquals(JustificationType.LEADING, JustificationType.stringToEnum("leading"));
        // A 4th char of 'f' is what actually selects LEFT.
        assertEquals(JustificationType.LEFT, JustificationType.stringToEnum("leaf"));
    }

    @Test
    public void justificationStringToEnumShortLStringThrows() {
        // 'l'-initial but shorter than 4 chars -> chars[3] is out of bounds.
        assertThrows(IndexOutOfBoundsException.class,
                     () -> JustificationType.stringToEnum("lo"));
    }

    // ---- TitlePositionType enum -----------------------------------------

    @Test
    public void titlePositionIntToEnum() {
        assertEquals(TitlePositionType.TOP, TitlePositionType.intToEnum(0));
        assertEquals(TitlePositionType.ABOVE_TOP, TitlePositionType.intToEnum(1));
        assertEquals(TitlePositionType.BELOW_TOP, TitlePositionType.intToEnum(2));
        assertEquals(TitlePositionType.BOTTOM, TitlePositionType.intToEnum(3));
        assertEquals(TitlePositionType.ABOVE_BOTTOM, TitlePositionType.intToEnum(4));
        assertEquals(TitlePositionType.BELOW_BOTTOM, TitlePositionType.intToEnum(5));
        assertEquals(TitlePositionType.TOP, TitlePositionType.intToEnum(42)); // default
    }

    @Test
    public void titlePositionStringToEnum() {
        assertNull(TitlePositionType.stringToEnum(null));
        assertNull(TitlePositionType.stringToEnum(""));
        assertEquals(TitlePositionType.ABOVE_TOP, TitlePositionType.stringToEnum("above_top"));
        assertEquals(TitlePositionType.BELOW_TOP, TitlePositionType.stringToEnum("below_top"));
        assertEquals(TitlePositionType.BOTTOM, TitlePositionType.stringToEnum("bottom"));
        assertEquals(TitlePositionType.ABOVE_BOTTOM, TitlePositionType.stringToEnum("above_bottom"));
        assertEquals(TitlePositionType.BELOW_BOTTOM, TitlePositionType.stringToEnum("below_bottom"));
        assertEquals(TitlePositionType.TOP, TitlePositionType.stringToEnum("top"));
        assertEquals(TitlePositionType.TOP, TitlePositionType.stringToEnum("unknown"));
    }

    @Test
    public void titlePositionStringToEnumIsCaseInsensitive() {
        assertEquals(TitlePositionType.ABOVE_TOP, TitlePositionType.stringToEnum("ABOVE_TOP"));
        assertEquals(TitlePositionType.BELOW_BOTTOM, TitlePositionType.stringToEnum("Below_Bottom"));
    }
}
