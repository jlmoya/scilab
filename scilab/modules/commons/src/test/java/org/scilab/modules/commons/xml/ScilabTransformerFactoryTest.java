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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import javax.xml.transform.TransformerFactory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link ScilabTransformerFactory}.
 *
 * <p>The class is a thin wrapper that temporarily forces the JDK-internal
 * {@code TransformerFactory} implementation via the
 * {@code javax.xml.transform.TransformerFactory} system property. These tests
 * exercise the property juggling and the round-trip restoration contract; the
 * shared system property is snapshotted before and restored after every test so
 * the suite never leaks global state.
 */
public class ScilabTransformerFactoryTest {

    private static final String PROP = "javax.xml.transform.TransformerFactory";
    private static final String IMPL = "com.sun.org.apache.xalan.internal.xsltc.trax.TransformerFactoryImpl";

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
    public void useDefaultReturnsPreviousValueAndInstallsTheInternalImpl() {
        System.clearProperty(PROP);
        String previous = ScilabTransformerFactory.useDefaultTransformerFactoryImpl();
        assertNull(previous, "no property was set beforehand");
        assertEquals(IMPL, System.getProperty(PROP), "the internal impl must be installed");
    }

    @Test
    public void useDefaultReportsAndOverwritesAnExistingValue() {
        System.setProperty(PROP, "some.other.Factory");
        String previous = ScilabTransformerFactory.useDefaultTransformerFactoryImpl();
        assertEquals("some.other.Factory", previous);
        assertEquals(IMPL, System.getProperty(PROP));
    }

    @Test
    public void restoreWithNullClearsTheProperty() {
        System.setProperty(PROP, "leftover");
        ScilabTransformerFactory.restoreTransformerFactoryImpl(null);
        assertNull(System.getProperty(PROP));
    }

    @Test
    public void restoreWithAValueSetsTheProperty() {
        System.clearProperty(PROP);
        ScilabTransformerFactory.restoreTransformerFactoryImpl("restored.Factory");
        assertEquals("restored.Factory", System.getProperty(PROP));
    }

    @Test
    public void newInstanceReturnsAUsableTransformerFactory() {
        TransformerFactory factory = ScilabTransformerFactory.newInstance();
        assertNotNull(factory);
        assertInstanceOf(TransformerFactory.class, factory);
        assertDoesNotThrow(() -> {
            factory.newTransformer();
        }, "the produced factory must be able to create a Transformer");
    }

    @Test
    public void newInstanceRestoresAnUnsetPropertyAfterwards() {
        System.clearProperty(PROP);
        ScilabTransformerFactory.newInstance();
        assertNull(System.getProperty(PROP), "newInstance must leave the property as it found it (unset)");
    }

    @Test
    public void newInstanceRestoresAPreexistingPropertyAfterwards() {
        System.setProperty(PROP, "sentinel.Factory");
        ScilabTransformerFactory.newInstance();
        assertEquals("sentinel.Factory", System.getProperty(PROP),
                     "newInstance must restore the caller's original property value");
    }
}
