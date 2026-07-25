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
 * Hermetic unit tests for {@link ScilabMList}: the field-name header (first
 * element), the field map / type accessors, dimensions, {@code toString}, and a
 * serialization round-trip.
 */
public class ScilabMListTest {

    @Test
    public void emptyMListHasZeroDimensionsAndNullType() {
        ScilabMList m = new ScilabMList();
        assertTrue(m.isEmpty());
        assertEquals(0, m.getHeight());
        assertEquals(0, m.getWidth());
        assertEquals("mlist()", m.toString());
        assertEquals(ScilabTypeEnum.sci_mlist, m.getType());
        assertNull(m.getMListType());
        assertTrue(m.getMListFields().isEmpty());
        assertFalse(m.isReference());
        assertFalse(m.isSwaped());
    }

    @Test
    public void headerConstructorSetsTypeAndUnfilledFields() {
        // The header names the type ("myType") and two fields ("a","b"); no values yet.
        ScilabMList m = new ScilabMList(new String[] {"myType", "a", "b"});
        assertEquals("myType", m.getMListType());
        assertEquals(1, m.size());
        assertEquals(1, m.getWidth());

        Map<String, ScilabType> fields = m.getMListFields();
        assertEquals(2, fields.size());
        assertTrue(fields.containsKey("a"));
        assertTrue(fields.containsKey("b"));
        // Not yet supplied, so mapped to null.
        assertNull(fields.get("a"));
        assertNull(fields.get("b"));
    }

    @Test
    public void fieldsMapToSuppliedValues() {
        ScilabMList m = new ScilabMList(new String[] {"myType", "a", "b"});
        ScilabDouble va = new ScilabDouble(1.0);
        ScilabString vb = new ScilabString("x");
        m.add(va);
        m.add(vb);
        assertEquals(3, m.size());

        Map<String, ScilabType> fields = m.getMListFields();
        assertEquals(va, fields.get("a"));
        assertEquals(vb, fields.get("b"));
    }

    @Test
    public void collectionConstructorPrependsHeader() {
        ScilabMList m = new ScilabMList(new String[] {"myType", "a"},
                                        Arrays.asList(new ScilabDouble(42.0)));
        // Header + one value.
        assertEquals(2, m.size());
        assertEquals("myType", m.getMListType());
        assertEquals(new ScilabDouble(42.0), m.getMListFields().get("a"));
    }

    @Test
    public void namedConstructorCarriesVarName() {
        ScilabMList m = new ScilabMList("mv");
        assertEquals("mv", m.getVarName());
        ScilabMList sized = new ScilabMList("mv2", 3);
        assertEquals("mv2", sized.getVarName());
    }

    @Test
    public void toStringWrapsInMListLiteral() {
        ScilabMList m = new ScilabMList();
        m.add(new ScilabDouble(1.0));
        assertEquals("mlist(1)", m.toString());
    }

    @Test
    public void serializationRoundTrip() throws Exception {
        ScilabMList original = new ScilabMList(new String[] {"myType", "a"});
        original.add(new ScilabDouble(9.0));

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(original);
        }
        ScilabMList restored;
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()))) {
            restored = (ScilabMList) ois.readObject();
        }
        assertEquals(original, restored);
        assertEquals("myType", restored.getMListType());
    }
}
