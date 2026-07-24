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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Container;
import java.awt.Dimension;
import java.awt.Point;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link PositionConverter}, the y-axis flip between the
 * bottom-left origin used by Scilab and the top-left origin used by Swing.
 *
 * <p>The only thing the converter reads from the parent is its pixel height, so
 * the tests use a bare lightweight {@link Container} sized via {@code setSize};
 * that creates no native peer and needs no display, keeping the tests headless
 * and free of the native runtime.</p>
 */
class PositionConverterTest {

    /**
     * A lightweight AWT container whose reported height is fixed via
     * {@code setSize}. {@code getHeight()} is the sole property
     * {@link PositionConverter} consults; the width is arbitrary and unused.
     */
    private static Container parentWithHeight(int height) {
        Container parent = new Container();
        parent.setSize(1000, height);
        return parent;
    }

    // ---- scilabToJava: null parent short-circuit ------------------------

    /**
     * With no parent yet, the Scilab position is returned unchanged — and it is
     * the very same instance, not a copy.
     */
    @Test
    void scilabToJavaWithNullParentReturnsSameInstance() {
        Position scilab = new Position(10, 20);
        Position result = PositionConverter.scilabToJava(scilab, new Size(5, 5), null);
        assertSame(scilab, result);
    }

    /**
     * The null-parent branch returns before the object size is read, so a null
     * size is tolerated in that case.
     */
    @Test
    void scilabToJavaWithNullParentDoesNotDereferenceSize() {
        Position scilab = new Position(3, 4);
        assertSame(scilab, PositionConverter.scilabToJava(scilab, null, null));
    }

    // ---- scilabToJava: the y-flip ---------------------------------------

    /**
     * With a parent, x is passed through and y is flipped as
     * {@code parentHeight - y - objectHeight}.
     */
    @Test
    void scilabToJavaFlipsYAgainstParentHeight() {
        Position result = PositionConverter.scilabToJava(new Position(10, 50), new Size(40, 20), parentWithHeight(300));
        assertEquals(10, result.getX());
        assertEquals(230, result.getY()); // 300 - 50 - 20
    }

    /**
     * The object width never enters the computation — only its height does.
     */
    @Test
    void scilabToJavaIgnoresObjectWidth() {
        Position narrow = PositionConverter.scilabToJava(new Position(7, 5), new Size(1, 12), parentWithHeight(100));
        Position wide = PositionConverter.scilabToJava(new Position(7, 5), new Size(999, 12), parentWithHeight(100));
        assertEquals(narrow.getX(), wide.getX());
        assertEquals(narrow.getY(), wide.getY());
    }

    /**
     * Characterization: the flip is unclamped, so an object taller than / below
     * the parent yields a negative Java y-coordinate.
     */
    @Test
    void scilabToJavaProducesNegativeYWhenObjectExceedsParent() {
        Position result = PositionConverter.scilabToJava(new Position(0, 20), new Size(10, 40), parentWithHeight(30));
        assertEquals(-30, result.getY()); // 30 - 20 - 40
    }

    /**
     * With a parent set, a fresh Position is produced rather than the input.
     */
    @Test
    void scilabToJavaWithParentReturnsNewInstance() {
        Position scilab = new Position(10, 50);
        Position result = PositionConverter.scilabToJava(scilab, new Size(40, 20), parentWithHeight(300));
        assertNotSame(scilab, result);
    }

    @Test
    void scilabToJavaWithParentButNullPositionThrowsNpe() {
        assertThrows(NullPointerException.class,
                     () -> PositionConverter.scilabToJava(null, new Size(1, 1), parentWithHeight(10)));
    }

    @Test
    void scilabToJavaWithParentButNullSizeThrowsNpe() {
        assertThrows(NullPointerException.class,
                     () -> PositionConverter.scilabToJava(new Position(1, 1), null, parentWithHeight(10)));
    }

    // ---- javaToScilab: null parent short-circuit ------------------------

    /**
     * With no parent, the Java point's coordinates are copied verbatim into a
     * new Position (no flip).
     */
    @Test
    void javaToScilabWithNullParentCopiesPointCoordinates() {
        Position result = PositionConverter.javaToScilab(new Point(10, 20), new Dimension(5, 5), null);
        assertEquals(10, result.getX());
        assertEquals(20, result.getY());
    }

    /**
     * The null-parent branch returns before the object size is read, so a null
     * size is tolerated in that case.
     */
    @Test
    void javaToScilabWithNullParentDoesNotDereferenceSize() {
        Position result = PositionConverter.javaToScilab(new Point(3, 4), null, null);
        assertEquals(3, result.getX());
        assertEquals(4, result.getY());
    }

    // ---- javaToScilab: the y-flip ---------------------------------------

    /**
     * With a parent, x is passed through and y is flipped as
     * {@code parentHeight - y - objectHeight}.
     */
    @Test
    void javaToScilabFlipsYAgainstParentHeight() {
        Position result = PositionConverter.javaToScilab(new Point(10, 50), new Dimension(40, 20), parentWithHeight(300));
        assertEquals(10, result.getX());
        assertEquals(230, result.getY()); // 300 - 50 - 20
    }

    /**
     * Characterization: the flip is unclamped, so a point below the object's
     * fit yields a negative Scilab y-coordinate.
     */
    @Test
    void javaToScilabProducesNegativeYWhenObjectExceedsParent() {
        Position result = PositionConverter.javaToScilab(new Point(0, 20), new Dimension(10, 40), parentWithHeight(30));
        assertEquals(-30, result.getY()); // 30 - 20 - 40
    }

    /**
     * Characterization: the null-parent guard reads {@code javaPosition.x}, so a
     * null point throws even when the parent is also null.
     */
    @Test
    void javaToScilabWithNullPointAndNullParentThrowsNpe() {
        assertThrows(NullPointerException.class,
                     () -> PositionConverter.javaToScilab(null, new Dimension(1, 1), null));
    }

    @Test
    void javaToScilabWithParentButNullPointThrowsNpe() {
        assertThrows(NullPointerException.class,
                     () -> PositionConverter.javaToScilab(null, new Dimension(1, 1), parentWithHeight(10)));
    }

    @Test
    void javaToScilabWithParentButNullSizeThrowsNpe() {
        assertThrows(NullPointerException.class,
                     () -> PositionConverter.javaToScilab(new Point(1, 1), null, parentWithHeight(10)));
    }

    // ---- Round-trip property --------------------------------------------

    /**
     * The two conversions are inverses under a fixed parent height and object
     * size: {@code javaToScilab(scilabToJava(p)) == p}. This pins the shared
     * {@code H - y - h} formula from both directions at once.
     */
    @Test
    void scilabToJavaThenJavaToScilabRecoversOriginal() {
        Container parent = parentWithHeight(300);
        Position original = new Position(37, 88);
        Size size = new Size(40, 25);

        Position java = PositionConverter.scilabToJava(original, size, parent);
        Position back = PositionConverter.javaToScilab(
                            new Point(java.getX(), java.getY()),
                            new Dimension(size.getWidth(), size.getHeight()),
                            parent);

        assertEquals(original.getX(), back.getX());
        assertEquals(original.getY(), back.getY());
    }

    // ---- Utility class contract -----------------------------------------

    /**
     * {@code PositionConverter} is a static utility class: its sole constructor
     * is private and rejects reflective instantiation with
     * {@link UnsupportedOperationException}.
     */
    @Test
    void constructorIsPrivateAndThrows() throws Exception {
        Constructor<PositionConverter> ctor = PositionConverter.class.getDeclaredConstructor();
        assertTrue(Modifier.isPrivate(ctor.getModifiers()));
        ctor.setAccessible(true);
        InvocationTargetException ex = assertThrows(InvocationTargetException.class, ctor::newInstance);
        assertTrue(ex.getCause() instanceof UnsupportedOperationException);
    }
}
