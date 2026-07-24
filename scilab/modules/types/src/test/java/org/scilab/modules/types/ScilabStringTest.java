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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link ScilabString}.
 */
public class ScilabStringTest {

    @Test
    public void scalarConstructorHoldsSingleValue() {
        ScilabString s = new ScilabString("hello");
        assertEquals(1, s.getHeight());
        assertEquals(1, s.getWidth());
        assertFalse(s.isEmpty());
        assertEquals("hello", s.getData()[0][0]);
        assertEquals(ScilabTypeEnum.sci_strings, s.getType());
        assertFalse(s.isReference());
        assertFalse(s.isSwaped());
        assertNull(s.getVarName());
    }

    @Test
    public void scalarConstructorRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> new ScilabString((String) null));
    }

    @Test
    public void defaultConstructorIsEmpty() {
        ScilabString s = new ScilabString();
        assertTrue(s.isEmpty());
        assertEquals(0, s.getHeight());
        assertEquals(0, s.getWidth());
        assertEquals("[]", s.toString());
        assertNull(s.getData());
    }

    @Test
    public void rowVectorConstructorProducesOneRow() {
        ScilabString s = new ScilabString(new String[] {"a", "b", "c"});
        assertEquals(1, s.getHeight());
        assertEquals(3, s.getWidth());
        assertEquals("b", s.getData()[0][1]);
    }

    @Test
    public void rowVectorConstructorWithEmptyArrayYieldsSingleEmptyString() {
        ScilabString s = new ScilabString(new String[0]);
        assertEquals(1, s.getHeight());
        assertEquals(1, s.getWidth());
        assertEquals("", s.getData()[0][0]);
        assertFalse(s.isEmpty());
    }

    @Test
    public void namedMatrixConstructorExposesVarNameAndSwap() {
        String[][] data = {{"x", "y"}, {"z", "w"}};
        ScilabString s = new ScilabString("myvar", data, true);
        assertEquals("myvar", s.getVarName());
        assertTrue(s.isSwaped());
        assertEquals(2, s.getHeight());
        assertEquals(2, s.getWidth());
    }

    @Test
    public void setDataReplacesContents() {
        ScilabString s = new ScilabString("old");
        s.setData(new String[][] {{"new1", "new2"}});
        assertEquals(1, s.getHeight());
        assertEquals(2, s.getWidth());
        assertEquals("new2", s.getData()[0][1]);
    }

    @Test
    public void equalsAndHashCodeDependOnlyOnData() {
        ScilabString a = new ScilabString(new String[][] {{"p", "q"}});
        ScilabString b = new ScilabString("someOtherName", new String[][] {{"p", "q"}}, true);
        // equals ignores varName and swaped; only the deep data matters.
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());

        ScilabString c = new ScilabString(new String[][] {{"p", "DIFFERENT"}});
        assertNotEquals(a, c);
        assertNotEquals(a, "p");
        assertNotEquals(a, null);
    }

    @Test
    public void toStringRendersScilabMatrixLiteral() {
        ScilabString s = new ScilabString(new String[][] {{"a", "b"}, {"c", "d"}});
        assertEquals("[\"a\", \"b\" ; \"c\", \"d\"]", s.toString());
    }

    @Test
    public void toStringDoublesEmbeddedQuotes() {
        // Both single and double quotes are collapsed to a doubled double-quote.
        ScilabString s = new ScilabString("a\"b'c");
        assertEquals("[\"a\"\"b\"\"c\"]", s.toString());
    }
}
