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

import java.util.ArrayList;

import org.scilab.modules.graphic_objects.graphicObject.GraphicObject.UpdateStatus;
import org.scilab.modules.graphic_objects.textObject.Font;
import org.scilab.modules.graphic_objects.textObject.FormattedText;

/**
 * Hermetic unit tests for {@link TicksProperty}: the ticks holder for one axis,
 * which switches between an automatic and a user ticks set based on its "auto"
 * flag.
 */
public class TicksPropertyTest {

    @Test
    public void constructorDefaults() {
        TicksProperty tp = new TicksProperty();
        assertFalse(tp.getAuto());
        assertEquals(Integer.valueOf(0), tp.getSubticks());
        assertEquals("", tp.getFormat());
        assertArrayEquals(new Double[] {1.0, 0.0}, tp.getSTFactors());
        assertNotNull(tp.getDefaultFont());
        // Not automatic => the user ticks set (empty) is active.
        assertEquals(Integer.valueOf(0), tp.getNumber());
        assertEquals(0, tp.getLocations().length);
    }

    @Test
    public void autoFlagSelectsTheAutomaticTicksSet() {
        TicksProperty tp = new TicksProperty();
        assertEquals(UpdateStatus.Success, tp.setAuto(true));
        assertTrue(tp.getAuto());
        // The automatic set is seeded with 11 ticks.
        assertEquals(Integer.valueOf(11), tp.getNumber());
        assertEquals(UpdateStatus.NoChange, tp.setAuto(true));
        assertEquals(UpdateStatus.Success, tp.setAuto(false));
    }

    @Test
    public void subticksRoundTrips() {
        TicksProperty tp = new TicksProperty();
        assertEquals(UpdateStatus.Success, tp.setSubticks(3));
        assertEquals(Integer.valueOf(3), tp.getSubticks());
        assertEquals(UpdateStatus.NoChange, tp.setSubticks(3));
    }

    @Test
    public void formatRoundTrips() {
        TicksProperty tp = new TicksProperty();
        assertEquals(UpdateStatus.Success, tp.setFormat("%.2f"));
        assertEquals("%.2f", tp.getFormat());
        assertEquals(UpdateStatus.NoChange, tp.setFormat("%.2f"));
    }

    @Test
    public void scaleTranslateFactorsRoundTrip() {
        TicksProperty tp = new TicksProperty();
        assertEquals(UpdateStatus.Success, tp.setSTFactors(new Double[] {2.0, 3.0}));
        assertArrayEquals(new Double[] {2.0, 3.0}, tp.getSTFactors());
        // Equal factors report no change.
        assertEquals(UpdateStatus.NoChange, tp.setSTFactors(new Double[] {2.0, 3.0}));
    }

    @Test
    public void settingLocationsResizesTheActiveTicksSet() {
        TicksProperty tp = new TicksProperty();
        assertEquals(UpdateStatus.Success, tp.setLocations(new Double[] {0.0, 0.5, 1.0}));
        assertEquals(Integer.valueOf(3), tp.getNumber());
        assertArrayEquals(new Double[] {0.0, 0.5, 1.0}, tp.getLocations());
    }

    @Test
    public void equalsIsReflexive() {
        TicksProperty tp = new TicksProperty();
        assertEquals(tp, tp);
    }

    @Test
    public void equalsRejectsNullOtherTypeAndDifferingAuto() {
        TicksProperty tp = new TicksProperty();
        assertNotEquals(tp, null);
        assertNotEquals(tp, "ticks");

        TicksProperty other = new TicksProperty();
        other.setAuto(true);
        assertNotEquals(tp, other);
    }

    // ---- equals: Font-identity quirk + automatic-set branch ---------------

    /**
     * Characterization: {@link TicksProperty#equals} AND-guards on
     * {@code defaultFont.equals(other.defaultFont)}, but {@link Font} does not
     * override {@code equals} (identity semantics). Two independently
     * constructed instances therefore compare unequal despite identical field
     * values, and a copy — which builds a fresh {@code Font} — does too.
     */
    @Test
    public void distinctInstancesAreUnequalBecauseFontUsesIdentityEquals() {
        assertNotEquals(new TicksProperty(), new TicksProperty());
        TicksProperty src = new TicksProperty();
        assertNotEquals(src, new TicksProperty(src));
    }

    @Test
    public void reflexiveEqualsUnderAutoComparesAutomaticTicks() {
        TicksProperty tp = new TicksProperty();
        tp.setAuto(true);
        // Same instance shares its defaultFont, so equals reaches (and passes)
        // the automatic-ticks comparison branch.
        assertEquals(tp, tp);
    }

    // ---- copy constructor -------------------------------------------------

    @Test
    public void copyConstructorCopiesStateIntoIndependentInstance() {
        TicksProperty src = new TicksProperty();
        src.setAuto(true);
        src.setSubticks(4);
        src.setFormat("%g");
        src.setSTFactors(new Double[] {2.0, 5.0});
        src.setLocations(new Double[] {0.0, 10.0, 20.0}); // auto -> automatic set

        TicksProperty copy = new TicksProperty(src);
        assertTrue(copy.getAuto());
        assertEquals(Integer.valueOf(4), copy.getSubticks());
        assertEquals("%g", copy.getFormat());
        assertArrayEquals(new Double[] {2.0, 5.0}, copy.getSTFactors());
        assertArrayEquals(new Double[] {0.0, 10.0, 20.0}, copy.getLocations());

        // The copied automatic locations live in their own array.
        src.setLocations(new Double[] {99.0, 99.0, 99.0});
        assertArrayEquals(new Double[] {0.0, 10.0, 20.0}, copy.getLocations());
    }

    // ---- labels as a FormattedText list -----------------------------------

    @Test
    public void userLabelsListSetGetIsDeepCopiedAndDetectsNoChange() {
        TicksProperty tp = new TicksProperty(); // auto == false -> user ticks
        assertTrue(tp.getLabels().isEmpty());

        ArrayList<FormattedText> list = new ArrayList<FormattedText>();
        list.add(new FormattedText("one", new Font()));
        list.add(new FormattedText("two", new Font()));
        assertEquals(UpdateStatus.Success, tp.setLabels(list));

        ArrayList<FormattedText> stored = tp.getLabels();
        assertEquals(2, stored.size());
        assertEquals("one", stored.get(0).getText());
        assertEquals("two", stored.get(1).getText());
        // Deep copy: stored labels are fresh instances, not the caller's.
        assertNotSame(list.get(0), stored.get(0));

        // Re-setting the already-stored list is a NoChange.
        assertEquals(UpdateStatus.NoChange, tp.setLabels(stored));
    }

    // ---- label strings (require locations first) --------------------------

    @Test
    public void userLabelsStringsRequireMatchingLocationCount() {
        TicksProperty tp = new TicksProperty();
        // With zero locations the count mismatch rejects the label strings.
        assertEquals(UpdateStatus.NoChange, tp.setLabelsStrings(new String[] {"a", "b", "c"}));

        tp.setLocations(new Double[] {0.0, 1.0, 2.0});
        assertEquals(UpdateStatus.Success, tp.setLabelsStrings(new String[] {"a", "b", "c"}));
        assertArrayEquals(new String[] {"a", "b", "c"}, tp.getLabelsStrings());

        // Identical strings -> NoChange; a single change -> Success.
        assertEquals(UpdateStatus.NoChange, tp.setLabelsStrings(new String[] {"a", "b", "c"}));
        assertEquals(UpdateStatus.Success, tp.setLabelsStrings(new String[] {"a", "b", "X"}));
        assertArrayEquals(new String[] {"a", "b", "X"}, tp.getLabelsStrings());
    }

    // ---- label interpreters (single value broadcast to all) ---------------

    @Test
    public void userLabelsInterpretersBroadcastSingleValue() {
        TicksProperty tp = new TicksProperty();
        tp.setLocations(new Double[] {0.0, 1.0, 2.0});
        tp.setLabelsStrings(new String[] {"a", "b", "c"});

        assertEquals(UpdateStatus.Success, tp.setLabelsInterpreters(new String[] {"latex"}));
        assertArrayEquals(new String[] {"latex", "latex", "latex"}, tp.getLabelsInterpreters());
        assertEquals(UpdateStatus.NoChange, tp.setLabelsInterpreters(new String[] {"latex"}));
    }

    // ---- font style/size/color/fractional (defaultFont + both ticks sets) --

    @Test
    public void fontStyleGetSetPropagatesToDefaultFont() {
        TicksProperty tp = new TicksProperty();
        assertEquals(Integer.valueOf(6), tp.getFontStyle()); // Font() default style
        assertEquals(UpdateStatus.Success, tp.setFontStyle(2));
        assertEquals(Integer.valueOf(2), tp.getFontStyle());
        assertEquals(Integer.valueOf(2), tp.getDefaultFont().getStyle());
        assertEquals(UpdateStatus.NoChange, tp.setFontStyle(2));
    }

    @Test
    public void fontSizeGetSetPropagatesToDefaultFont() {
        TicksProperty tp = new TicksProperty();
        assertEquals(Double.valueOf(1.0), tp.getFontSize());
        assertEquals(UpdateStatus.Success, tp.setFontSize(3.0));
        assertEquals(Double.valueOf(3.0), tp.getFontSize());
        assertEquals(Double.valueOf(3.0), tp.getDefaultFont().getSize());
        assertEquals(UpdateStatus.NoChange, tp.setFontSize(3.0));
    }

    @Test
    public void fontColorGetSetPropagatesToDefaultFont() {
        TicksProperty tp = new TicksProperty();
        assertEquals(Integer.valueOf(-1), tp.getFontColor());
        assertEquals(UpdateStatus.Success, tp.setFontColor(5));
        assertEquals(Integer.valueOf(5), tp.getFontColor());
        assertEquals(UpdateStatus.NoChange, tp.setFontColor(5));
    }

    @Test
    public void fontFractionalGetSetPropagatesToDefaultFont() {
        TicksProperty tp = new TicksProperty();
        assertTrue(tp.getFontFractional());
        assertEquals(UpdateStatus.Success, tp.setFontFractional(false));
        assertFalse(tp.getFontFractional());
        assertEquals(UpdateStatus.NoChange, tp.setFontFractional(false));
    }

    @Test
    public void getDefaultFontReturnsTheLiveBackingFont() {
        TicksProperty tp = new TicksProperty();
        assertNotNull(tp.getDefaultFont());
        tp.setFontSize(7.0);
        assertEquals(Double.valueOf(7.0), tp.getDefaultFont().getSize());
    }
}
