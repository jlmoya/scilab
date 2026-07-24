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

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link DocbookElement}, an immutable-ish tag holder.
 *
 * <p>The constructor is {@code protected}; this test lives in the same package
 * ({@code org.scilab.modules.helptools}) so it can construct instances directly.
 * The base class deliberately implements {@code append}/{@code get}/{@code setParent}/
 * {@code getParent} as no-ops/nulls (subclasses override) — that contract is pinned here.
 */
public class DocbookElementTest {

    private static Map<String, String> attrs(String k, String v) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(k, v);
        return m;
    }

    @Test
    public void gettersReturnConstructorArguments() {
        Map<String, String> a = attrs("role", "bold");
        DocbookElement e = new DocbookElement("para", "http://uri", a);

        assertEquals("para", e.getName());
        assertEquals("http://uri", e.getURI());
        assertSame(a, e.getAttributes(), "getAttributes exposes the very same map instance");
    }

    @Test
    public void stringBuilderStartsEmptyAndIsReplaceable() {
        DocbookElement e = new DocbookElement("para", "u", attrs("k", "v"));

        StringBuilder initial = e.getStringBuilder();
        assertNotNull(initial);
        assertEquals(0, initial.length());

        StringBuilder replacement = new StringBuilder("contents");
        e.setStringBuilder(replacement);
        assertSame(replacement, e.getStringBuilder());
    }

    @Test
    public void baseAppendAndGetAreNoOps() {
        DocbookElement e = new DocbookElement("para", "u", attrs("k", "v"));
        // Base class stores nothing; get() is always null (subclasses add behavior).
        e.append("ignored");
        assertNull(e.get());
    }

    @Test
    public void baseParentAccessorsAreNoOps() {
        DocbookElement e = new DocbookElement("para", "u", attrs("k", "v"));
        DocbookElement parent = new DocbookElement("root", "u", attrs("k", "v"));
        e.setParent(parent); // no-op in the base class
        assertNull(e.getParent());
    }

    @Test
    public void getNewInstanceProducesADistinctDocbookElement() {
        DocbookElement proto = new DocbookElement("para", "u", attrs("k", "v"));
        Map<String, String> a2 = attrs("id", "x");
        DocbookElement made = proto.getNewInstance("chapter", "u2", a2);

        assertNotSame(proto, made);
        assertEquals(DocbookElement.class, made.getClass());
        assertEquals("chapter", made.getName());
        assertEquals("u2", made.getURI());
        assertSame(a2, made.getAttributes());
    }

    @Test
    public void toStringRendersNameAndAttributes() {
        DocbookElement e = new DocbookElement("para", "u", attrs("role", "bold"));
        assertEquals("<para>:{role=bold}", e.toString());
    }
}
