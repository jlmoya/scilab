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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.scilab.modules.preferences.XCommonManager;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Hermetic unit tests for the package-private {@code Model} (the
 * {@link javax.swing.table.AbstractTableModel} declared in {@code Table.java}).
 * It scans a DOM subtree, treating {@code <tableCol>} children as columns and
 * {@code <tableRow>} children as rows, and resolves cell values by attribute
 * name. All pure DOM/attribute logic; no display required.
 *
 * <p>The DOM is built by PARSING XML (not {@code createElement}) so attribute
 * values are freshly-allocated strings &mdash; this is what makes the
 * {@code isCellEditable} reference-equality defect deterministic (see
 * {@link #isCellEditableIsBrokenByReferenceEquality()}).
 */
public class ModelTest {

    private static Element parse(String xml) throws Exception {
        Document d = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                     .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        return d.getDocumentElement();
    }

    private static final String TABLE =
        "<Table>"
        + "<tableCol title='Name' attr='name' editable='true'/>"
        + "<tableCol title='Color' attr='color'/>"
        + "<tableRow name='Alice' color='#ff0000'/>"
        + "<tableRow name='Bob' color='blue'/>"
        + "<tablePrototype name='proto'/>"
        + "</Table>";

    private static Model model() throws Exception {
        return new Model(parse(TABLE));
    }

    @Test
    public void columnCountCountsTableColChildren() throws Exception {
        assertEquals(2, model().getColumnCount());
    }

    @Test
    public void rowCountCountsTableRowChildren() throws Exception {
        assertEquals(2, model().getRowCount());
    }

    @Test
    public void columnCountIsStableAcrossRepeatedCalls() throws Exception {
        // Exercises the prevC/prevR memoisation branch on the second call.
        Model m = model();
        assertEquals(2, m.getColumnCount());
        assertEquals(2, m.getColumnCount());
        assertEquals(2, m.getRowCount());
        assertEquals(2, m.getRowCount());
    }

    @Test
    public void columnNamesComeFromTheTitleAttribute() throws Exception {
        Model m = model();
        assertEquals("Name", m.getColumnName(0));
        assertEquals("Color", m.getColumnName(1));
    }

    @Test
    public void columnAttrComesFromTheAttrAttribute() throws Exception {
        assertEquals("name", model().getColumnAttr(0));
    }

    @Test
    public void columnNameOfAnOutOfRangeColumnIsTheNavSentinel() throws Exception {
        assertEquals(XCommonManager.NAV, model().getColumnName(9),
                     "no <tableCol> at index 9 -> getColumnRecord is null -> NAV");
    }

    @Test
    public void prototypeRecordIsFoundByNodeName() throws Exception {
        Element proto = (Element) model().getPrototypeRecord();
        assertNotNull(proto);
        assertEquals("tablePrototype", proto.getNodeName());
    }

    @Test
    public void prototypeRecordIsNullWhenAbsent() throws Exception {
        Model m = new Model(parse("<Table><tableRow name='x'/></Table>"));
        assertNull(m.getPrototypeRecord());
    }

    @Test
    public void plainCellValueIsTheAttributeString() throws Exception {
        Model m = model();
        assertEquals("Alice", m.getValueAt(0, 0));
        assertEquals("Bob", m.getValueAt(1, 0));
    }

    @Test
    public void hashPrefixedCellValueIsDecodedToAColor() throws Exception {
        Object cell = model().getValueAt(0, 1); // color='#ff0000'
        assertInstanceOf(java.awt.Color.class, cell);
        assertEquals(new java.awt.Color(255, 0, 0), cell);
    }

    @Test
    public void nonHashColorValueStaysAString() throws Exception {
        assertEquals("blue", model().getValueAt(1, 1), "only '#'-prefixed values are decoded");
    }

    @Test
    public void setValueAtWritesThroughToTheRowAttribute() throws Exception {
        Model m = model();
        m.setValueAt("Carol", 0, 0);
        assertEquals("Carol", m.getValueAt(0, 0));
    }

    @Test
    public void isCellEditableIsBrokenByReferenceEquality() throws Exception {
        // Defect characterization: isCellEditable compares the attribute value with
        // `value == "true"` (reference equality) instead of `.equals("true")`.
        // The parsed DOM string is never the interned literal, so even a column
        // explicitly declared editable='true' reports NON-editable.
        Model m = model();
        assertFalse(m.isCellEditable(0, 0),
                    "editable='true' SHOULD be editable, but == comparison makes it false");
        assertFalse(m.isCellEditable(0, 1), "column with no 'editable' attribute is not editable");
    }
}
