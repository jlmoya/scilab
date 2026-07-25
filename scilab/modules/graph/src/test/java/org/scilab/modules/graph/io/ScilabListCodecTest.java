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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.scilab.modules.types.ScilabList;
import org.scilab.modules.types.ScilabMList;
import org.scilab.modules.types.ScilabString;
import org.scilab.modules.types.ScilabTList;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import com.mxgraph.io.mxCodec;

/**
 * Hermetic unit tests for {@link ScilabListCodec}'s two pure overrides:
 *
 * <ul>
 * <li>{@code beforeEncode} stamps a {@code scilabClass} attribute recording the
 * concrete list flavour (ScilabList / ScilabMList / ScilabTList), because on the
 * wire every list is emitted under the generic "Array" element name.</li>
 * <li>{@code cloneTemplate} reads that attribute back to reconstruct the right
 * concrete type (the workaround for jgraphx bug 55).</li>
 * </ul>
 *
 * The full encode/decode delegate to jgraphx reflection machinery and are not
 * exercised here; these two hooks carry the Scilab-specific behaviour.
 */
public class ScilabListCodecTest {

    private static ScilabListCodec codec() {
        return new ScilabListCodec(new ScilabList(), new String[] {"scilabClass"}, null, null);
    }

    private static Document newDocument() throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
    }

    @BeforeEach
    public void ensureTextSerialization() {
        // beforeEncode only stamps the attribute when NOT in binary mode; make
        // the shared static toggle deterministic regardless of test ordering.
        ScilabObjectCodec.disableBinarySerialization();
    }

    /*
     * ----- beforeEncode : records the concrete list flavour -----
     */

    @Test
    public void beforeEncodeStampsScilabListClass() throws Exception {
        Document doc = newDocument();
        Element node = doc.createElement("Array");

        Object returned = codec().beforeEncode(new mxCodec(), new ScilabList(), node);

        assertEquals("ScilabList", node.getAttribute("scilabClass"));
        assertTrue(returned instanceof ScilabList);
    }

    @Test
    public void beforeEncodeStampsScilabMListClass() throws Exception {
        Document doc = newDocument();
        Element node = doc.createElement("Array");

        codec().beforeEncode(new mxCodec(), new ScilabMList(), node);

        assertEquals("ScilabMList", node.getAttribute("scilabClass"));
    }

    @Test
    public void beforeEncodeStampsScilabTListClass() throws Exception {
        Document doc = newDocument();
        Element node = doc.createElement("Array");

        codec().beforeEncode(new mxCodec(), new ScilabTList(), node);

        assertEquals("ScilabTList", node.getAttribute("scilabClass"));
    }

    @Test
    public void beforeEncodeReturnsTheSameObject() throws Exception {
        Document doc = newDocument();
        Element node = doc.createElement("Array");
        ScilabList list = new ScilabList();

        assertSame(list, codec().beforeEncode(new mxCodec(), list, node));
    }

    /*
     * ----- cloneTemplate : reconstructs the concrete type from the attribute -----
     */

    @Test
    public void cloneTemplateBuildsScilabMList() throws Exception {
        Document doc = newDocument();
        Element node = doc.createElement("Array");
        node.setAttribute("scilabClass", "ScilabMList");

        Object clone = codec().cloneTemplate(node);
        assertTrue(clone instanceof ScilabMList);
    }

    @Test
    public void cloneTemplateBuildsScilabTList() throws Exception {
        Document doc = newDocument();
        Element node = doc.createElement("Array");
        node.setAttribute("scilabClass", "ScilabTList");

        Object clone = codec().cloneTemplate(node);
        assertTrue(clone instanceof ScilabTList);
    }

    @Test
    public void cloneTemplateBuildsExactScilabList() throws Exception {
        Document doc = newDocument();
        Element node = doc.createElement("Array");
        node.setAttribute("scilabClass", "ScilabList");

        Object clone = codec().cloneTemplate(node);
        assertNotNull(clone);
        assertSame(ScilabList.class, clone.getClass());
        // ScilabMList / ScilabTList are siblings, not subclasses, of ScilabList.
        assertFalse(clone instanceof ScilabMList);
        assertFalse(clone instanceof ScilabTList);
    }

    @Test
    public void cloneTemplateIsCaseInsensitiveOnTheAttribute() throws Exception {
        Document doc = newDocument();
        Element node = doc.createElement("Array");
        node.setAttribute("scilabClass", "scilabmlist");

        Object clone = codec().cloneTemplate(node);
        assertTrue(clone instanceof ScilabMList);
    }

    @Test
    public void cloneTemplateWithoutAttributeFallsBackToTemplateType() throws Exception {
        // No scilabClass attribute -> super.cloneTemplate() returns a new
        // instance of the template class (ScilabList).
        Document doc = newDocument();
        Element node = doc.createElement("Array");

        Object clone = codec().cloneTemplate(node);
        assertSame(ScilabList.class, clone.getClass());
    }

    /*
     * ----- binary serialization path (encode + decode) -----
     */

    @Test
    public void binaryEncodeStoresTheListAndDecodeReturnsItFromTheStore() {
        ScilabList list = new ScilabList();
        list.add(new ScilabString("a"));

        ScilabList store = new ScilabList();
        ScilabObjectCodec.enableBinarySerialization(store);
        try {
            mxCodec enc = new mxCodec();
            Node node = codec().encode(enc, list);
            assertEquals("true", ((Element) node).getAttribute("binary"));
            assertEquals("0", ((Element) node).getAttribute("position"));
            assertEquals(1, store.size());
            assertSame(list, store.get(0));

            Object decoded = codec().decode(enc, node, null);
            assertSame(list, decoded);
        } finally {
            ScilabObjectCodec.disableBinarySerialization();
        }
    }
}
