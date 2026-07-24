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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import javax.xml.xpath.XPathFactory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link ScilabXPathFactory}.
 *
 * <p>The save/restore helpers are pure {@code System} property manipulation and are
 * asserted precisely. {@code newInstance()} has a fallback branch (it retries with the
 * platform default if the explicitly-named internal impl cannot be constructed), so it is
 * only asserted to return a usable factory — its exact property side effects depend on
 * whether the JDK can instantiate the internal impl by name and are not pinned here. The
 * global property is snapshotted and restored around every test.
 */
public class ScilabXPathFactoryTest {

    private static final String PROP = "javax.xml.xpath.XPathFactory";
    private static final String INTERNAL_IMPL =
        "com.sun.org.apache.xpath.internal.jaxp.XPathFactoryImpl";

    private String saved;

    @BeforeEach
    public void snapshot() {
        saved = System.getProperty(PROP);
    }

    @AfterEach
    public void restore() {
        if (saved == null) {
            System.clearProperty(PROP);
        } else {
            System.setProperty(PROP, saved);
        }
    }

    @Test
    public void useDefaultSelectsInternalImplAndReturnsThePreviousValue() {
        System.setProperty(PROP, "com.example.XPath");
        String previous = ScilabXPathFactory.useDefaultTransformerFactoryImpl();
        assertEquals("com.example.XPath", previous);
        assertEquals(INTERNAL_IMPL, System.getProperty(PROP));
    }

    @Test
    public void useDefaultReturnsNullWhenNoFactoryWasConfigured() {
        System.clearProperty(PROP);
        String previous = ScilabXPathFactory.useDefaultTransformerFactoryImpl();
        assertNull(previous);
        assertEquals(INTERNAL_IMPL, System.getProperty(PROP));
    }

    @Test
    public void restoreWithNullClearsTheProperty() {
        System.setProperty(PROP, "x");
        ScilabXPathFactory.restoreTransformerFactoryImpl(null);
        assertNull(System.getProperty(PROP));
    }

    @Test
    public void restoreWithAValueReinstatesIt() {
        ScilabXPathFactory.restoreTransformerFactoryImpl("com.example.Kept");
        assertEquals("com.example.Kept", System.getProperty(PROP));
    }

    @Test
    public void useThenRestoreRoundTripsAConfiguredValue() {
        System.setProperty(PROP, "com.example.Original");
        String previous = ScilabXPathFactory.useDefaultTransformerFactoryImpl();
        ScilabXPathFactory.restoreTransformerFactoryImpl(previous);
        assertEquals("com.example.Original", System.getProperty(PROP));
    }

    @Test
    public void newInstanceReturnsAUsableFactory() {
        XPathFactory factory = ScilabXPathFactory.newInstance();
        assertNotNull(factory);
        // Sanity: the returned factory can actually compile an XPath expression.
        assertNotNull(factory.newXPath());
    }
}
