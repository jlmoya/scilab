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

package org.scilab.modules.graph.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.scilab.modules.types.ScilabDouble;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import com.mxgraph.io.mxCodec;

/**
 * Hermetic unit tests for {@link ScilabDoubleCodec}. A {@code <data>} child
 * carries a mandatory {@code realPart} and an optional {@code imaginaryPart};
 * the presence of any imaginary part flips the decoded matrix to complex.
 */
public class ScilabDoubleCodecTest {

    private static ScilabDoubleCodec codec() {
        return new ScilabDoubleCodec(new ScilabDouble(), null, null, null);
    }

    private static Document newDocument() throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
    }

    private static Element realCell(Document doc, int line, int column, double real) {
        Element d = doc.createElement("data");
        d.setAttribute("line", Integer.toString(line));
        d.setAttribute("column", Integer.toString(column));
        d.setAttribute("realPart", Double.toString(real));
        return d;
    }

    @Test
    public void isAScilabObjectCodec() {
        assertTrue(codec() instanceof ScilabObjectCodec);
    }

    @Test
    public void decodeRealMatrix() throws Exception {
        Document doc = newDocument();
        Element node = doc.createElement("ScilabDouble");
        node.setAttribute("height", "1");
        node.setAttribute("width", "2");
        node.appendChild(realCell(doc, 0, 0, 1.5));
        node.appendChild(realCell(doc, 0, 1, -2.25));

        ScilabDouble decoded = (ScilabDouble) codec().decode(new mxCodec(), node, null);

        assertNotNull(decoded);
        assertTrue(decoded.isReal());
        assertEquals(1.5, decoded.getRealElement(0, 0), 0.0);
        assertEquals(-2.25, decoded.getRealElement(0, 1), 0.0);
    }

    @Test
    public void decodeComplexMatrixWhenImaginaryPartPresent() throws Exception {
        Document doc = newDocument();
        Element node = doc.createElement("ScilabDouble");
        node.setAttribute("height", "1");
        node.setAttribute("width", "1");
        Element cell = realCell(doc, 0, 0, 3.0);
        cell.setAttribute("imaginaryPart", "4.0");
        node.appendChild(cell);

        ScilabDouble decoded = (ScilabDouble) codec().decode(new mxCodec(), node, null);

        assertFalse(decoded.isReal());
        assertEquals(3.0, decoded.getRealElement(0, 0), 0.0);
        assertEquals(4.0, decoded.getImaginaryElement(0, 0), 0.0);
    }

    @Test
    public void encodeThenDecodeRealRoundTrips() throws Exception {
        ScilabDouble original = new ScilabDouble(new double[][] {{1.0, 2.0}, {3.0, 4.0}});

        mxCodec enc = new mxCodec();
        Node node = codec().encode(enc, original);
        assertEquals("2", ((Element) node).getAttribute("width"));
        assertEquals("2", ((Element) node).getAttribute("height"));

        ScilabDouble decoded = (ScilabDouble) codec().decode(enc, node, null);
        assertTrue(decoded.isReal());
        assertEquals(original, decoded);
    }

    @Test
    public void encodeThenDecodeComplexRoundTrips() throws Exception {
        ScilabDouble original = new ScilabDouble(new double[][] {{1.0, 2.0}},
                                                 new double[][] {{-1.0, 5.0}});

        mxCodec enc = new mxCodec();
        Node node = codec().encode(enc, original);

        ScilabDouble decoded = (ScilabDouble) codec().decode(enc, node, null);
        assertFalse(decoded.isReal());
        assertEquals(original, decoded);
    }

    @Test
    public void missingRealPartIsCaughtAndTemplateReturned() throws Exception {
        // realPart is mandatory: its absence raises UnrecognizeFormatException,
        // which decode catches before setRealPart, returning the empty template.
        Document doc = newDocument();
        Element node = doc.createElement("ScilabDouble");
        node.setAttribute("height", "1");
        node.setAttribute("width", "1");
        Element d = doc.createElement("data");
        d.setAttribute("line", "0");
        d.setAttribute("column", "0");
        node.appendChild(d);

        ScilabDouble decoded = (ScilabDouble) codec().decode(new mxCodec(), node, null);
        assertNotNull(decoded);
        assertEquals(0, decoded.getHeight() * decoded.getWidth());
    }
}
