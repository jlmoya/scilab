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

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link ScilabTypeEnumDescription#getListTypeDescription},
 * the short-code -&gt; long-name expander. (The sibling
 * {@code getTypeDescriptionFromId} routes through the native localization layer and
 * is intentionally not covered here.)
 */
public class ScilabTypeEnumDescriptionTest {

    @Test
    public void knownShortCodesExpandToFullNames() {
        assertEquals("cell", ScilabTypeEnumDescription.getListTypeDescription("ce"));
        assertEquals("struct", ScilabTypeEnumDescription.getListTypeDescription("st"));
        assertEquals("built-in", ScilabTypeEnumDescription.getListTypeDescription("fptr"));
    }

    @Test
    public void unknownShortCodeIsReturnedUnchanged() {
        assertEquals("mycustomtype", ScilabTypeEnumDescription.getListTypeDescription("mycustomtype"));
        assertEquals("", ScilabTypeEnumDescription.getListTypeDescription(""));
    }
}
