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
package org.scilab.modules.xcos.io.spec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link ContentEntry}.
 *
 * <p>{@code ContentEntry} is the {@link Entry} that reads/writes the
 * {@code content.xml} member of an {@code .xcos} (ODF-style ZIP) package. The
 * media-type / full-path pair is a <em>file-format contract</em>: {@code
 * XcosPackage.load} routes a ZIP entry to this handler by string-matching
 * {@link ContentEntry#getFullPath()}, so a change to either constant silently
 * changes what gets loaded. These tests pin that contract.</p>
 *
 * <p>The remaining surface is not hermetically unit-testable and is covered by
 * higher-level integration tests instead: {@code setup(XcosPackage)} needs an
 * {@code XcosPackage}, whose constructor calls a native ({@code
 * ScilabCommonsJNI}) version timestamp; {@code load}/{@code store} pull in
 * {@code XcosSAXHandler}/{@code XcosWriter} over a live {@code XcosDiagram}.
 * None of that can run without the Scilab native runtime, so it is skipped
 * here.</p>
 */
public class ContentEntryTest {

    @Test
    @DisplayName("getMediaType() is the fixed text/xml media type")
    public void mediaTypeIsTextXml() {
        assertEquals("text/xml", new ContentEntry().getMediaType());
    }

    @Test
    @DisplayName("getFullPath() is the fixed content.xml package member")
    public void fullPathIsContentXml() {
        assertEquals("content.xml", new ContentEntry().getFullPath());
    }

    @Test
    @DisplayName("ContentEntry is an Entry")
    public void implementsEntry() {
        assertTrue(Entry.class.isInstance(new ContentEntry()));
    }

    @Test
    @DisplayName("the format identifiers are stable across calls and instances")
    public void identifiersAreConstant() {
        ContentEntry a = new ContentEntry();
        ContentEntry b = new ContentEntry();

        // idempotent on one instance
        assertEquals(a.getMediaType(), a.getMediaType());
        assertEquals(a.getFullPath(), a.getFullPath());
        // identical across independent instances (no per-instance state)
        assertEquals(a.getMediaType(), b.getMediaType());
        assertEquals(a.getFullPath(), b.getFullPath());
    }
}
