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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.DefaultListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.TableModelEvent;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Hermetic unit tests for the {@link Table} preference component. {@code Table}
 * extends {@link Panel} (a {@code JPanel}) and wraps a {@code JTable} whose model
 * is scanned out of the {@code <tableCol>}/{@code <tableRow>} children of its DOM
 * node — all constructed headless, no display needed.
 *
 * <p>Exercised: the row-selection {@code item} sensor/actuator, the {@code choose}
 * value (selected-index vs. a named column), the dynamic-control action dispatch
 * ({@code tableAdd}/{@code tableDel}), and the static {@code processModelEvent}
 * reducer that copies a changed cell back onto an action node. The interactive
 * cell renderer and the header widget are display-bound and out of scope.
 */
public class TableTest {

    private static Document newDoc() throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
    }

    /**
     * A two-column ("name", "color"), two-row table node. {@code size} defaults
     * to "dynamic" (wires the add/remove controls) and extra attributes can be
     * appended as key/value pairs.
     */
    private static Element tableNode(Document doc, String... kv) {
        Element t = doc.createElement("Table");
        t.setAttribute("size", "dynamic");
        for (int i = 0; i + 1 < kv.length; i += 2) {
            t.setAttribute(kv[i], kv[i + 1]);
        }
        Element col1 = doc.createElement("tableCol");
        col1.setAttribute("attr", "name");
        col1.setAttribute("title", "Name");
        Element col2 = doc.createElement("tableCol");
        col2.setAttribute("attr", "color");
        col2.setAttribute("title", "Color");
        Element row1 = doc.createElement("tableRow");
        row1.setAttribute("name", "alpha");
        row1.setAttribute("color", "red");
        Element row2 = doc.createElement("tableRow");
        row2.setAttribute("name", "beta");
        row2.setAttribute("color", "blue");
        t.appendChild(col1);
        t.appendChild(col2);
        t.appendChild(row1);
        t.appendChild(row2);
        return t;
    }

    // ----- construction / trivial surface -----------------------------------

    @Test
    public void constructsHeadlessFromADomTable() throws Exception {
        Table t = new Table(tableNode(newDoc()));
        assertNotNull(t);
        assertArrayEquals(new String[] {"item"}, t.actuators());
        assertEquals("Table ...", t.toString());
    }

    @Test
    public void itemIsZeroWhenNothingIsSelected() throws Exception {
        // getSelectedRow() == -1 with no selection, so item() == "-1 + 1" == "0".
        Table t = new Table(tableNode(newDoc()));
        assertEquals("0", t.item());
    }

    // ----- item sensor / actuator -------------------------------------------

    @Test
    public void itemActuatorSelectsTheOneBasedRow() throws Exception {
        Table t = new Table(tableNode(newDoc()));
        t.item("1");
        assertEquals("1", t.item(), "selecting row 1 (index 0) reads back as \"1\"");
        t.item("2");
        assertEquals("2", t.item());
    }

    @Test
    public void itemActuatorIgnoresTheMinusOneSentinel() throws Exception {
        Table t = new Table(tableNode(newDoc()));
        t.item("2");
        t.item("-1"); // the (selectedRow != -1) guard makes this a no-op
        assertEquals("2", t.item(), "-1 does not clear the existing selection");
    }

    // ----- choose ------------------------------------------------------------

    @Test
    public void chooseWithoutAColumnReturnsTheSelectedIndex() throws Exception {
        // No "column" attribute => column == NAV => choose() delegates to item().
        Table t = new Table(tableNode(newDoc()));
        t.item("2");
        assertEquals("2", t.choose());
    }

    @Test
    public void chooseWithAColumnReturnsThatColumnsCellValue() throws Exception {
        Table t = new Table(tableNode(newDoc(), "column", "name"));
        t.item("1");
        assertEquals("alpha", t.choose(), "row 1's \"name\" attribute");
        t.item("2");
        assertEquals("beta", t.choose());
    }

    // ----- dynamic controls dispatch ----------------------------------------

    @Test
    public void addControlEmitsATableAddAction() throws Exception {
        Table t = new Table(tableNode(newDoc()));
        AtomicReference<String> got = new AtomicReference<>();
        t.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                got.set(e.getActionCommand());
            }
        });
        // controls[0] is the "add row" button.
        t.actionPerformed(new ActionEvent(t.controls[0], 0, "add"));
        assertEquals("tableAdd", got.get());
    }

    @Test
    public void removeControlEmitsATableDelAction() throws Exception {
        Table t = new Table(tableNode(newDoc()));
        AtomicReference<String> got = new AtomicReference<>();
        t.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                got.set(e.getActionCommand());
            }
        });
        // controls[4] is the "remove row" button.
        t.actionPerformed(new ActionEvent(t.controls[4], 0, "del"));
        assertEquals("tableDel", got.get());
    }

    @Test
    public void moveControlsAreInertAndEmitNoAction() throws Exception {
        Table t = new Table(tableNode(newDoc()));
        AtomicReference<String> got = new AtomicReference<>();
        t.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                got.set(e.getActionCommand());
            }
        });
        // controls[1] (up) and controls[3] (down) are no-ops in this build.
        t.actionPerformed(new ActionEvent(t.controls[1], 0, "up"));
        t.actionPerformed(new ActionEvent(t.controls[3], 0, "down"));
        assertNull(got.get(), "the reorder buttons transmit nothing");
    }

    // ----- static processModelEvent -----------------------------------------

    @Test
    public void processModelEventCopiesTheChangedCellOntoTheActionNode() throws Exception {
        Document doc = newDoc();
        Element peer = tableNode(doc);
        Model model = new Model(peer);

        // A change on row 0, column 0 (attr "name", value "alpha").
        TableModelEvent event = new TableModelEvent(model, 0, 0, 0);
        Element action = doc.createElement("action");
        action.setAttribute("context", "root/");

        Table.processModelEvent(event, new org.w3c.dom.Node[] {action});

        assertEquals("name", action.getAttribute("set"), "the changed column's attr name");
        assertEquals("alpha", action.getAttribute("value"), "the changed cell's value");
        assertEquals("root/1/", action.getAttribute("context"),
                     "context is extended with the one-based row index");
    }

    @Test
    public void processModelEventUsesTheEventsRowAndColumn() throws Exception {
        Document doc = newDoc();
        Element peer = tableNode(doc);
        Model model = new Model(peer);

        // Row 1, column 1 (attr "color", value "blue").
        TableModelEvent event = new TableModelEvent(model, 1, 1, 1);
        Element action = doc.createElement("action");
        action.setAttribute("context", "c/");

        Table.processModelEvent(event, new org.w3c.dom.Node[] {action});

        assertEquals("color", action.getAttribute("set"));
        assertEquals("blue", action.getAttribute("value"));
        assertEquals("c/2/", action.getAttribute("context"));
    }

    // ----- refresh / external selection -------------------------------------

    @Test
    public void refreshSelectsTheRowNamedByTheItemAttribute() throws Exception {
        Document doc = newDoc();
        Table t = new Table(tableNode(doc));
        Element peer = tableNode(doc);
        peer.setAttribute("item", "2");
        t.refresh(peer);
        assertEquals("2", t.item(), "refresh drives the selection from the item attribute");
    }

    @Test
    public void externalSelectionChangeEmitsATableSelectAction() throws Exception {
        Table t = new Table(tableNode(newDoc()));
        // item(...) flips the internal "externalChange" flag on, which is what
        // gates valueChanged from firing a callback.
        t.item("1");
        AtomicReference<String> got = new AtomicReference<>();
        t.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                got.set(e.getActionCommand());
            }
        });
        t.valueChanged(new ListSelectionEvent(new DefaultListSelectionModel(), 0, 0, false));
        assertEquals("tableSelect", got.get());
    }
}
