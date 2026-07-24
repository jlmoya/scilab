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

package org.scilab.modules.graph.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.mxgraph.util.mxConstants;

/**
 * Hermetic unit tests pinning the public constant contract of
 * {@link ScilabGraphConstants}. These string/number keys are persisted in
 * saved diagrams and consumed by styles, so their literal values are part of
 * the module's on-disk contract.
 */
public class ScilabGraphConstantsTest {

    @Test
    public void extendsMxConstants() {
        assertTrue(mxConstants.class.isAssignableFrom(ScilabGraphConstants.class));
    }

    @Test
    public void lineArcSizeValue() {
        assertEquals(200.0, ScilabGraphConstants.LINE_ARCSIZE, 0.0);
    }

    @Test
    public void styleKeyValues() {
        assertEquals("centerArrow", ScilabGraphConstants.STYLE_CENTERARROW);
        assertEquals("centerSize", ScilabGraphConstants.STYLE_CENTERSIZE);
        assertEquals("flip", ScilabGraphConstants.STYLE_FLIP);
        assertEquals("mirror", ScilabGraphConstants.STYLE_MIRROR);
    }

    @Test
    public void shapeAndArrowValues() {
        assertEquals("spline", ScilabGraphConstants.SHAPE_SPLINE);
        assertEquals("center", ScilabGraphConstants.ARROW_POSITION_CENTER);
    }

    @Test
    public void htmlMarkupConstants() {
        assertEquals("<html>", ScilabGraphConstants.HTML_BEGIN);
        assertEquals("</html>", ScilabGraphConstants.HTML_END);
        assertEquals("<br>", ScilabGraphConstants.HTML_NEWLINE);
        assertEquals("<code>", ScilabGraphConstants.HTML_BEGIN_CODE);
        assertEquals("</code>", ScilabGraphConstants.HTML_END_CODE);
    }

    @Test
    public void eventConstant() {
        assertEquals("edit", ScilabGraphConstants.EVENT_CHANGE_EDIT);
    }

    @Test
    public void protectedConstructorIsInvokableFromSamePackage() {
        // The class documents itself as a static singleton; the protected
        // no-op constructor still succeeds when reached from the package.
        assertNotNull(new ScilabGraphConstants());
    }
}
