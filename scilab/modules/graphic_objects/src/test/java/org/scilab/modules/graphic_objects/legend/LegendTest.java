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

package org.scilab.modules.graphic_objects.legend;

import java.util.ArrayList;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import org.scilab.modules.graphic_objects.legend.Legend.LegendLocation;
import org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties;
import org.scilab.modules.graphic_objects.graphicObject.GraphicObject.UpdateStatus;

/**
 * Hermetic unit tests for {@link Legend}. Only the controller-free surface is
 * exercised: the "valid links" lookups consult the native GraphicController, but
 * only when the link list is non-empty, so they are tested on an empty legend.
 */
public class LegendTest {

    private static final double EPS = 1e-12;

    @Test
    public void constructorDefaults() {
        Legend l = new Legend();
        assertEquals(LegendLocation.LOWER_CAPTION, l.getLegendLocationAsEnum());
        assertEquals(Integer.valueOf(9), l.getLegendLocation()); // LOWER_CAPTION ordinal
        assertArrayEquals(new Double[] {0.0, 0.0}, l.getPosition());
        assertArrayEquals(new Double[] {0.0, 0.0}, l.getSize());
        assertEquals(Integer.valueOf(3), l.getMarksCount());
        assertEquals(0.1, l.getLineWidth(), EPS);
        assertEquals(Integer.valueOf(0), l.getLinksCount());
        assertEquals(0, l.getLinks().length);
    }

    @Test
    public void typeIsLegend() {
        assertEquals(GraphicObjectProperties.__GO_LEGEND__, new Legend().getType());
    }

    @Test
    public void legendLocationRoundTrips() {
        Legend l = new Legend();
        assertEquals(UpdateStatus.Success, l.setLegendLocation(0));
        assertEquals(LegendLocation.IN_UPPER_RIGHT, l.getLegendLocationAsEnum());
        assertEquals(Integer.valueOf(0), l.getLegendLocation());

        assertEquals(UpdateStatus.Success, l.setLegendLocationAsEnum(LegendLocation.BY_COORDINATES));
        assertEquals(Integer.valueOf(10), l.getLegendLocation());
    }

    @Test
    public void legendLocationIntToEnumBounds() {
        assertEquals(LegendLocation.IN_UPPER_RIGHT, LegendLocation.intToEnum(0));
        assertEquals(LegendLocation.LOWER_CAPTION, LegendLocation.intToEnum(9));
        assertEquals(LegendLocation.BY_COORDINATES, LegendLocation.intToEnum(10));
        assertNull(LegendLocation.intToEnum(11));
        assertNull(LegendLocation.intToEnum(-1));
    }

    @Test
    public void setLinksReplacesPreviousLinks() {
        Legend l = new Legend();
        assertEquals(UpdateStatus.Success, l.setLinks(new Integer[] {10, 20, 30}));
        assertArrayEquals(new Integer[] {10, 20, 30}, l.getLinks());
        assertEquals(Integer.valueOf(3), l.getLinksCount());

        // A subsequent set clears the previous list.
        l.setLinks(new Integer[] {99});
        assertArrayEquals(new Integer[] {99}, l.getLinks());
        assertEquals(Integer.valueOf(1), l.getLinksCount());
    }

    @Test
    public void setLinksFromArrayListVariant() {
        Legend l = new Legend();
        l.setLinks(new ArrayList<Integer>(Arrays.asList(1, 2)));
        assertArrayEquals(new Integer[] {1, 2}, l.getLinks());
    }

    @Test
    public void validLookupsAreEmptyForADefaultLegend() {
        // With no links, the valid* helpers never call the GraphicController, so
        // they are hermetic and must report an empty legend.
        Legend l = new Legend();
        assertEquals(0, l.getValidLinks().length);
        assertEquals(Integer.valueOf(0), l.getValidLinksCount());
        assertEquals(0, l.getValidTextStrings().length);
        assertArrayEquals(new Integer[] {0, 1}, l.getValidTextArrayDimensions());
    }

    @Test
    public void marksCountTracksChange() {
        Legend l = new Legend();
        assertEquals(UpdateStatus.NoChange, l.setMarksCount(3));
        assertEquals(UpdateStatus.Success, l.setMarksCount(5));
        assertEquals(Integer.valueOf(5), l.getMarksCount());
        assertEquals(UpdateStatus.NoChange, l.setMarksCount(5));
    }

    @Test
    public void lineWidthTracksChange() {
        Legend l = new Legend();
        assertEquals(UpdateStatus.NoChange, l.setLineWidth(0.1));
        assertEquals(UpdateStatus.Success, l.setLineWidth(0.25));
        assertEquals(0.25, l.getLineWidth(), EPS);
        assertEquals(UpdateStatus.NoChange, l.setLineWidth(0.25));
    }

    @Test
    public void positionAndSizeRoundTrip() {
        Legend l = new Legend();
        assertEquals(UpdateStatus.Success, l.setPosition(new Double[] {0.3, 0.7}));
        assertArrayEquals(new Double[] {0.3, 0.7}, l.getPosition());
        assertEquals(UpdateStatus.Success, l.setSize(new Double[] {0.4, 0.2}));
        assertArrayEquals(new Double[] {0.4, 0.2}, l.getSize());
    }

    @Test
    public void propertyDispatchRoundTripsForPureProperties() {
        Legend l = new Legend();
        // LegendProperty is private; reach the dispatch via getPropertyFromName.
        Object locKey = l.getPropertyFromName(GraphicObjectProperties.__GO_LEGEND_LOCATION__);
        assertEquals(UpdateStatus.Success, l.setProperty(locKey, Integer.valueOf(4)));
        assertEquals(Integer.valueOf(4), l.getProperty(locKey));

        Object mcKey = l.getPropertyFromName(GraphicObjectProperties.__GO_MARKS_COUNT__);
        l.setProperty(mcKey, Integer.valueOf(6));
        assertEquals(Integer.valueOf(6), l.getProperty(mcKey));

        Object lwKey = l.getPropertyFromName(GraphicObjectProperties.__GO_LINE_WIDTH__);
        l.setProperty(lwKey, Double.valueOf(0.5));
        assertEquals(0.5, (Double) l.getProperty(lwKey), EPS);
    }
}
