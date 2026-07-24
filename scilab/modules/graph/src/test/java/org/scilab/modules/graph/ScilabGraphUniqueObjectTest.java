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

package org.scilab.modules.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.mxgraph.model.mxGeometry;

/**
 * Hermetic unit tests for {@link ScilabGraphUniqueObject}. The class is
 * abstract but adds no abstract methods, so a trivial concrete subclass is
 * enough. Its dependencies (mxCell, mxGeometry, java.rmi.server.UID,
 * Point2D) are all headless-safe.
 */
public class ScilabGraphUniqueObjectTest {

    /** Concrete stand-in for the abstract unique object. */
    private static final class TestNode extends ScilabGraphUniqueObject {
    }

    private static TestNode nodeAt(double x, double y) {
        TestNode node = new TestNode();
        // zero width/height => geometry centre is exactly (x, y)
        node.setGeometry(new mxGeometry(x, y, 0, 0));
        return node;
    }

    @Test
    public void constructorAssignsAnId() {
        assertNotNull(new TestNode().getId());
    }

    @Test
    public void distinctInstancesGetDistinctIds() {
        assertNotEquals(new TestNode().getId(), new TestNode().getId());
    }

    @Test
    public void generateIdReplacesTheCurrentId() {
        TestNode node = new TestNode();
        String first = node.getId();
        node.generateId();
        assertNotEquals(first, node.getId());
    }

    @Test
    public void compareToOfEqualCentresIsZero() {
        TestNode a = nodeAt(10, 20);
        assertEquals(0, a.compareTo(a));
        assertEquals(0, a.compareTo(nodeAt(10, 20)));
    }

    @Test
    public void compareToReturnsSquaredCentreDistance() {
        // centres (0,0) and (3,4) => 3^2 + 4^2 = 25
        TestNode origin = nodeAt(0, 0);
        TestNode far = nodeAt(3, 4);
        assertEquals(25, origin.compareTo(far));
    }

    @Test
    public void compareToIsSymmetricAndNonNegative_violatesComparableContract() {
        // Because it returns a squared distance, compareTo(a,b) == compareTo(b,a)
        // and is always >= 0: it can never express "less than", so it is not a
        // valid Comparable ordering. Documented, not asserted-as-correct.
        TestNode a = nodeAt(0, 0);
        TestNode b = nodeAt(3, 4);
        assertEquals(a.compareTo(b), b.compareTo(a));
        assertTrue(a.compareTo(b) >= 0);
        assertTrue(b.compareTo(a) >= 0);
    }

    @Test
    public void cloneRegeneratesTheId() throws Exception {
        TestNode original = nodeAt(1, 2);
        String originalId = original.getId();

        Object clone = original.clone();
        assertNotSame(original, clone);
        assertTrue(clone instanceof ScilabGraphUniqueObject);
        assertNotEquals(originalId, ((ScilabGraphUniqueObject) clone).getId());
        // the original is untouched
        assertEquals(originalId, original.getId());
    }
}
