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

package org.scilab.modules.types;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link ScilabTList}: the type header (first element),
 * the field map / type accessors, dimensions, {@code toString}, and a
 * serialization round-trip.
 */
public class ScilabTListTest {

    @Test
    public void emptyTListHasZeroDimensionsAndNullType() {
        ScilabTList t = new ScilabTList();
        assertTrue(t.isEmpty());
        assertEquals(0, t.getHeight());
        assertEquals(0, t.getWidth());
        assertEquals("tlist()", t.toString());
        assertEquals(ScilabTypeEnum.sci_tlist, t.getType());
        assertNull(t.getTListType());
        assertTrue(t.getTListFields().isEmpty());
        assertFalse(t.isReference());
        assertFalse(t.isSwaped());
    }

    @Test
    public void headerConstructorSetsTypeAndUnfilledFields() {
        ScilabTList t = new ScilabTList(new String[] {"myType", "a", "b"});
        assertEquals("myType", t.getTListType());
        assertEquals(1, t.size());
        assertEquals(1, t.getHeight());

        Map<String, ScilabType> fields = t.getTListFields();
        assertEquals(2, fields.size());
        assertNull(fields.get("a"));
        assertNull(fields.get("b"));
    }

    @Test
    public void fieldsMapToSuppliedValues() {
        ScilabTList t = new ScilabTList(new String[] {"myType", "a", "b"});
        ScilabDouble va = new ScilabDouble(1.0);
        ScilabString vb = new ScilabString("x");
        t.add(va);
        t.add(vb);

        Map<String, ScilabType> fields = t.getTListFields();
        assertEquals(va, fields.get("a"));
        assertEquals(vb, fields.get("b"));
    }

    @Test
    public void collectionConstructorPrependsHeader() {
        ScilabTList t = new ScilabTList(new String[] {"myType", "a"},
                                        Arrays.asList(new ScilabDouble(42.0)));
        assertEquals(2, t.size());
        assertEquals("myType", t.getTListType());
        assertEquals(new ScilabDouble(42.0), t.getTListFields().get("a"));
    }

    @Test
    public void namedConstructorCarriesVarName() {
        ScilabTList t = new ScilabTList("tv");
        assertEquals("tv", t.getVarName());
        ScilabTList sized = new ScilabTList("tv2", 3);
        assertEquals("tv2", sized.getVarName());
    }

    @Test
    public void toStringWrapsInTListLiteral() {
        ScilabTList t = new ScilabTList();
        t.add(new ScilabDouble(1.0));
        assertEquals("tlist(1)", t.toString());
    }

    @Test
    public void serializationRoundTrip() throws Exception {
        ScilabTList original = new ScilabTList(new String[] {"myType", "a"});
        original.add(new ScilabDouble(9.0));

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(original);
        }
        ScilabTList restored;
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()))) {
            restored = (ScilabTList) ois.readObject();
        }
        assertEquals(original, restored);
        assertEquals("myType", restored.getTListType());
    }
}
