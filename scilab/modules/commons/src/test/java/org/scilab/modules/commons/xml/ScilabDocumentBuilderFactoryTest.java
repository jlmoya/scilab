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

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link ScilabDocumentBuilderFactory}.
 *
 * <p>The class exists to force the JDK-internal JAXP implementation while a factory is
 * built, then transparently restore whatever {@code javax.xml.parsers.DocumentBuilderFactory}
 * the caller had. These tests assert that save/restore contract; the global property is
 * snapshotted and restored around every test so nothing leaks into the rest of the suite.
 */
public class ScilabDocumentBuilderFactoryTest {

    private static final String PROP = "javax.xml.parsers.DocumentBuilderFactory";
    private static final String INTERNAL_IMPL =
        "com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl";

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
        System.setProperty(PROP, "com.example.MyFactory");
        String previous = ScilabDocumentBuilderFactory.useDefaultDocumentBuilderFactoryImpl();
        assertEquals("com.example.MyFactory", previous);
        assertEquals(INTERNAL_IMPL, System.getProperty(PROP));
    }

    @Test
    public void useDefaultReturnsNullWhenNoFactoryWasConfigured() {
        System.clearProperty(PROP);
        String previous = ScilabDocumentBuilderFactory.useDefaultDocumentBuilderFactoryImpl();
        assertNull(previous);
        assertEquals(INTERNAL_IMPL, System.getProperty(PROP));
    }

    @Test
    public void restoreWithNullClearsTheProperty() {
        System.setProperty(PROP, "something");
        ScilabDocumentBuilderFactory.restoreDocumentBuilderFactoryImpl(null);
        assertNull(System.getProperty(PROP));
    }

    @Test
    public void restoreWithAValueReinstatesIt() {
        ScilabDocumentBuilderFactory.restoreDocumentBuilderFactoryImpl("com.example.Restored");
        assertEquals("com.example.Restored", System.getProperty(PROP));
    }

    @Test
    public void useThenRestoreRoundTripsAConfiguredValue() {
        System.setProperty(PROP, "com.example.Original");
        String previous = ScilabDocumentBuilderFactory.useDefaultDocumentBuilderFactoryImpl();
        ScilabDocumentBuilderFactory.restoreDocumentBuilderFactoryImpl(previous);
        assertEquals("com.example.Original", System.getProperty(PROP));
    }

    @Test
    public void useThenRestoreRoundTripsAnUnsetProperty() {
        System.clearProperty(PROP);
        String previous = ScilabDocumentBuilderFactory.useDefaultDocumentBuilderFactoryImpl();
        ScilabDocumentBuilderFactory.restoreDocumentBuilderFactoryImpl(previous);
        assertNull(System.getProperty(PROP));
    }

    @Test
    public void newInstanceReturnsAFactoryAndDoesNotLeaveThePropertyMutated() {
        System.setProperty(PROP, "com.example.Sentinel");
        DocumentBuilderFactory factory = ScilabDocumentBuilderFactory.newInstance();
        assertNotNull(factory);
        // The whole purpose of the wrapper: the caller's global property is left untouched.
        assertEquals("com.example.Sentinel", System.getProperty(PROP));
    }

    @Test
    public void newInstanceRestoresAnUnsetPropertyBackToUnset() {
        System.clearProperty(PROP);
        DocumentBuilderFactory factory = ScilabDocumentBuilderFactory.newInstance();
        assertNotNull(factory);
        assertNull(System.getProperty(PROP));
    }
}
