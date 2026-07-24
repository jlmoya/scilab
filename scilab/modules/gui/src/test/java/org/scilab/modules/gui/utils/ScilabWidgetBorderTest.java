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

package org.scilab.modules.gui.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.awt.Insets;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;

import javax.swing.border.BevelBorder;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.EtchedBorder;
import javax.swing.border.LineBorder;

/**
 * Hermetic unit tests for {@link ScilabWidgetBorder}, the holder of the six
 * pre-built {@link Border} instances used for the Scilab widget relief property
 * (FLAT / GROOVE / RAISED / RIDGE / SOLID / SUNKEN).
 *
 * <p>The borders are built by {@code BorderFactory} at class-load time; none of
 * them needs a display peer or the native runtime, so these tests run on a
 * headless JVM. Each test pins the <em>kind</em> of border a field represents
 * (its Swing type plus the etch/bevel/line specifics), because that mapping is
 * the class's whole contract.</p>
 */
class ScilabWidgetBorderTest {

    private static final String[] FIELD_NAMES =
        {"FLAT", "GROOVE", "RAISED", "RIDGE", "SOLID", "SUNKEN"};

    // ---- Every constant exists and is initialised -----------------------

    @Test
    void allBorderConstantsAreNonNull() {
        assertNotNull(ScilabWidgetBorder.FLAT);
        assertNotNull(ScilabWidgetBorder.GROOVE);
        assertNotNull(ScilabWidgetBorder.RAISED);
        assertNotNull(ScilabWidgetBorder.RIDGE);
        assertNotNull(ScilabWidgetBorder.SOLID);
        assertNotNull(ScilabWidgetBorder.SUNKEN);
    }

    // ---- The identity (type + specifics) of each border -----------------

    @Test
    void flatIsAZeroInsetEmptyBorder() {
        assertInstanceOf(EmptyBorder.class, ScilabWidgetBorder.FLAT);
        // FLAT means "draw nothing": the border must contribute no insets.
        Insets insets = ((EmptyBorder) ScilabWidgetBorder.FLAT).getBorderInsets(null);
        assertEquals(new Insets(0, 0, 0, 0), insets);
    }

    @Test
    void grooveIsALoweredEtchedBorder() {
        assertInstanceOf(EtchedBorder.class, ScilabWidgetBorder.GROOVE);
        assertEquals(EtchedBorder.LOWERED, ((EtchedBorder) ScilabWidgetBorder.GROOVE).getEtchType());
    }

    @Test
    void ridgeIsARaisedEtchedBorder() {
        assertInstanceOf(EtchedBorder.class, ScilabWidgetBorder.RIDGE);
        assertEquals(EtchedBorder.RAISED, ((EtchedBorder) ScilabWidgetBorder.RIDGE).getEtchType());
    }

    @Test
    void raisedIsARaisedBevelBorder() {
        assertInstanceOf(BevelBorder.class, ScilabWidgetBorder.RAISED);
        assertEquals(BevelBorder.RAISED, ((BevelBorder) ScilabWidgetBorder.RAISED).getBevelType());
    }

    @Test
    void sunkenIsALoweredBevelBorder() {
        assertInstanceOf(BevelBorder.class, ScilabWidgetBorder.SUNKEN);
        assertEquals(BevelBorder.LOWERED, ((BevelBorder) ScilabWidgetBorder.SUNKEN).getBevelType());
    }

    @Test
    void solidIsABlackOnePixelLineBorder() {
        assertInstanceOf(LineBorder.class, ScilabWidgetBorder.SOLID);
        LineBorder line = (LineBorder) ScilabWidgetBorder.SOLID;
        assertEquals(Color.BLACK, line.getLineColor());
        assertEquals(1, line.getThickness());
    }

    // ---- GROOVE vs RIDGE are genuinely distinct (opposite etch) ---------

    /**
     * GROOVE and RIDGE are both {@link EtchedBorder}s but must be opposite
     * etchings; a regression that made them identical would break the visual
     * distinction between the two reliefs.
     */
    @Test
    void grooveAndRidgeAreOppositeEtchings() {
        int groove = ((EtchedBorder) ScilabWidgetBorder.GROOVE).getEtchType();
        int ridge = ((EtchedBorder) ScilabWidgetBorder.RIDGE).getEtchType();
        assertNotEquals(groove, ridge);
    }

    /**
     * Likewise RAISED and SUNKEN must be opposite bevels.
     */
    @Test
    void raisedAndSunkenAreOppositeBevels() {
        int raised = ((BevelBorder) ScilabWidgetBorder.RAISED).getBevelType();
        int sunken = ((BevelBorder) ScilabWidgetBorder.SUNKEN).getBevelType();
        assertNotEquals(raised, sunken);
    }

    // ---- Field-declaration contract -------------------------------------

    /**
     * Every border is exposed as a {@code public static final} field so callers
     * can reference the shared instance directly.
     */
    @Test
    void everyBorderFieldIsPublicStaticFinalBorder() throws Exception {
        for (String name : FIELD_NAMES) {
            Field f = ScilabWidgetBorder.class.getField(name);
            int mods = f.getModifiers();
            assertTrue(Modifier.isPublic(mods), name + " must be public");
            assertTrue(Modifier.isStatic(mods), name + " must be static");
            assertTrue(Modifier.isFinal(mods), name + " must be final");
            assertEquals(Border.class, f.getType(), name + " must be declared as Border");
        }
    }

    // ---- Utility-class contract -----------------------------------------

    @Test
    void classIsFinal() {
        assertTrue(Modifier.isFinal(ScilabWidgetBorder.class.getModifiers()));
    }

    /**
     * {@code ScilabWidgetBorder} is a pure constant holder: its sole constructor
     * is private and rejects reflective instantiation with
     * {@link UnsupportedOperationException}.
     */
    @Test
    void constructorIsPrivateAndThrows() throws Exception {
        Constructor<ScilabWidgetBorder> ctor = ScilabWidgetBorder.class.getDeclaredConstructor();
        assertTrue(Modifier.isPrivate(ctor.getModifiers()));
        ctor.setAccessible(true);
        InvocationTargetException ex = assertThrows(InvocationTargetException.class, ctor::newInstance);
        assertInstanceOf(UnsupportedOperationException.class, ex.getCause());
    }
}
