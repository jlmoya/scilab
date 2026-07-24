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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.scilab.modules.types.ScilabString;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import com.mxgraph.io.mxCodec;

/**
 * Hermetic unit tests for {@link ScilabStringCodec}.
 *
 * The codec (de)serializes a {@link ScilabString} matrix to/from the mxGraph
 * DOM format {@code <name height=".." width=".."><data line=".." column=".."
 * value=".."/>...</name>}. DOM nodes are built with the JDK parser and the
 * mxCodec is a plain in-memory instance, so nothing here needs a running
 * Scilab or a display.
 */
public class ScilabStringCodecTest {

    private static ScilabStringCodec codec() {
        return new ScilabStringCodec(new ScilabString(), null, null, null);
    }

    private static Document newDocument() throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
    }

    /** Build a codec element carrying a full row-major string matrix. */
    private static Element matrixNode(Document doc, String[][] data) {
        Element root = doc.createElement("ScilabString");
        root.setAttribute("height", Integer.toString(data.length));
        root.setAttribute("width", Integer.toString(data.length == 0 ? 0 : data[0].length));
        for (int i = 0; i < data.length; i++) {
            for (int j = 0; j < data[i].length; j++) {
                Element d = doc.createElement("data");
                d.setAttribute("line", Integer.toString(i));
                d.setAttribute("column", Integer.toString(j));
                d.setAttribute("value", data[i][j]);
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
    public void decodeReadsRowMajorData() throws Exception {
        Document doc = newDocument();
        Element node = matrixNode(doc, new String[][] {{"a", "b"}, {"c", "d"}});

        ScilabString decoded = (ScilabString) codec().decode(new mxCodec(), node, null);

        assertNotNull(decoded);
        assertEquals(2, decoded.getHeight());
        assertEquals(2, decoded.getWidth());
        assertArrayEquals(new String[][] {{"a", "b"}, {"c", "d"}}, decoded.getData());
    }

    @Test
    public void decodeIsRobustToChildOrdering() throws Exception {
        // The line/column attributes -- not the child order -- place each value.
        Document doc = newDocument();
        Element root = doc.createElement("ScilabString");
        root.setAttribute("height", "1");
        root.setAttribute("width", "2");

        Element second = doc.createElement("data");
        second.setAttribute("line", "0");
        second.setAttribute("column", "1");
        second.setAttribute("value", "second");
        root.appendChild(second);

        Element first = doc.createElement("data");
        first.setAttribute("line", "0");
        first.setAttribute("column", "0");
        first.setAttribute("value", "first");
        root.appendChild(first);

        ScilabString decoded = (ScilabString) codec().decode(new mxCodec(), root, null);
        assertArrayEquals(new String[][] {{"first", "second"}}, decoded.getData());
    }

    @Test
    public void encodeThenDecodeRoundTrips() throws Exception {
        ScilabString original = new ScilabString(new String[][] {{"hello", "world"}});

        mxCodec enc = new mxCodec();
        Node node = codec().encode(enc, original);

        assertEquals("2", ((Element) node).getAttribute("width"));
        assertEquals("1", ((Element) node).getAttribute("height"));

        ScilabString decoded = (ScilabString) codec().decode(enc, node, null);
        assertEquals(original, decoded);
    }

    @Test
    public void zeroSizedMatrixShortCircuitsToTemplate() throws Exception {
        // height*width == 0 returns the freshly cloned (empty) template without
        // reading any <data> child.
        Document doc = newDocument();
        Element node = doc.createElement("ScilabString");
        node.setAttribute("height", "0");
        node.setAttribute("width", "0");

        ScilabString decoded = (ScilabString) codec().decode(new mxCodec(), node, null);
        assertNotNull(decoded);
        assertEquals(0, decoded.getHeight() * decoded.getWidth());
    }

    @Test
    public void nonElementNodeDecodesToNull() throws Exception {
        // A non-ELEMENT node throws UnrecognizeFormatException internally; it is
        // caught and the (never-assigned) result stays null.
        Document doc = newDocument();
        Node text = doc.createTextNode("not-an-element");
        assertNull(codec().decode(new mxCodec(), text, null));
    }

    @Test
    public void dataChildMissingValueIsCaughtAndTemplateReturned() throws Exception {
        // A <data> without a value attribute makes fillData raise
        // UnrecognizeFormatException; decode catches it before setData, so the
        // empty cloned template is returned rather than a partially filled one.
        Document doc = newDocument();
        Element node = doc.createElement("ScilabString");
        node.setAttribute("height", "1");
        node.setAttribute("width", "1");
        Element d = doc.createElement("data");
        d.setAttribute("line", "0");
        d.setAttribute("column", "0");
        node.appendChild(d);

        ScilabString decoded = (ScilabString) codec().decode(new mxCodec(), node, null);
        assertNotNull(decoded);
        assertEquals(0, decoded.getHeight() * decoded.getWidth());
    }
}
