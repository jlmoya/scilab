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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.scilab.modules.graph.shape.LatexTextShape;

import com.mxgraph.shape.mxITextShape;
import com.mxgraph.util.mxConstants;

/**
 * Hermetic unit tests for {@link ScilabCanvas}.
 *
 * Only the pieces that do not need a live {@link java.awt.Graphics2D} surface
 * are exercised: the public rotation constants, the {@code image base path ->
 * resolved image URL} logic ({@link ScilabCanvas#setImageBasePath} /
 * {@link ScilabCanvas#getImageForStyle}), and the label-type dispatch of
 * {@link ScilabCanvas#getTextShape} for plain text and LaTeX. The MathML branch
 * is deliberately avoided because detecting it triggers a classpath side load.
 *
 * Constructing a {@link ScilabCanvas} runs its static initialiser (registering
 * the SVG / LaTeX / MathML shapes) but touches no native code, so the whole
 * class stays hermetic under headless AWT.
 */
public class ScilabCanvasTest {

    private static Map<String, Object> styleWithImage(String image) {
        Map<String, Object> style = new HashMap<String, Object>();
        if (image != null) {
            style.put(mxConstants.STYLE_IMAGE, image);
        }
        return style;
    }

    @Test
    public void rotationConstantsHaveTheDocumentedValues() {
        assertEquals(90, ScilabCanvas.ROTATION_STEP);
        assertEquals(360, ScilabCanvas.MAX_ROTATION);
    }

    @Test
    public void getImageForStyleReturnsNullWhenNoImageIsSet() {
        ScilabCanvas canvas = new ScilabCanvas();
        assertNull(canvas.getImageForStyle(styleWithImage(null)));
    }

    @Test
    public void getImageForStyleReturnsAnAbsoluteImageUrlEvenWithoutABasePath() {
        // An absolute spec resolves against a null context URL just fine.
        ScilabCanvas canvas = new ScilabCanvas();
        String resolved = canvas.getImageForStyle(styleWithImage("http://example.com/a.png"));
        assertEquals("http://example.com/a.png", resolved);
    }

    @Test
    public void getImageForStyleReturnsNullForARelativeImageWithoutABasePath() {
        // A relative spec with a null base is a MalformedURLException -> null.
        ScilabCanvas canvas = new ScilabCanvas();
        assertNull(canvas.getImageForStyle(styleWithImage("icon.png")));
    }

    @Test
    public void getImageForStyleResolvesARelativeImageAgainstTheBasePath() {
        ScilabCanvas canvas = new ScilabCanvas();
        canvas.setImageBasePath("file:/base/dir/");
        String resolved = canvas.getImageForStyle(styleWithImage("icon.png"));
        assertEquals("file:/base/dir/icon.png", resolved);
    }

    @Test
    public void setImageBasePathSwallowsAMalformedPathAndLeavesRelativeLookupsUnresolved() {
        ScilabCanvas canvas = new ScilabCanvas();
        // "not a url" has no protocol: setImageBasePath logs and keeps the base
        // null, so a later relative lookup still yields null.
        canvas.setImageBasePath("not a url");
        assertNull(canvas.getImageForStyle(styleWithImage("icon.png")));
    }

    @Test
    public void getTextShapeReturnsANonNullDefaultShapeForPlainText() {
        ScilabCanvas canvas = new ScilabCanvas();
        mxITextShape shape = canvas.getTextShape("hello", new HashMap<String, Object>(), false);
        assertNotNull(shape);
    }

    @Test
    public void getTextShapeReturnsTheLatexShapeForDollarDelimitedText() {
        ScilabCanvas canvas = new ScilabCanvas();
        mxITextShape shape = canvas.getTextShape("$x$", new HashMap<String, Object>(), false);
        assertInstanceOf(LatexTextShape.class, shape);
    }

    @Test
    public void getTextShapeReusesTheSameRegisteredLatexShapeInstance() {
        // The text shapes are registered once in a static map, so repeated
        // lookups for the same valid LaTeX return the identical instance.
        ScilabCanvas canvas = new ScilabCanvas();
        mxITextShape first = canvas.getTextShape("$x$", new HashMap<String, Object>(), false);
        mxITextShape second = canvas.getTextShape("$y$", new HashMap<String, Object>(), false);
        assertSame(first, second);
    }
}
