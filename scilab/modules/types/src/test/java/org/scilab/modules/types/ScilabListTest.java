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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link ScilabList}: empty vs populated dimensions, the
 * type-tagged serialized form, the Scilab-literal {@code toString}, the
 * collection-copy constructor, and an Externalizable serialization round-trip.
 */
public class ScilabListTest {

    @Test
    public void emptyListHasZeroDimensions() {
        ScilabList list = new ScilabList();
        assertTrue(list.isEmpty());
        assertEquals(0, list.getHeight());
        assertEquals(0, list.getWidth());
        assertEquals("list()", list.toString());
        assertEquals(ScilabTypeEnum.sci_list, list.getType());
        assertFalse(list.isReference());
        assertFalse(list.isSwaped());
    }

    @Test
    public void populatedListHasHeightOneAndWidthSize() {
        ScilabList list = new ScilabList();
        list.add(new ScilabString("hello"));
        list.add(new ScilabDouble(2.0));
        assertFalse(list.isEmpty());
        assertEquals(1, list.getHeight());
        assertEquals(2, list.getWidth());
    }

    @Test
    public void namedConstructorCarriesVarName() {
        ScilabList list = new ScilabList("myList");
        assertEquals("myList", list.getVarName());
        ScilabList sized = new ScilabList("sized", 4);
        assertEquals("sized", sized.getVarName());
        assertTrue(sized.isEmpty());
    }

    @Test
    public void collectionConstructorCopiesElements() {
        ScilabList src = new ScilabList();
        src.add(new ScilabDouble(1.0));
        src.add(new ScilabDouble(2.0));
        ScilabList copy = new ScilabList(src);
        assertEquals(2, copy.size());
        assertEquals(src, copy);
    }

    @Test
    public void serializedObjectCarriesTypeTagsThenItems() {
        ScilabList list = new ScilabList();
        list.add(new ScilabString("a"));
        list.add(new ScilabDouble(3.0));
        Object[] ser = list.getSerializedObject();
        // items[0] is the int[] of swig type codes; then one entry per element.
        assertEquals(3, ser.length);
        int[] types = (int[]) ser[0];
        assertArrayEquals(new int[] {ScilabTypeEnum.sci_strings.swigValue(), ScilabTypeEnum.sci_matrix.swigValue()}, types);
    }

    @Test
    public void toStringWrapsElementsInListLiteral() {
        ScilabList list = new ScilabList();
        list.add(new ScilabDouble(1.0));
        list.add(new ScilabDouble(2.0));
        assertEquals("list(1, 2)", list.toString());
    }

    @Test
    public void serializationRoundTrip() throws Exception {
        ScilabList original = new ScilabList("v");
        original.add(new ScilabString("x"));
        original.add(new ScilabDouble(7.0));

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(original);
        }
        ScilabList restored;
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()))) {
            restored = (ScilabList) ois.readObject();
        }
        assertEquals(2, restored.size());
        assertEquals(original, restored);
        assertEquals("v", restored.getVarName());
    }

    @Test
    public void listEqualityIsContentBased() {
        ScilabList a = new ScilabList(Arrays.asList(new ScilabDouble(1.0)));
        ScilabList b = new ScilabList(Arrays.asList(new ScilabDouble(1.0)));
        assertEquals(a, b);
    }
}
