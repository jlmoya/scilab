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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link ScilabBooleanSparse}: the compressed "true cells"
 * representation, scalar / matrix / checked constructors, dense reconstruction,
 * equality, the Scilab-literal {@code toString}, and a serialization round-trip.
 */
public class ScilabBooleanSparseTest {

    @Test
    public void defaultConstructorIsEmpty() {
        ScilabBooleanSparse s = new ScilabBooleanSparse();
        assertTrue(s.isEmpty());
        assertEquals(0, s.getHeight());
        assertEquals(0, s.getWidth());
        assertEquals(0, s.getNbNonNullItems());
        assertEquals("[]", s.toString());
        assertEquals(ScilabTypeEnum.sci_boolean_sparse, s.getType());
        assertFalse(s.isReference());
        assertFalse(s.isSwaped());
    }

    @Test
    public void trueScalarConstructor() {
        ScilabBooleanSparse s = new ScilabBooleanSparse(true);
        assertFalse(s.isEmpty());
        assertEquals(1, s.getHeight());
        assertEquals(1, s.getWidth());
        assertEquals(1, s.getNbNonNullItems());
        assertArrayEquals(new int[] {1}, s.getNbItemRow());
        assertArrayEquals(new int[] {0}, s.getColPos());
        assertArrayEquals(new int[] {1}, s.getScilabColPos());
    }

    @Test
    public void falseScalarConstructorStaysEmpty() {
        ScilabBooleanSparse s = new ScilabBooleanSparse(false);
        assertTrue(s.isEmpty());
        assertEquals(0, s.getNbNonNullItems());
    }

    @Test
    public void matrixConstructorBuildsCompressedForm() {
        // {{T,F,T},{F,T,F}} -> nbItemRow [2,1], colPos [0,2,1], 3 true cells.
        boolean[][] data = {{true, false, true}, {false, true, false}};
        ScilabBooleanSparse s = new ScilabBooleanSparse(data);
        assertEquals(2, s.getHeight());
        assertEquals(3, s.getWidth());
        assertEquals(3, s.getNbNonNullItems());
        assertArrayEquals(new int[] {2, 1}, s.getNbItemRow());
        assertArrayEquals(new int[] {0, 2, 1}, s.getColPos());
    }

    @Test
    public void getFullMatrixReconstructsDenseBooleans() {
        boolean[][] data = {{true, false, true}, {false, true, false}};
        ScilabBooleanSparse s = new ScilabBooleanSparse(data);
        boolean[][] full = s.getFullMatrix();
        assertArrayEquals(new boolean[] {true, false, true}, full[0]);
        assertArrayEquals(new boolean[] {false, true, false}, full[1]);
    }

    @Test
    public void checkedConstructorAcceptsValidRepresentation() throws ScilabSparseException {
        ScilabBooleanSparse s = new ScilabBooleanSparse(2, 2, 2, new int[] {1, 1}, new int[] {0, 1}, true);
        assertEquals(2, s.getNbNonNullItems());
        boolean[][] full = s.getFullMatrix();
        assertTrue(full[0][0]);
        assertTrue(full[1][1]);
        assertFalse(full[0][1]);
    }

    @Test
    public void checkedConstructorRejectsInvalidRepresentation() {
        // nbItem (3) exceeds rows*cols (4)? no - use column out of range instead.
        assertThrows(ScilabSparseException.class, () ->
                     new ScilabBooleanSparse(2, 2, 1, new int[] {1, 0}, new int[] {5}, true));
    }

    @Test
    public void settersMutateCompressedArrays() {
        ScilabBooleanSparse s = new ScilabBooleanSparse(true);
        s.setNbNonNullItems(1);
        assertEquals(1, s.getNbNonNullItems());
        s.setColPos(new int[] {0});
        assertArrayEquals(new int[] {0}, s.getColPos());
        s.setNbItemRow(new int[] {1});
        assertArrayEquals(new int[] {1}, s.getNbItemRow());
    }

    @Test
    public void serializedBooleanSparseMatrixIsEmptyStub() {
        // Documented behaviour: the serialized-boolean helper is a TODO stub.
        assertEquals(0, new ScilabBooleanSparse(true).getSerializedBooleanSparseMatrix().length);
    }

    @Test
    public void getSerializedObjectHasThreeComponents() {
        Object[] ser = (Object[]) new ScilabBooleanSparse(true).getSerializedObject();
        assertEquals(3, ser.length);
    }

    @Test
    public void equalsAndHashCode() {
        ScilabBooleanSparse a = new ScilabBooleanSparse(new boolean[][] {{true, false}, {false, true}});
        ScilabBooleanSparse b = new ScilabBooleanSparse(new boolean[][] {{true, false}, {false, true}});
        ScilabBooleanSparse c = new ScilabBooleanSparse(new boolean[][] {{true, true}, {false, true}});
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
        assertNotEquals(a, "not a boolean sparse");
    }

    @Test
    public void namedConstructorCarriesVarName() {
        ScilabBooleanSparse s = new ScilabBooleanSparse("bs", 1, 1, 1, new int[] {1}, new int[] {0});
        assertEquals("bs", s.getVarName());
    }

    @Test
    public void toStringScalar() {
        assertEquals("sparse([1, 1], [%t], [1, 1])", new ScilabBooleanSparse(true).toString());
    }

    @Test
    public void serializationRoundTrip() throws Exception {
        ScilabBooleanSparse original = new ScilabBooleanSparse("v", 2, 2, 2, new int[] {1, 1}, new int[] {0, 1});
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(original);
        }
        ScilabBooleanSparse restored;
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()))) {
            restored = (ScilabBooleanSparse) ois.readObject();
        }
        assertEquals(original, restored);
        assertEquals("v", restored.getVarName());
        assertEquals(2, restored.getNbNonNullItems());
    }
}
