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

package org.scilab.modules.graphic_objects.imageplot;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import org.scilab.modules.graphic_objects.graphicObject.GraphicObject.UpdateStatus;
import org.scilab.modules.graphic_objects.graphicObject.Visitor;

import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.*;

/**
 * Hermetic unit tests for the abstract {@link Imageplot} class, exercised via a
 * minimal concrete stub. Covers the scale/translate defaults, their setters, the
 * read-copy semantics, and the property-name lookup.
 */
public class ImageplotTest {

    /** Minimal concrete Imageplot used only to instantiate the abstract class. */
    private static final class ImageplotStub extends Imageplot {
        @Override
        public void accept(Visitor visitor) {
            // no-op
        }
        @Override
        public Integer getType() {
            return -1;
        }
    }

    private static Imageplot newImageplot() {
        return new ImageplotStub();
    }

    @Test
    public void constructorDefaults() {
        Imageplot i = newImageplot();
        assertArrayEquals(new Double[] {1.0, 1.0}, i.getScale());
        assertArrayEquals(new Double[] {0.5, 0.5}, i.getTranslate());
    }

    @Test
    public void scaleRoundTrips() {
        Imageplot i = newImageplot();
        assertEquals(UpdateStatus.Success, i.setScale(new Double[] {2.0, 3.0}));
        assertArrayEquals(new Double[] {2.0, 3.0}, i.getScale());
    }

    @Test
    public void translateRoundTrips() {
        Imageplot i = newImageplot();
        assertEquals(UpdateStatus.Success, i.setTranslate(new Double[] {-4.0, 7.0}));
        assertArrayEquals(new Double[] {-4.0, 7.0}, i.getTranslate());
    }

    @Test
    public void getScaleReturnsFreshCopyEachCall() {
        Imageplot i = newImageplot();
        Double[] a = i.getScale();
        Double[] b = i.getScale();
        assertNotSame(a, b);
        a[0] = 42.0;
        assertEquals(1.0, i.getScale()[0], 0.0);
    }

    @Test
    public void getTranslateReturnsFreshCopyEachCall() {
        Imageplot i = newImageplot();
        Double[] a = i.getTranslate();
        Double[] b = i.getTranslate();
        assertNotSame(a, b);
        a[1] = 42.0;
        assertEquals(0.5, i.getTranslate()[1], 0.0);
    }

    @Test
    public void propertyNameLookupRoundTrips() {
        Imageplot i = newImageplot();

        Object scale = i.getPropertyFromName(__GO_MATPLOT_SCALE__);
        assertNotNull(scale);
        assertEquals(UpdateStatus.Success, i.setProperty(scale, new Double[] {5.0, 6.0}));
        assertArrayEquals(new Double[] {5.0, 6.0}, (Double[]) i.getProperty(scale));

        Object translate = i.getPropertyFromName(__GO_MATPLOT_TRANSLATE__);
        assertNotNull(translate);
        assertEquals(UpdateStatus.Success, i.setProperty(translate, new Double[] {8.0, 9.0}));
        assertArrayEquals(new Double[] {8.0, 9.0}, (Double[]) i.getProperty(translate));
    }

    @Test
    public void unknownPropertyDelegatesToSuperClass() {
        Imageplot i = newImageplot();
        Object visible = i.getPropertyFromName(__GO_VISIBLE__);
        assertNotNull(visible);
        assertEquals(Boolean.TRUE, i.getProperty(visible));
    }
}
