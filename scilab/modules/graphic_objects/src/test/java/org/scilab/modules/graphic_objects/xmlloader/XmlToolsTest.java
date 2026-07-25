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

package org.scilab.modules.graphic_objects.xmlloader;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Hermetic unit tests for {@link XmlTools}. Every {@code setPropAs*} setter ends
 * by pushing into GraphicController, which is native and out of scope; what is
 * covered here is the controller-free surface:
 * <ul>
 *   <li>the {@code getFromMap} helpers, which are pure map lookups with a
 *       remove-on-hit side effect and a default fallback;</li>
 *   <li>the early-return guards of every setter — a {@code null} / empty input,
 *       or (for the comma-separated vector setters) more tokens than the declared
 *       size — all of which return {@code false} before the controller is ever
 *       touched.</li>
 * </ul>
 */
public class XmlToolsTest {

    private static final Integer UID = 1;
    private static final Integer PROP = 2;

    /* ------------------------------ getFromMap --------------------------- */

    @Test
    public void getFromMapReturnsValueAndRemovesItOnHit() {
        Map<String, String> map = new HashMap<>();
        map.put("color", "red");
        map.put("width", "auto");

        assertEquals("red", XmlTools.getFromMap(map, "color", "fallback"));
        // The consumed entry is removed so a second read falls back.
        assertFalse(map.containsKey("color"));
        assertEquals("fallback", XmlTools.getFromMap(map, "color", "fallback"));
        // Untouched entries survive.
        assertTrue(map.containsKey("width"));
    }

    @Test
    public void getFromMapReturnsDefaultForMissingKey() {
        Map<String, String> map = new HashMap<>();
        map.put("a", "b");
        assertEquals("def", XmlTools.getFromMap(map, "missing", "def"));
        // A miss must not disturb the map.
        assertTrue(map.containsKey("a"));
    }

    @Test
    public void getFromMapNullMapReturnsDefault() {
        assertEquals("def", XmlTools.getFromMap(null, "color", "def"));
    }

    @Test
    public void getFromMapTwoArgOverloadDefaultsToEmptyString() {
        Map<String, String> map = new HashMap<>();
        map.put("k", "v");
        assertEquals("v", XmlTools.getFromMap(map, "k"));
        // Now removed -> empty-string default.
        assertEquals("", XmlTools.getFromMap(map, "k"));
        assertEquals("", XmlTools.getFromMap(null, "anything"));
    }

    /* ---------------------- scalar setter guards ------------------------- */

    @Test
    public void scalarSettersReturnFalseForNull() {
        assertFalse(XmlTools.setPropAsDouble(UID, PROP, null));
        assertFalse(XmlTools.setPropAsBoolean(UID, PROP, null));
        assertFalse(XmlTools.setPropAsInteger(UID, PROP, null));
        assertFalse(XmlTools.setPropAsString(UID, PROP, null));
    }

    @Test
    public void scalarSettersReturnFalseForEmptyString() {
        assertFalse(XmlTools.setPropAsDouble(UID, PROP, ""));
        assertFalse(XmlTools.setPropAsBoolean(UID, PROP, ""));
        assertFalse(XmlTools.setPropAsInteger(UID, PROP, ""));
        assertFalse(XmlTools.setPropAsString(UID, PROP, ""));
    }

    /* ---------------------- vector setter guards ------------------------- */

    @Test
    public void vectorSettersReturnFalseForNullOrEmptyString() {
        assertFalse(XmlTools.setPropAsDoubleVector(UID, PROP, (String) null, 3));
        assertFalse(XmlTools.setPropAsDoubleVector(UID, PROP, "", 3));
        assertFalse(XmlTools.setPropAsBooleanVector(UID, PROP, null, 3));
        assertFalse(XmlTools.setPropAsBooleanVector(UID, PROP, "", 3));
        assertFalse(XmlTools.setPropAsIntegerVector(UID, PROP, null, 3));
        assertFalse(XmlTools.setPropAsIntegerVector(UID, PROP, "", 3));
        assertFalse(XmlTools.setPropAsStringVector(UID, PROP, null, 3));
        assertFalse(XmlTools.setPropAsStringVector(UID, PROP, "", 3));
    }

    @Test
    public void vectorSettersReturnFalseWhenTokenCountExceedsDeclaredSize() {
        // "1,2,3" is three tokens but the declared size is two -> reject before
        // any parsing or controller call.
        assertFalse(XmlTools.setPropAsDoubleVector(UID, PROP, "1,2,3", 2));
        assertFalse(XmlTools.setPropAsIntegerVector(UID, PROP, "1,2,3", 2));
        assertFalse(XmlTools.setPropAsBooleanVector(UID, PROP, "true,false,true", 2));
        assertFalse(XmlTools.setPropAsStringVector(UID, PROP, "a,b,c", 2));
    }

    @Test
    public void doubleVectorFromMapReturnsFalseForNullOrEmptyKeyArray() {
        Map<String, String> map = new HashMap<>();
        assertFalse(XmlTools.setPropAsDoubleVector(UID, PROP, map, (String[]) null));
        assertFalse(XmlTools.setPropAsDoubleVector(UID, PROP, map, new String[0]));
    }
}
