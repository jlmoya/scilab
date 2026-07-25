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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import com.mxgraph.io.mxCodec;

/**
 * Hermetic unit tests for {@link ScilabGraphCodec}.
 *
 * The {@code beforeEncode}/{@code afterDecode} hooks cast to a live Swing
 * {@link org.scilab.modules.graph.ScilabGraph} and are out of scope here. The
 * two constructors and the {@code trace(...)} helper - which appends a
 * {@code String.format}-ed XML comment to a node - are exercised against an
 * in-memory DOM owned by a bare {@link mxCodec}, so no display or native code is
 * needed.
 */
public class ScilabGraphCodecTest {

    @Test
    public void singleArgConstructorBuildsACodec() {
        ScilabGraphCodec codec = new ScilabGraphCodec(new Object());
        assertNotNull(codec);
    }

    @Test
    public void configurationConstructorBuildsACodec() {
        Map<String, String> mapping = new HashMap<String, String>();
        mapping.put("from", "to");
        ScilabGraphCodec codec = new ScilabGraphCodec(new Object(), new String[] {"excluded"}, new String[] {"idref"}, mapping);
        assertNotNull(codec);
    }

    @Test
    public void traceAppendsAFormattedCommentNode() {
        mxCodec enc = new mxCodec();
        Element node = enc.getDocument().createElement("root");
        ScilabGraphCodec codec = new ScilabGraphCodec(new Object());

        codec.trace(enc, node, "value=%d/%s", 42, "ok");

        Node child = node.getFirstChild();
        assertNotNull(child);
        assertEquals(Node.COMMENT_NODE, child.getNodeType());
        assertEquals("value=42/ok", child.getNodeValue());
    }

    @Test
    public void traceWithNoFormatArgumentsCopiesTheMessageVerbatim() {
        mxCodec enc = new mxCodec();
        Element node = enc.getDocument().createElement("root");
        ScilabGraphCodec codec = new ScilabGraphCodec(new Object());

        codec.trace(enc, node, "a plain message");

        assertEquals("a plain message", node.getFirstChild().getNodeValue());
    }

    @Test
    public void traceEmitsExactlyOneChildPerCall() {
        mxCodec enc = new mxCodec();
        Element node = enc.getDocument().createElement("root");
        ScilabGraphCodec codec = new ScilabGraphCodec(new Object());
        assertNull(node.getFirstChild());

        codec.trace(enc, node, "first");
        codec.trace(enc, node, "second");

        assertEquals(2, node.getChildNodes().getLength());
        assertEquals("first", node.getFirstChild().getNodeValue());
        assertEquals("second", node.getLastChild().getNodeValue());
    }
}
