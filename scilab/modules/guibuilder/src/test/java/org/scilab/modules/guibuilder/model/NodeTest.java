/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 *
 */

package org.scilab.modules.guibuilder.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class NodeTest {

    private static SourceRange anywhere() {
        return new SourceRange(0, 10);
    }

    @Test
    public void styleNamesMapFromScilabSpelling() {
        assertEquals(WidgetStyle.PUSHBUTTON, WidgetStyle.fromScilab("pushbutton"));
        assertEquals(WidgetStyle.POPUPMENU, WidgetStyle.fromScilab("popupmenu"));
    }

    @Test
    public void anUnknownStyleIsNullRatherThanAnException() {
        // The parser must be able to ask "is this a style I know?" without
        // catching. An unknown style locks the widget; it never aborts a parse.
        assertNull(WidgetStyle.fromScilab("hologram"));
    }

    @Test
    public void aNodeWithOnlyLiteralPropertiesIsNotLocked() {
        Node n = new Node("okButton", WidgetStyle.PUSHBUTTON, anywhere());
        n.putProperty("string", PropertyValue.literal("\"OK\"", anywhere(), "OK"));
        assertFalse(n.isLocked());
    }

    /**
     * The computed property is added LAST here and FIRST in the test below,
     * and both orders matter. Adding it only ever last would let an {@code
     * isLocked()} that examined nothing but the most recently added property
     * pass -- a mistake easy to write and impossible to see from a green
     * suite. The pair pins "any locked property locks the node", which is
     * what the class actually promises.
     */
    @Test
    public void oneComputedPropertyLocksTheNodeWhenItIsAddedLast() {
        Node n = new Node("okButton", WidgetStyle.PUSHBUTTON, anywhere());
        n.putProperty("string", PropertyValue.literal("\"OK\"", anywhere(), "OK"));
        n.putProperty("position", PropertyValue.computed("[x y w h]", anywhere(),
                                                         "position is computed from variables"));
        assertTrue(n.isLocked());
        // ...but only that property is locked; the rest stay editable.
        assertFalse(n.properties().get("string").isLocked());
        assertTrue(n.properties().get("position").isLocked());
    }

    @Test
    public void oneComputedPropertyLocksTheNodeWhenItIsAddedFirst() {
        Node n = new Node("okButton", WidgetStyle.PUSHBUTTON, anywhere());
        n.putProperty("position", PropertyValue.computed("[x y w h]", anywhere(),
                                                         "position is computed from variables"));
        n.putProperty("string", PropertyValue.literal("\"OK\"", anywhere(), "OK"));
        n.putProperty("visible", PropertyValue.literal("\"on\"", anywhere(), "on"));
        assertTrue(n.isLocked(), "a locked property anywhere in the map locks the node");
        assertFalse(n.properties().get("string").isLocked());
        assertFalse(n.properties().get("visible").isLocked());
        assertTrue(n.properties().get("position").isLocked());
    }
}
