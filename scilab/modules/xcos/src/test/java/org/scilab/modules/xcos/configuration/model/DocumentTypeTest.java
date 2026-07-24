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

package org.scilab.modules.xcos.configuration.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for the JAXB value class {@link DocumentType}.
 *
 * <p>
 * {@code DocumentType} is a generated plain-old-data holder with five
 * independent properties, each a trivial getter/setter pair. Its only
 * non-{@link String} property is a {@link XMLGregorianCalendar}, which lives in
 * the JDK's {@code java.xml} module, so these tests need neither the Scilab
 * native runtime nor a JAXB implementation.
 */
public class DocumentTypeTest {

    @Test
    public void freshInstanceHasAllNullProperties() {
        DocumentType d = new DocumentType();
        assertNull(d.getUrl());
        assertNull(d.getDate());
        assertNull(d.getPath());
        assertNull(d.getUuid());
        assertNull(d.getViewport());
    }

    @Test
    public void urlRoundTrips() {
        DocumentType d = new DocumentType();
        d.setUrl("file:///tmp/a.xcos");
        assertEquals("file:///tmp/a.xcos", d.getUrl());
    }

    @Test
    public void pathRoundTrips() {
        DocumentType d = new DocumentType();
        d.setPath("/tmp/a.xcos");
        assertEquals("/tmp/a.xcos", d.getPath());
    }

    @Test
    public void uuidRoundTrips() {
        DocumentType d = new DocumentType();
        d.setUuid("01234567-89ab-cdef-0123-456789abcdef");
        assertEquals("01234567-89ab-cdef-0123-456789abcdef", d.getUuid());
    }

    @Test
    public void viewportRoundTrips() {
        DocumentType d = new DocumentType();
        d.setViewport("0 0 1100 800");
        assertEquals("0 0 1100 800", d.getViewport());
    }

    @Test
    public void dateRoundTrips() throws Exception {
        DocumentType d = new DocumentType();
        XMLGregorianCalendar cal = DatatypeFactory.newInstance()
                .newXMLGregorianCalendar("2011-12-01T15:54:28");
        d.setDate(cal);
        assertSame(cal, d.getDate());
        assertEquals(cal, d.getDate());
    }

    @Test
    public void settersOverwritePreviousValue() {
        DocumentType d = new DocumentType();
        d.setUrl("first");
        d.setUrl("second");
        assertEquals("second", d.getUrl());
    }

    @Test
    public void settersAcceptNullToClearAValue() {
        DocumentType d = new DocumentType();
        d.setUrl("something");
        d.setUrl(null);
        assertNull(d.getUrl());
    }

    @Test
    public void propertiesAreIndependent() {
        DocumentType d = new DocumentType();
        d.setUrl("u");
        d.setPath("p");
        d.setUuid("id");
        d.setViewport("v");

        assertEquals("u", d.getUrl());
        assertEquals("p", d.getPath());
        assertEquals("id", d.getUuid());
        assertEquals("v", d.getViewport());
        // date was never touched by the other setters
        assertNull(d.getDate());
    }
}
