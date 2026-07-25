/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Scilab Enterprises
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 *
 */

package org.scilab.modules.graphic_objects.graphicView;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties;
import org.scilab.modules.graphic_objects.rectangle.Rectangle;
import org.scilab.modules.graphic_objects.uicontrol.frame.border.FrameBorder;
import org.scilab.modules.graphic_objects.uicontrol.pushbutton.PushButton;

/**
 * Hermetic unit tests for the controller-free surface of the {@code graphicView}
 * package. The {@link TreeView#GraphicObjectNode} inner class (reachable from
 * this same-package test as a {@code protected static} member) wraps a
 * {@link org.scilab.modules.graphic_objects.graphicObject.GraphicObject} and
 * renders it to a label / HTML detail table without ever constructing the
 * Swing {@code TreeView} frame. Type names are resolved through
 * {@link LogView#pierreDeRosette}, the reflection-built int-to-property-name
 * translation table, which is exercised directly too.
 *
 * <p>The wrapped object is a parentless {@link Rectangle}: with a zero parent,
 * {@code getProperty} never dereferences the {@code GraphicController}, so the
 * whole render path is native-free.
 */
public class TreeViewTest {

    @Test
    public void rosettaTableTranslatesPropertyIntToItsFieldName() {
        // Built by reflection over GraphicObjectProperties' public int fields.
        String name = LogView.pierreDeRosette.get(GraphicObjectProperties.__GO_TYPE__);
        assertNotNull(name);
        assertTrue(name.startsWith("__GO_"), name);
        assertFalse(LogView.pierreDeRosette.isEmpty());
    }

    @Test
    public void nodeToStringShowsTypeNameAndIdentifier() {
        Rectangle rect = new Rectangle();
        rect.setIdentifier(4242);

        TreeView.GraphicObjectNode node = new TreeView.GraphicObjectNode(rect);
        String s = node.toString();

        // "<typeName> : @<id>"
        assertTrue(s.contains("__GO_RECTANGLE__"), s);
        assertTrue(s.contains("@4242"), s);
    }

    @Test
    public void nodeToStringUsesDefaultZeroIdentifier() {
        // A fresh GraphicObject's identifier defaults to 0 (not null), so the
        // render prints "@0" rather than throwing.
        TreeView.GraphicObjectNode node = new TreeView.GraphicObjectNode(new Rectangle());
        assertTrue(node.toString().endsWith("@0"), node.toString());
    }

    @Test
    public void nodeToHtmlRendersPropertyTableForAContouredObject() {
        Rectangle rect = new Rectangle();
        rect.setIdentifier(7);
        rect.setBackground(3);

        TreeView.GraphicObjectNode node = new TreeView.GraphicObjectNode(rect);
        String html = node.toHTML();

        assertTrue(html.startsWith("<html><body>"), html);
        assertTrue(html.endsWith("</body></html>"), html);
        assertTrue(html.contains("Graphic Object of type: __GO_RECTANGLE__"), html);
        assertTrue(html.contains("Id : 7"), html);
        assertTrue(html.contains("<table"), html);

        // The base GraphicObjectPropertyType loop emits a row per property.
        assertTrue(html.contains("TYPE"), html);
        assertTrue(html.contains("VISIBLE"), html);

        // Rectangle is a ContouredObject, so that property block runs as well.
        assertTrue(html.contains("FILLMODE"), html);
    }

    @Test
    public void nodeToHtmlIncludesTheUicontrolSpecificRows() {
        PushButton pb = new PushButton();
        pb.setIdentifier(11);

        String html = new TreeView.GraphicObjectNode(pb).toHTML();
        // The `instanceof Uicontrol` branch appends these four labelled rows.
        assertTrue(html.contains("STYLE"), html);
        assertTrue(html.contains("LAYOUT"), html);
        assertTrue(html.contains("GROUP NAME"), html);
        assertTrue(html.contains("FRAME_BORDER"), html);
    }

    @Test
    public void nodeToHtmlIncludesTheFrameBorderStyleRow() {
        FrameBorder fb = new FrameBorder();
        fb.setIdentifier(12);
        fb.setStyle(1); // LINE — a non-null style is required: toHTML calls
        // getStyleAsEnum().toString() unconditionally on the FrameBorder branch.

        String html = new TreeView.GraphicObjectNode(fb).toHTML();
        assertTrue(html.contains("STYLE"), html);
        assertTrue(html.contains("LINE"), html);
    }
}
