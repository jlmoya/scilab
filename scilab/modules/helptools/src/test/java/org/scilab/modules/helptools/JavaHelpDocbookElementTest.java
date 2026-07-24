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

package org.scilab.modules.helptools;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link JavaHelpDocbookElement}, the subclass that (unlike
 * its {@link DocbookElement} base) actually stores the {@code append}-ed map-id
 * contents and tracks a parent. Constructed directly via the {@code protected}
 * same-package constructor.
 */
public class JavaHelpDocbookElementTest {

    private static Map<String, String> attrs() {
        return new HashMap<>();
    }

    @Test
    public void inheritsBaseGetters() {
        JavaHelpDocbookElement e = new JavaHelpDocbookElement("refentry", "uri", attrs());
        assertEquals("refentry", e.getName());
        assertEquals("uri", e.getURI());
    }

    @Test
    public void appendAccumulatesStringsAndStringBuilders() {
        JavaHelpDocbookElement e = new JavaHelpDocbookElement("refentry", "uri", attrs());

        Object store = e.get();
        assertNotNull(store);
        assertEquals("", store.toString(), "map-id buffer starts empty");

        e.append("a");
        e.append(new StringBuilder("b"));
        assertEquals("ab", e.get().toString());
    }

    @Test
    public void appendIgnoresUnsupportedTypes() {
        JavaHelpDocbookElement e = new JavaHelpDocbookElement("refentry", "uri", attrs());
        e.append("keep");
        e.append(Integer.valueOf(42)); // neither String nor StringBuilder => ignored
        assertEquals("keep", e.get().toString());
    }

    @Test
    public void parentIsStoredAndReturned() {
        JavaHelpDocbookElement e = new JavaHelpDocbookElement("refentry", "uri", attrs());
        DocbookElement parent = new JavaHelpDocbookElement("root", "uri", attrs());

        assertNull(e.getParent(), "no parent set initially");
        e.setParent(parent);
        assertSame(parent, e.getParent());
    }

    @Test
    public void getNewInstanceProducesAnotherJavaHelpElement() {
        JavaHelpDocbookElement proto = new JavaHelpDocbookElement("refentry", "uri", attrs());
        DocbookElement made = proto.getNewInstance("section", "uri2", attrs());

        assertNotSame(proto, made);
        assertInstanceOf(JavaHelpDocbookElement.class, made);
        assertEquals("section", made.getName());
        assertEquals("uri2", made.getURI());
        // Fresh instance has its own empty map-id buffer.
        assertEquals("", made.get().toString());
    }
}
