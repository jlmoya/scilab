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
package org.scilab.modules.xcos.io.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.scilab.modules.xcos.port.Orientation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import com.mxgraph.io.mxCodec;
import com.mxgraph.io.mxCodecRegistry;
import com.mxgraph.io.mxObjectCodec;

/**
 * Hermetic unit tests for {@link OrientationCodec}, the jgraphx codec that
 * serializes the {@link Orientation} enum to/from XML (jgraphx has no built-in
 * enum serialization, which is the whole reason this class exists).
 *
 * <p>These tests are pure Java: they build w3c DOM nodes with the JDK's own
 * {@code DocumentBuilderFactory} and drive {@code encode}/{@code decode}
 * directly. They never touch the Scilab native runtime.</p>
 *
 * <p>Note the codec's {@code encode}/{@code decode} do not use their
 * {@code mxCodec}/{@code into} arguments in the paths under test, so those are
 * passed as {@code null} where irrelevant.</p>
 */
public class OrientationCodecTest {

    private static Document newDocument() throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
    }

    /**
     * Build an element carrying (or, when {@code value == null}, deliberately
     * omitting) the {@code value} attribute the codec reads.
     */
    private static Element elementWithValue(String value) throws Exception {
        Element e = newDocument().createElement("Orientation");
        if (value != null) {
            e.setAttribute("value", value);
        }
        return e;
    }

    @Test
    @DisplayName("constructor keeps the supplied template instance")
    public void constructorStoresTemplate() {
        OrientationCodec codec = new OrientationCodec(Orientation.NORTH);
        assertSame(Orientation.NORTH, codec.getTemplate());
    }

    @Test
    @DisplayName("is an mxObjectCodec (so mxCodecRegistry.register accepts it)")
    public void isAnMxObjectCodec() {
        OrientationCodec codec = new OrientationCodec(Orientation.NORTH);
        assertTrue(mxObjectCodec.class.isInstance(codec));
    }

    @Test
    @DisplayName("getName() is the registry name of the template class")
    public void getNameDerivesFromTemplate() {
        OrientationCodec codec = new OrientationCodec(Orientation.NORTH);
        String name = codec.getName();
        assertNotNull(name);
        // The port package is not registered with mxCodecRegistry, so the name
        // is the fully-qualified class name; either way it ends with the simple
        // class name.
        assertTrue(name.endsWith("Orientation"), "unexpected codec name: " + name);
        assertEquals(mxCodecRegistry.getName(Orientation.NORTH), name);
    }

    @Test
    @DisplayName("register() makes the codec retrievable from mxCodecRegistry")
    public void registerWiresCodecIntoRegistry() {
        OrientationCodec.register();

        String name = mxCodecRegistry.getName(Orientation.NORTH);
        mxObjectCodec found = mxCodecRegistry.getCodec(name);

        assertNotNull(found, "codec should be registered under " + name);
        // A registry miss would hand back a freshly-built plain mxObjectCodec,
        // so the concrete type proves our codec is the one that was stored.
        assertTrue(found instanceof OrientationCodec, "wrong codec type: " + found.getClass());
        assertTrue(found.getTemplate() instanceof Orientation);
    }

    @Test
    @DisplayName("decode reads the value attribute for every orientation")
    public void decodeReadsValueAttribute() throws Exception {
        OrientationCodec codec = new OrientationCodec(Orientation.NORTH);

        assertSame(Orientation.WEST, codec.decode(null, elementWithValue("WEST"), null));
        assertSame(Orientation.NORTH, codec.decode(null, elementWithValue("NORTH"), null));
        assertSame(Orientation.EAST, codec.decode(null, elementWithValue("EAST"), null));
        assertSame(Orientation.SOUTH, codec.decode(null, elementWithValue("SOUTH"), null));
    }

    @Test
    @DisplayName("decode ignores the mxCodec and into arguments")
    public void decodeIgnoresUnusedArguments() throws Exception {
        OrientationCodec codec = new OrientationCodec(Orientation.NORTH);
        // Passing a non-null 'into' of an unrelated type must not affect the result.
        assertSame(Orientation.EAST, codec.decode(null, elementWithValue("EAST"), "ignored-into"));
    }

    @Test
    @DisplayName("decode falls back to values()[0] (WEST) when value is missing")
    public void decodeMissingAttributeReturnsFirstConstant() throws Exception {
        OrientationCodec codec = new OrientationCodec(Orientation.NORTH);

        Object result = codec.decode(null, elementWithValue(null), null);

        // The code returns Orientation.values()[0]; pin both the invariant and
        // the concrete current first constant. Notably that is WEST, NOT the
        // NORTH instance used as the register() template.
        assertSame(Orientation.values()[0], result);
        assertSame(Orientation.WEST, result);
    }

    @Test
    @DisplayName("decode fallback triggers on an unrelated attribute too")
    public void decodeUnrelatedAttributeReturnsFirstConstant() throws Exception {
        OrientationCodec codec = new OrientationCodec(Orientation.NORTH);
        Element e = newDocument().createElement("Orientation");
        e.setAttribute("other", "NORTH"); // present, but not the "value" attribute

        assertSame(Orientation.WEST, codec.decode(null, e, null));
    }

    @Test
    @DisplayName("decode throws on an unknown orientation name")
    public void decodeUnknownValueThrows() {
        OrientationCodec codec = new OrientationCodec(Orientation.NORTH);
        assertThrows(IllegalArgumentException.class,
                     () -> codec.decode(null, elementWithValue("DIAGONAL"), null));
    }

    @Test
    @DisplayName("decode throws on an empty value string")
    public void decodeEmptyValueThrows() {
        OrientationCodec codec = new OrientationCodec(Orientation.NORTH);
        assertThrows(IllegalArgumentException.class,
                     () -> codec.decode(null, elementWithValue(""), null));
    }

    @Test
    @DisplayName("decode is case-sensitive (Enum.valueOf semantics)")
    public void decodeIsCaseSensitive() {
        OrientationCodec codec = new OrientationCodec(Orientation.NORTH);
        assertThrows(IllegalArgumentException.class,
                     () -> codec.decode(null, elementWithValue("north"), null));
    }

    @Test
    @DisplayName("encode writes an element named by the registry with a value attribute")
    public void encodeWritesElementAndValue() throws Exception {
        Document doc = newDocument();
        mxCodec enc = new mxCodec(doc);
        OrientationCodec codec = new OrientationCodec(Orientation.NORTH);

        Node node = codec.encode(enc, Orientation.SOUTH);

        assertEquals(Node.ELEMENT_NODE, node.getNodeType());
        // The element is created on the encoder's document...
        assertSame(doc, node.getOwnerDocument());
        // ...named exactly by mxCodecRegistry (independent of the template)...
        assertEquals(mxCodecRegistry.getName(Orientation.SOUTH), node.getNodeName());
        // ...and carries the enum constant name in the "value" attribute.
        assertEquals("SOUTH", ((Element) node).getAttribute("value"));
    }

    @Test
    @DisplayName("encode then decode is a round-trip for every orientation")
    public void encodeDecodeRoundTrip() throws Exception {
        mxCodec enc = new mxCodec(newDocument());
        OrientationCodec codec = new OrientationCodec(Orientation.NORTH);

        for (Orientation o : Orientation.values()) {
            Node node = codec.encode(enc, o);
            Object back = codec.decode(null, node, null);
            assertSame(o, back, "round-trip failed for " + o);
        }
    }
}
