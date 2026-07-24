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
import org.scilab.modules.types.ScilabBoolean;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import com.mxgraph.io.mxCodec;

/**
 * Hermetic unit tests for {@link ScilabBooleanCodec}. The codec (de)serializes
 * a {@link ScilabBoolean} matrix; decode uses {@code Boolean.parseBoolean} per
 * cell so any non-"true" token reads as {@code false}.
 */
public class ScilabBooleanCodecTest {

    private static ScilabBooleanCodec codec() {
        return new ScilabBooleanCodec(new ScilabBoolean(), null, null, null);
    }

    private static Document newDocument() throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
    }

    private static Element matrixNode(Document doc, boolean[][] data) {
        Element root = doc.createElement("ScilabBoolean");
        root.setAttribute("height", Integer.toString(data.length));
        root.setAttribute("width", Integer.toString(data.length == 0 ? 0 : data[0].length));
        for (int i = 0; i < data.length; i++) {
            for (int j = 0; j < data[i].length; j++) {
                Element d = doc.createElement("data");
                d.setAttribute("line", Integer.toString(i));
                d.setAttribute("column", Integer.toString(j));
                d.setAttribute("value", Boolean.toString(data[i][j]));
                root.appendChild(d);
            }
        }
        return root;
    }

    @Test
    public void isAScilabObjectCodec() {
        assertTrue(codec() instanceof ScilabObjectCodec);
    }

    @Test
    public void decodeReadsBooleanMatrix() throws Exception {
        Document doc = newDocument();
        Element node = matrixNode(doc, new boolean[][] {{true, false}, {false, true}});

        ScilabBoolean decoded = (ScilabBoolean) codec().decode(new mxCodec(), node, null);

        assertNotNull(decoded);
        assertEquals(2, decoded.getHeight());
        assertEquals(2, decoded.getWidth());
        assertTrue(decoded.getElement(0, 0));
        assertFalse(decoded.getElement(0, 1));
        assertFalse(decoded.getElement(1, 0));
        assertTrue(decoded.getElement(1, 1));
    }

    @Test
    public void encodeThenDecodeRoundTrips() throws Exception {
        ScilabBoolean original = new ScilabBoolean(new boolean[][] {{true, false, true}});

        mxCodec enc = new mxCodec();
        Node node = codec().encode(enc, original);
        assertEquals("3", ((Element) node).getAttribute("width"));
        assertEquals("1", ((Element) node).getAttribute("height"));

        ScilabBoolean decoded = (ScilabBoolean) codec().decode(enc, node, null);
        assertEquals(original, decoded);
    }

    @Test
    public void unparsableValueIsReadAsFalse() throws Exception {
        // Boolean.parseBoolean treats any non-"true" token as false; this is the
        // decode contract for a malformed value attribute (no exception thrown).
        Document doc = newDocument();
        Element node = doc.createElement("ScilabBoolean");
        node.setAttribute("height", "1");
        node.setAttribute("width", "1");
        Element d = doc.createElement("data");
        d.setAttribute("line", "0");
        d.setAttribute("column", "0");
        d.setAttribute("value", "yes");
        node.appendChild(d);

        ScilabBoolean decoded = (ScilabBoolean) codec().decode(new mxCodec(), node, null);
        assertNotNull(decoded);
        assertFalse(decoded.getElement(0, 0));
    }

    @Test
    public void caseInsensitiveTrueIsAccepted() throws Exception {
        // Boolean.parseBoolean("TRUE") == true : the format is case-insensitive.
        Document doc = newDocument();
        Element node = doc.createElement("ScilabBoolean");
        node.setAttribute("height", "1");
        node.setAttribute("width", "1");
        Element d = doc.createElement("data");
        d.setAttribute("line", "0");
        d.setAttribute("column", "0");
        d.setAttribute("value", "TRUE");
        node.appendChild(d);

        ScilabBoolean decoded = (ScilabBoolean) codec().decode(new mxCodec(), node, null);
        assertTrue(decoded.getElement(0, 0));
    }
}
