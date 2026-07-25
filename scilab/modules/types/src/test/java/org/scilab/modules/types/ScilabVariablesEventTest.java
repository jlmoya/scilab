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

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for the {@link ScilabVariablesEvent} value holder.
 */
public class ScilabVariablesEventTest {

    @Test
    public void eventExposesTheWrappedVariable() {
        ScilabDouble var = new ScilabDouble(3.0);
        ScilabVariablesEvent event = new ScilabVariablesEvent(var);
        assertSame(var, event.getScilabType());
    }

    @Test
    public void eventAcceptsNullVariable() {
        ScilabVariablesEvent event = new ScilabVariablesEvent(null);
        assertNull(event.getScilabType());
    }
}
