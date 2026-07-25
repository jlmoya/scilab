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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.scilab.modules.types.ScilabInteger;
import org.scilab.modules.types.ScilabIntegerTypeEnum;
import org.scilab.modules.types.ScilabList;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import com.mxgraph.io.mxCodec;

/**
 * Hermetic unit tests for {@link ScilabIntegerCodec}. The decoded precision is
 * driven by the {@code intPrecision} attribute (defaulting to {@code sci_uint8}
 * when absent) and each cell value is parsed with the width-appropriate parser
 * ({@code Byte/Short/Integer/Long.parse...}).
 */
public class ScilabIntegerCodecTest {

    private static ScilabIntegerCodec codec() {
        return new ScilabIntegerCodec(new ScilabInteger(), null, null, null);
    }

    private static Document newDocument() throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
    }

    /** Build a 1x1 integer node with an explicit (or null) precision and value. */
    private static Element scalarNode(Document doc, String intPrecision, String value) {
        Element root = doc.createElement("ScilabInteger");
        root.setAttribute("height", "1");
        root.setAttribute("width", "1");
        if (intPrecision != null) {
            root.setAttribute("intPrecision", intPrecision);
        }
        Element d = doc.createElement("data");
        d.setAttribute("line", "0");
        d.setAttribute("column", "0");
        d.setAttribute("value", value);
        root.appendChild(d);
        return root;
    }

    @Test
    public void isAScilabObjectCodec() {
        assertTrue(codec() instanceof ScilabObjectCodec);
    }

    @Test
    public void decodeSignedInt32() throws Exception {
        Document doc = newDocument();
        Element node = scalarNode(doc, "sci_int32", "-70000");

        ScilabInteger decoded = (ScilabInteger) codec().decode(new mxCodec(), node, null);

        assertNotNull(decoded);
        assertEquals(ScilabIntegerTypeEnum.sci_int32, decoded.getPrec());
        assertEquals(-70000L, decoded.getData()[0][0]);
    }

    @Test
    public void decodeDefaultsToUnsignedByteWhenPrecisionAbsent() throws Exception {
        // With no intPrecision attribute the decoder falls back to sci_uint8.
        Document doc = newDocument();
        Element node = scalarNode(doc, null, "42");

        ScilabInteger decoded = (ScilabInteger) codec().decode(new mxCodec(), node, null);

        assertEquals(ScilabIntegerTypeEnum.sci_uint8, decoded.getPrec());
        assertTrue(decoded.isUnsigned());
        assertEquals(42L, decoded.getData()[0][0]);
    }

    @Test
    public void encodeThenDecodeInt8RoundTrips() throws Exception {
        ScilabInteger original = new ScilabInteger(new byte[][] {{5, -6}}, false);
        assertRoundTrips(original, ScilabIntegerTypeEnum.sci_int8);
    }

    @Test
    public void encodeThenDecodeInt16RoundTrips() throws Exception {
        ScilabInteger original = new ScilabInteger(new short[][] {{100, -200}}, false);
        assertRoundTrips(original, ScilabIntegerTypeEnum.sci_int16);
    }

    @Test
    public void encodeThenDecodeInt32RoundTrips() throws Exception {
        ScilabInteger original = new ScilabInteger(new int[][] {{70000, -1}}, false);
        assertRoundTrips(original, ScilabIntegerTypeEnum.sci_int32);
    }

    @Test
    public void encodeThenDecodeInt64RoundTrips() throws Exception {
        ScilabInteger original = new ScilabInteger(new long[][] {{123456789012L}}, false);
        assertRoundTrips(original, ScilabIntegerTypeEnum.sci_int64);
    }

    private void assertRoundTrips(ScilabInteger original, ScilabIntegerTypeEnum expectedPrec) {
        mxCodec enc = new mxCodec();
        Node node = codec().encode(enc, original);
        assertEquals(expectedPrec.name(), ((Element) node).getAttribute("intPrecision"));

        ScilabInteger decoded = (ScilabInteger) codec().decode(enc, node, null);
        assertEquals(expectedPrec, decoded.getPrec());
        assertEquals(original, decoded);
    }

    @Test
    public void unsignedShortCarriesValueAboveSignedByteRange() throws Exception {
        // Positive control for the defect below: sci_uint16 uses Short.parseShort
        // and therefore preserves 200 (which does not fit a signed byte).
        Document doc = newDocument();
        Element node = scalarNode(doc, "sci_uint16", "200");

        ScilabInteger decoded = (ScilabInteger) codec().decode(new mxCodec(), node, null);
        assertEquals(200L, decoded.getData()[0][0]);
    }

    @Test
    public void unsignedByteValueAbove127IsLost_defectCharacterization() throws Exception {
        // Defect characterization: sci_uint8 cells are parsed with Byte.parseByte,
        // whose valid range is [-128, 127]. A legitimate unsigned-byte value like
        // 200 therefore throws NumberFormatException; decode catches it before
        // setData, so the whole matrix is silently dropped to the empty template.
        Document doc = newDocument();
        Element node = scalarNode(doc, "sci_uint8", "200");

        ScilabInteger decoded = (ScilabInteger) codec().decode(new mxCodec(), node, null);

        assertNotNull(decoded);
        assertEquals(new ScilabInteger(), decoded);                 // empty template
        assertNotEquals(new ScilabInteger((short) 200, true), decoded);
    }

    @Test
    public void encodeThenDecodeUnsignedInt32RoundTrips() throws Exception {
        // Exercises the sci_uint32 branch of the decode switch.
        assertRoundTrips(new ScilabInteger(new int[][] {{10, 20}}, true), ScilabIntegerTypeEnum.sci_uint32);
    }

    @Test
    public void encodeThenDecodeUnsignedInt16RoundTrips() throws Exception {
        assertRoundTrips(new ScilabInteger(new short[][] {{100, 200}}, true), ScilabIntegerTypeEnum.sci_uint16);
    }

    @Test
    public void encodeThenDecodeUnsignedInt64RoundTrips() throws Exception {
        // The default branch of the switch, in its unsigned form.
        assertRoundTrips(new ScilabInteger(new long[][] {{123456789012L}}, true), ScilabIntegerTypeEnum.sci_uint64);
    }

    @Test
    public void binaryEncodeStoresTheObjectAndDecodeReturnsItFromTheStore() {
        // In binary mode encode emits only a position marker and pushes the
        // live object into the shared store; decode reads it straight back.
        ScilabInteger original = new ScilabInteger(new int[][] {{7, -3}}, false);
        ScilabList store = new ScilabList();
        ScilabObjectCodec.enableBinarySerialization(store);
        try {
            mxCodec enc = new mxCodec();
            Node node = codec().encode(enc, original);
            assertEquals("true", ((Element) node).getAttribute("binary"));
            assertEquals("0", ((Element) node).getAttribute("position"));
            assertEquals(1, store.size());
            assertSame(original, store.get(0));

            Object decoded = codec().decode(enc, node, null);
            assertSame(original, decoded);
        } finally {
            ScilabObjectCodec.disableBinarySerialization();
        }
    }

    @Test
    public void emptyMatrixDecodesToTheEmptyTemplate() throws Exception {
        // height * width == 0 short-circuits before any data parsing.
        Document doc = newDocument();
        Element node = doc.createElement("ScilabInteger");
        node.setAttribute("height", "0");
        node.setAttribute("width", "0");
        node.setAttribute("intPrecision", "sci_int32");

        ScilabInteger decoded = (ScilabInteger) codec().decode(new mxCodec(), node, null);
        assertNotNull(decoded);
        assertEquals(new ScilabInteger(), decoded);
    }

    @Test
    public void nonElementNodeDecodesToNull() throws Exception {
        // A non-ELEMENT node trips UnrecognizeFormatException, caught before obj
        // is ever assigned, so decode returns the null it started with.
        Document doc = newDocument();
        Node text = doc.createTextNode("not an element");
        assertNull(codec().decode(new mxCodec(), text, null));
    }

    @Test
    public void legacyPrecisionAttributeType32SignedIsHonoured() throws Exception {
        // Pre-5.2.2 files carry a "precision" attribute (type8/16/32/64) instead
        // of "intPrecision"; convertOldType maps it to the modern enum.
        Document doc = newDocument();
        Element node = scalarNode(doc, null, "-123456");
        node.setAttribute("precision", "type32");

        ScilabInteger decoded = (ScilabInteger) codec().decode(new mxCodec(), node, null);
        assertEquals(ScilabIntegerTypeEnum.sci_int32, decoded.getPrec());
        assertEquals(-123456L, decoded.getData()[0][0]);
    }

    @Test
    public void legacyPrecisionType8WithBUnsignedDecodesAsUnsignedByte() throws Exception {
        // The mere presence of the bUnsigned attribute selects the unsigned type.
        Document doc = newDocument();
        Element node = scalarNode(doc, null, "100");
        node.setAttribute("precision", "type8");
        node.setAttribute("bUnsigned", "true");

        ScilabInteger decoded = (ScilabInteger) codec().decode(new mxCodec(), node, null);
        assertEquals(ScilabIntegerTypeEnum.sci_uint8, decoded.getPrec());
        assertTrue(decoded.isUnsigned());
        assertEquals(100L, decoded.getData()[0][0]);
    }
}
