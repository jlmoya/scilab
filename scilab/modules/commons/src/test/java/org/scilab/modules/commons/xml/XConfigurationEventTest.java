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

package org.scilab.modules.commons.xml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for the {@link XConfigurationEvent} data holder.
 */
public class XConfigurationEventTest {

    @Test
    public void exposesTheExactSetInstanceItWasBuiltWith() {
        Set<String> paths = new HashSet<>();
        paths.add("/root/foo");
        paths.add("/root/bar");

        XConfigurationEvent event = new XConfigurationEvent(paths);

        assertSame(paths, event.getModifiedPaths());
        assertEquals(2, event.getModifiedPaths().size());
        assertTrue(event.getModifiedPaths().contains("/root/foo"));
        assertTrue(event.getModifiedPaths().contains("/root/bar"));
    }

    @Test
    public void preservesAnEmptySet() {
        XConfigurationEvent event = new XConfigurationEvent(Collections.<String>emptySet());
        assertNotNull(event.getModifiedPaths());
        assertTrue(event.getModifiedPaths().isEmpty());
    }

    @Test
    public void acceptsNullPaths() {
        XConfigurationEvent event = new XConfigurationEvent(null);
        assertNull(event.getModifiedPaths());
    }

    @Test
    public void reflectsLaterMutationsBecauseTheReferenceIsShared() {
        Set<String> paths = new HashSet<>();
        XConfigurationEvent event = new XConfigurationEvent(paths);
        assertTrue(event.getModifiedPaths().isEmpty());

        paths.add("/added/after/construction");

        assertEquals(1, event.getModifiedPaths().size());
        assertTrue(event.getModifiedPaths().contains("/added/after/construction"));
    }
}
