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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

/**
 * Hermetic unit tests for {@link MathMLRenderUtils}.
 *
 * The malformed-XML path is fully display-free: {@code getMathMLComponent}
 * escapes the {@code ^...^} tags, hands the remainder to a JAXP
 * {@link javax.xml.parsers.DocumentBuilder}, and the SAX parser throws before
 * any Swing {@code JMathComponent} is constructed. The successful render path
 * (which does build a Swing component) is only exercised behind a non-headless
 * assumption.
 */
public class MathMLRenderUtilsTest {

    @Test
    public void getMathMLComponentThrowsSAXExceptionOnMalformedMarkup() {
        // MathML.escape strips the surrounding '^' tags leaving "<a>", which is
        // not well-formed XML => the DocumentBuilder throws a SAXException
        // (declared on the method) before a JMathComponent is ever built.
        assertThrows(SAXException.class, () -> MathMLRenderUtils.getMathMLComponent("^<a>^"));
    }

    @Test
    public void getMathMLComponentThrowsOnMismatchedTags() {
        assertThrows(SAXException.class, () -> MathMLRenderUtils.getMathMLComponent("^<a></b>^"));
    }

    @Test
    public void constructorIsPrivateAndInvocable() throws Exception {
        Constructor<MathMLRenderUtils> ctor = MathMLRenderUtils.class.getDeclaredConstructor();
        assertTrue(Modifier.isPrivate(ctor.getModifiers()));
        ctor.setAccessible(true);
        assertNotNull(ctor.newInstance());
    }

    @Test
    public void getMathMLComponentBuildsAndCachesComponentWhenADisplayIsAvailable() throws SAXException {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());

        final String text = "^<math><mi>x</mi></math>^";
        Component first = MathMLRenderUtils.getMathMLComponent(text);
        assertNotNull(first);

        // The cache is keyed on the raw text, so a repeat call returns the same
        // component instance.
        Component second = MathMLRenderUtils.getMathMLComponent(text);
        assertSame(first, second);
    }
}
