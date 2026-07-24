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

package org.scilab.modules.preferences.Component;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Hermetic unit tests for the {@link XCosPreview} preference component (a
 * {@link javax.swing.JLabel}). {@code refresh} walks {@code peer -> first child ->
 * first child} and joins that grandchild's children into a
 * {@code [name=value;...]} string set as the label text. That is pure DOM string
 * building; no native code, no Xcos and no display is required.
 */
public class XCosPreviewTest {

    private static Document doc() throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
    }

    /** Build {@code <XCosPreview><lvl1><root> children... </root></lvl1></XCosPreview>}. */
    private static Element peerWithPorts(Document d, String... portNames) {
        Element root = d.createElement("root");
        for (String p : portNames) {
            root.appendChild(d.createElement(p));
        }
        Element lvl1 = d.createElement("lvl1");
        lvl1.appendChild(root);
        Element peer = d.createElement("XCosPreview");
        peer.appendChild(lvl1);
        return peer;
    }

    @Test
    public void refreshJoinsGrandchildElementsIntoTheLabelText() throws Exception {
        Document d = doc();
        XCosPreview c = new XCosPreview(d.createElement("XCosPreview"));
        c.refresh(peerWithPorts(d, "in", "out"));
        // Element nodes have a null nodeValue, so each entry is "name=null;".
        assertEquals("[in=null;out=null;]", c.getText());
    }

    @Test
    public void refreshOnAnEmptyRootProducesEmptyBrackets() throws Exception {
        Document d = doc();
        XCosPreview c = new XCosPreview(d.createElement("XCosPreview"));
        c.refresh(peerWithPorts(d));
        assertEquals("[]", c.getText());
    }

    @Test
    public void actuatorsAreEmpty() throws Exception {
        assertArrayEquals(new String[] {}, new XCosPreview(doc().createElement("XCosPreview")).actuators());
    }

    @Test
    public void toStringIsBlockPreview() throws Exception {
        assertEquals("BlockPreview", new XCosPreview(doc().createElement("XCosPreview")).toString());
    }
}
