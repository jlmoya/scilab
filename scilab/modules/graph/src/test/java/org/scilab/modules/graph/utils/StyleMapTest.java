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

package org.scilab.modules.graph.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link StyleMap}, the pure key=value;... style
 * string parser/serializer used across the graph module.
 */
public class StyleMapTest {

    @Test
    public void isALinkedHashMap() {
        // Contract relied upon by every consumer: insertion order is preserved.
        assertTrue(new StyleMap("") instanceof LinkedHashMap);
    }

    @Test
    public void emptyStringYieldsEmptyMap() {
        StyleMap map = new StyleMap("");
        assertTrue(map.isEmpty());
        assertEquals("", map.toString());
    }

    @Test
    public void nullStringYieldsEmptyMap() {
        // putAll(null) must be a no-op (guarded by the length check).
        StyleMap map = new StyleMap(null);
        assertTrue(map.isEmpty());
        assertEquals("", map.toString());
    }

    @Test
    public void singleKeyValue() {
        StyleMap map = new StyleMap("a=b");
        assertEquals(1, map.size());
        assertEquals("b", map.get("a"));
        assertEquals("a=b", map.toString());
    }

    @Test
    public void multipleKeyValuesPreserveInsertionOrder() {
        StyleMap map = new StyleMap("a=1;b=2;c=3");
        assertEquals(3, map.size());
        assertEquals("1", map.get("a"));
        assertEquals("2", map.get("b"));
        assertEquals("3", map.get("c"));

        List<String> keys = new ArrayList<String>(map.keySet());
        assertEquals(List.of("a", "b", "c"), keys);
        assertEquals("a=1;b=2;c=3", map.toString());
    }

    @Test
    public void trailingSemicolonIsIgnored() {
        // String.split(";") drops trailing empty tokens, so a final ';' is a no-op.
        StyleMap map = new StyleMap("a=1;b=2;");
        assertEquals(2, map.size());
        assertEquals("a=1;b=2", map.toString());
    }

    @Test
    public void duplicateKeyKeepsLastValue() {
        StyleMap map = new StyleMap("a=1;a=2");
        assertEquals(1, map.size());
        assertEquals("2", map.get("a"));
        assertEquals("a=2", map.toString());
    }

    @Test
    public void tokenWithoutEqualsIsInheritedStyleNotAKey() {
        // "BlockName" has no '=' : it becomes the (single) inherited style,
        // it is NOT stored as a map key.
        StyleMap map = new StyleMap("key=value;BlockName");
        assertEquals(1, map.size());
        assertTrue(map.containsKey("key"));
        assertFalse(map.containsKey("BlockName"));
        assertNull(map.get("BlockName"));
    }

    @Test
    public void inheritedStyleIsSerializedFirst() {
        // The inherited token is emitted before the key/value pairs regardless
        // of its position in the source string.
        StyleMap map = new StyleMap("key=value;BlockName");
        assertEquals("BlockName;key=value", map.toString());
    }

    @Test
    public void inheritedStyleOnlyRoundTrips() {
        StyleMap map = new StyleMap("BlockName");
        assertTrue(map.isEmpty());
        assertEquals("BlockName", map.toString());
        // re-parsing the serialized form is stable
        assertEquals("BlockName", new StyleMap(map.toString()).toString());
    }

    @Test
    public void inheritedFirstThenKeyRoundTripsStably() {
        String src = "BlockName;key=value";
        StyleMap map = new StyleMap(src);
        assertEquals(1, map.size());
        assertEquals("value", map.get("key"));
        assertEquals(src, map.toString());
    }

    @Test
    public void putAllReturnsSameInstanceAndMutates() {
        StyleMap map = new StyleMap("a=1");
        StyleMap returned = map.putAll("b=2");
        assertSame(map, returned);
        assertEquals(2, map.size());
        assertEquals("2", map.get("b"));
    }

    @Test
    public void valueContainingNoContentSerializesAsBareKey() {
        // "flip=" stores key "flip" -> "" ; toString omits the '=' for empty values.
        StyleMap map = new StyleMap("flip=");
        assertEquals(1, map.size());
        assertEquals("", map.get("flip"));
        assertEquals("flip", map.toString());
    }

    @Test
    public void emptyValueDoesNotRoundTrip_defectCharacterization() {
        // Documented asymmetry: a key with an empty value serializes to a bare
        // token ("flip"), which on re-parse is interpreted as an inherited
        // style rather than a key -> the key is lost.
        StyleMap original = new StyleMap("flip=");
        assertTrue(original.containsKey("flip"));

        StyleMap reparsed = new StyleMap(original.toString());
        assertFalse(reparsed.containsKey("flip"), "empty-valued key survives round-trip");
        assertTrue(reparsed.isEmpty());
    }

    @Test
    public void lastInheritedStyleWins_defectCharacterization() {
        // The javadoc claims "only the first inherited style is supported",
        // but the implementation overwrites, so the LAST bare token wins.
        StyleMap map = new StyleMap("First;Second");
        assertEquals("Second", map.toString());
    }

    @Test
    public void valueMayContainEqualsSign() {
        // Only the first '=' splits key/value ; the remainder is kept verbatim.
        StyleMap map = new StyleMap("expr=a=b=c");
        assertEquals("a=b=c", map.get("expr"));
        assertEquals("expr=a=b=c", map.toString());
    }
}
