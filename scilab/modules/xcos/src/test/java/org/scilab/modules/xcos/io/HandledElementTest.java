/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Scilab
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 *
 */

package org.scilab.modules.xcos.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for the {@link HandledElement} enum and its
 * {@link HandledElement#getMap()} / {@link HandledElement#getCategory()}
 * behaviour. A plain Java enum: no native runtime is touched.
 */
public class HandledElementTest {

    /**
     * The category each constant is expected to belong to. This mirrors the
     * source declaration exactly; {@code default} throws so that adding a new
     * constant without updating this test fails loudly rather than silently
     * mis-classifying it (a defect-characterization guard).
     */
    private static HandledElementsCategory expectedCategory(HandledElement e) {
        switch (e) {
            // RAW_DATA (7)
            case add:
            case Array:
            case data:
            case ScilabBoolean:
            case ScilabDouble:
            case ScilabInteger:
            case ScilabString:
                return HandledElementsCategory.RAW_DATA;
            // LINK (3)
            case CommandControlLink:
            case ExplicitLink:
            case ImplicitLink:
                return HandledElementsCategory.LINK;
            // PORT (6)
            case CommandPort:
            case ControlPort:
            case ExplicitInputPort:
            case ExplicitOutputPort:
            case ImplicitInputPort:
            case ImplicitOutputPort:
                return HandledElementsCategory.PORT;
            // JGRAPHX (3)
            case mxCell:
            case mxGeometry:
            case mxPoint:
                return HandledElementsCategory.JGRAPHX;
            // CUSTOM (3)
            case Orientation:
            case SuperBlockDiagram:
            case XcosDiagram:
                return HandledElementsCategory.CUSTOM;
            // BLOCK (20)
            case AfficheBlock:
            case BasicBlock:
            case BigSom:
            case ConstBlock:
            case EventInBlock:
            case EventOutBlock:
            case ExplicitInBlock:
            case ExplicitOutBlock:
            case GainBlock:
            case GroundBlock:
            case ImplicitInBlock:
            case ImplicitOutBlock:
            case PrintBlock:
            case Product:
            case RoundBlock:
            case SplitBlock:
            case Summation:
            case SuperBlock:
            case TextBlock:
            case VoltageSensorBlock:
                return HandledElementsCategory.BLOCK;
            default:
                throw new AssertionError("unmapped HandledElement: " + e
                                         + " — update HandledElementTest.expectedCategory");
        }
    }

    @Test
    public void hasExactlyFortyTwoConstants() {
        // Characterizes the current catalog size; a diff here flags an add/remove.
        assertEquals(42, HandledElement.values().length);
    }

    @Test
    public void everyConstantHasANonNullCategory() {
        for (HandledElement e : HandledElement.values()) {
            assertNotNull(e.getCategory(), "null category for " + e);
        }
    }

    @Test
    public void everyConstantMapsToItsDeclaredCategory() {
        for (HandledElement e : HandledElement.values()) {
            assertSame(expectedCategory(e), e.getCategory(),
                       "wrong category for " + e);
        }
    }

    @Test
    public void getCategoryIsStableAcrossCalls() {
        for (HandledElement e : HandledElement.values()) {
            assertSame(e.getCategory(), e.getCategory());
        }
    }

    @Test
    public void categoryHistogramMatchesExpectedCounts() {
        Map<HandledElementsCategory, Integer> counts = new EnumMap<>(HandledElementsCategory.class);
        for (HandledElement e : HandledElement.values()) {
            counts.merge(e.getCategory(), 1, Integer::sum);
        }
        assertEquals(Integer.valueOf(7), counts.get(HandledElementsCategory.RAW_DATA));
        assertEquals(Integer.valueOf(20), counts.get(HandledElementsCategory.BLOCK));
        assertEquals(Integer.valueOf(3), counts.get(HandledElementsCategory.LINK));
        assertEquals(Integer.valueOf(6), counts.get(HandledElementsCategory.PORT));
        assertEquals(Integer.valueOf(3), counts.get(HandledElementsCategory.JGRAPHX));
        assertEquals(Integer.valueOf(3), counts.get(HandledElementsCategory.CUSTOM));
        // All six categories are actually used.
        assertEquals(HandledElementsCategory.values().length, counts.size());
    }

    @Test
    public void countsSumToTheNumberOfConstants() {
        int sum = 0;
        Map<HandledElementsCategory, Integer> counts = new EnumMap<>(HandledElementsCategory.class);
        for (HandledElement e : HandledElement.values()) {
            counts.merge(e.getCategory(), 1, Integer::sum);
        }
        for (int c : counts.values()) {
            sum += c;
        }
        assertEquals(HandledElement.values().length, sum);
    }

    // ---- getMap() ---------------------------------------------------------

    @Test
    public void getMapHasOneEntryPerConstant() {
        Map<String, HandledElement> map = HandledElement.getMap();
        assertEquals(HandledElement.values().length, map.size());
    }

    @Test
    public void getMapKeyIsTheConstantNameAndValueIsTheConstant() {
        Map<String, HandledElement> map = HandledElement.getMap();
        for (HandledElement e : HandledElement.values()) {
            assertSame(e, map.get(e.name()),
                       "getMap() must key each constant under its name(): " + e);
        }
    }

    @Test
    public void getMapContainsExactlyTheConstantNames() {
        Map<String, HandledElement> map = HandledElement.getMap();
        for (HandledElement e : HandledElement.values()) {
            assertTrue(map.containsKey(e.name()), "missing key " + e.name());
        }
        // Spot-check a lower-case-named constant is keyed verbatim (names mirror localName).
        assertSame(HandledElement.add, map.get("add"));
        assertSame(HandledElement.data, map.get("data"));
        assertSame(HandledElement.mxCell, map.get("mxCell"));
    }

    @Test
    public void getMapMissAndNullKeyReturnNull() {
        Map<String, HandledElement> map = HandledElement.getMap();
        assertNull(map.get("NoSuchElement"));
        assertNull(map.get(null));
    }

    @Test
    public void getMapIsUnmodifiable() {
        Map<String, HandledElement> map = HandledElement.getMap();
        assertThrows(UnsupportedOperationException.class,
                     () -> map.put("x", HandledElement.add));
        assertThrows(UnsupportedOperationException.class,
                     () -> map.remove("add"));
        assertThrows(UnsupportedOperationException.class, map::clear);
    }

    @Test
    public void getMapReturnsAFreshInstanceButEqualContentEachCall() {
        // Documents current behaviour: getMap() rebuilds a new unmodifiable map every call.
        Map<String, HandledElement> a = HandledElement.getMap();
        Map<String, HandledElement> b = HandledElement.getMap();
        assertTrue(a != b, "getMap() is expected to allocate a new map per call");
        assertEquals(a, b, "successive maps must have equal content");
        // And they agree with an independently built reference map.
        Map<String, HandledElement> reference = new HashMap<>();
        for (HandledElement e : HandledElement.values()) {
            reference.put(e.name(), e);
        }
        assertEquals(reference, a);
    }

    // ---- enum identity basics --------------------------------------------

    @Test
    public void valueOfRoundTripsForEveryConstant() {
        for (HandledElement e : HandledElement.values()) {
            assertSame(e, HandledElement.valueOf(e.name()));
        }
    }

    @Test
    public void valueOfUnknownThrows() {
        assertThrows(IllegalArgumentException.class,
                     () -> HandledElement.valueOf("DefinitelyNotAnElement"));
    }
}
