/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Scilab
 *
 * Hermetic JUnit 6 unit tests for the gui module.
 * This file is hereby licensed under the terms of the GNU GPL v2.0.
 */

package org.scilab.modules.gui.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import org.junit.jupiter.api.Test;

/**
 * Tests {@link Position}, a mutable (x, y) integer coordinate holder used across the
 * Scilab GUIs. Pure data class: construction and the accessors involve no native code.
 */
public class PositionTest {

    @Test
    public void constructorStoresCoordinatesInOrder() {
        // Guards against an x/y field swap: the first argument is X, the second is Y.
        Position p = new Position(3, 7);
        assertEquals(3, p.getX());
        assertEquals(7, p.getY());
    }

    @Test
    public void setXChangesOnlyX() {
        Position p = new Position(3, 7);
        p.setX(42);
        assertEquals(42, p.getX());
        assertEquals(7, p.getY());
    }

    @Test
    public void setYChangesOnlyY() {
        Position p = new Position(3, 7);
        p.setY(42);
        assertEquals(3, p.getX());
        assertEquals(42, p.getY());
    }

    @Test
    public void negativeCoordinatesArePreserved() {
        Position p = new Position(-10, -20);
        assertEquals(-10, p.getX());
        assertEquals(-20, p.getY());
    }

    @Test
    public void integerBoundaryValuesArePreserved() {
        Position p = new Position(Integer.MIN_VALUE, Integer.MAX_VALUE);
        assertEquals(Integer.MIN_VALUE, p.getX());
        assertEquals(Integer.MAX_VALUE, p.getY());
    }

    @Test
    public void zeroIsAValidCoordinate() {
        Position p = new Position(0, 0);
        assertEquals(0, p.getX());
        assertEquals(0, p.getY());
    }

    /**
     * Defect-characterization: {@link Position} overrides neither {@code equals} nor
     * {@code hashCode}, so two positions with identical coordinates are NOT value-equal;
     * only reference identity holds. This documents current behavior.
     */
    @Test
    public void equalsIsReferenceIdentityOnly() {
        Position a = new Position(5, 5);
        Position b = new Position(5, 5);
        assertNotSame(a, b);
        assertNotEquals(a, b);
        assertEquals(a, a);
    }
}
