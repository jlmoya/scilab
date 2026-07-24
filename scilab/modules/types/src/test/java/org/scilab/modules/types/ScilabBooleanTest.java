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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link ScilabBoolean}.
 */
public class ScilabBooleanTest {

    @Test
    public void scalarConstructor() {
        ScilabBoolean b = new ScilabBoolean(true);
        assertEquals(1, b.getHeight());
        assertEquals(1, b.getWidth());
        assertTrue(b.getElement(0, 0));
        assertFalse(b.isEmpty());
        assertEquals(ScilabTypeEnum.sci_boolean, b.getType());
        assertFalse(b.isReference());
        assertFalse(b.isSwaped());
    }

    @Test
    public void defaultConstructorIsEmpty() {
        ScilabBoolean b = new ScilabBoolean();
        assertTrue(b.isEmpty());
        assertEquals(0, b.getHeight());
        assertEquals(0, b.getWidth());
        assertEquals("[]", b.toString());
    }

    @Test
    public void matrixConstructorAndElementAccess() {
        boolean[][] data = {{true, false}, {false, true}};
        ScilabBoolean b = new ScilabBoolean(data);
        assertEquals(2, b.getHeight());
        assertEquals(2, b.getWidth());
        assertTrue(b.getElement(0, 0));
        assertFalse(b.getElement(0, 1));
        assertSame(data, b.getRawData());
    }

    @Test
    public void setElementAndSetDataMutate() {
        ScilabBoolean b = new ScilabBoolean(new boolean[][] {{true}});
        b.setElement(0, 0, false);
        assertFalse(b.getElement(0, 0));

        b.setData(new boolean[][] {{false, true, false}});
        assertEquals(1, b.getHeight());
        assertEquals(3, b.getWidth());
        assertTrue(b.getElement(0, 1));
    }

    @Test
    public void namedConstructorCarriesVarNameAndSwap() {
        ScilabBoolean b = new ScilabBoolean("flag", new boolean[][] {{true}}, true);
        assertEquals("flag", b.getVarName());
        assertTrue(b.isSwaped());
    }

    @Test
    public void equalsAndHashCode() {
        ScilabBoolean a = new ScilabBoolean(new boolean[][] {{true, false}, {false, true}});
        ScilabBoolean b = new ScilabBoolean(new boolean[][] {{true, false}, {false, true}});
        ScilabBoolean c = new ScilabBoolean(new boolean[][] {{true, true}, {false, true}});
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }

    @Test
    public void twoEmptyBooleansAreEqualButSizeMismatchIsNot() {
        assertEquals(new ScilabBoolean(), new ScilabBoolean());

        ScilabBoolean row = new ScilabBoolean(new boolean[][] {{true, true}});
        ScilabBoolean col = new ScilabBoolean(new boolean[][] {{true}, {true}});
        assertNotEquals(row, col);
        assertNotEquals(row, Boolean.TRUE);
    }

    @Test
    public void toStringRendersScilabBooleanLiteral() {
        ScilabBoolean b = new ScilabBoolean(new boolean[][] {{true, false}, {false, true}});
        assertEquals("[%t, %f ; %f, %t]", b.toString());
    }

    @Test
    public void toStringScalar() {
        assertEquals("[%t]", new ScilabBoolean(true).toString());
        assertEquals("[%f]", new ScilabBoolean(false).toString());
    }
}
