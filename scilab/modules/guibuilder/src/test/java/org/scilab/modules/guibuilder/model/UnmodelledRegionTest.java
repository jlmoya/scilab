/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 *
 */

package org.scilab.modules.guibuilder.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

public class UnmodelledRegionTest {

    private static SourceRange anywhere() {
        return new SourceRange(0, 10);
    }

    @Test
    public void storesTheRangeAndReasonItWasGiven() {
        SourceRange range = anywhere();
        UnmodelledRegion region = new UnmodelledRegion(range, "loop creates controls");
        assertSame(range, region.range());
        assertEquals("loop creates controls", region.reason());
    }

    @Test
    public void aNullReasonIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new UnmodelledRegion(anywhere(), null));
    }

    @Test
    public void anEmptyReasonIsRejected() {
        // The reason is shown to the user verbatim; a region with none would
        // be useless in the UI, so an empty reason is rejected just like a
        // missing one.
        assertThrows(IllegalArgumentException.class, () -> new UnmodelledRegion(anywhere(), ""));
    }

    @Test
    public void aNullRangeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new UnmodelledRegion(null, "unrecognised call"));
    }
}
