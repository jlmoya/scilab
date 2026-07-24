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

package org.scilab.modules.scinotes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Hermetic unit tests for {@link TabManager.Tabulation}, the plain data holder
 * describing one tabulation setting (the whitespace char, its width in spaces, the
 * view-representation code and the display char).
 *
 * <p>Only the explicit four-argument constructor is exercised: the no-argument
 * constructor deliberately reads the live SciNotes display preferences (a
 * configuration document), which is out of scope for a hermetic test.
 */
public class TabManagerTest {

    @Test
    public void fourArgConstructorAssignsEveryField() {
        TabManager.Tabulation t = new TabManager.Tabulation('\t', 4, 2, '>');
        assertEquals('\t', t.tab);
        assertEquals(4, t.number);
        assertEquals(2, t.type);
        assertEquals('>', t.rep);
    }

    @Test
    public void spaceBasedTabulationIsStoredVerbatim() {
        TabManager.Tabulation t = new TabManager.Tabulation(' ', 3, 0, ' ');
        assertEquals(' ', t.tab);
        assertEquals(3, t.number);
        assertEquals(0, t.type);
        assertEquals(' ', t.rep);
    }

    @Test
    public void fieldsAreIndependentlyReassignable() {
        // The fields are public and mutable (used directly by the tab machinery).
        TabManager.Tabulation t = new TabManager.Tabulation('\t', 8, 1, '|');
        t.tab = ' ';
        t.number = 2;
        t.type = 4;
        t.rep = '.';
        assertEquals(' ', t.tab);
        assertEquals(2, t.number);
        assertEquals(4, t.type);
        assertEquals('.', t.rep);
    }

    @Test
    public void distinctInstancesDoNotShareState() {
        TabManager.Tabulation a = new TabManager.Tabulation('\t', 4, 1, '>');
        TabManager.Tabulation b = new TabManager.Tabulation(' ', 2, 0, ' ');
        assertNotEquals(a.tab, b.tab);
        assertNotEquals(a.number, b.number);
        // Mutating one leaves the other untouched.
        a.number = 99;
        assertEquals(2, b.number);
    }

    @Test
    public void constructorDoesNotValidateArguments() {
        // Nonsensical values are accepted as-is; the holder performs no validation.
        TabManager.Tabulation t = new TabManager.Tabulation('x', -5, 999, '\0');
        assertEquals('x', t.tab);
        assertEquals(-5, t.number);
        assertEquals(999, t.type);
        assertEquals('\0', t.rep);
    }
}
