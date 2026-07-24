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

import java.io.StringWriter;

import javax.xml.stream.XMLOutputFactory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link ScilabXMLOutputFactory}.
 *
 * <p>Mirror of the {@link ScilabTransformerFactory} contract but for the StAX
 * {@code javax.xml.stream.XMLOutputFactory} system property. The property is
 * snapshotted before and restored after each test.
 */
public class ScilabXMLOutputFactoryTest {

    private static final String PROP = "javax.xml.stream.XMLOutputFactory";
    private static final String IMPL = "com.sun.xml.internal.stream.XMLOutputFactoryImpl";

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
        String previous = ScilabXMLOutputFactory.useDefaultTransformerFactoryImpl();
        assertNull(previous);
        assertEquals(IMPL, System.getProperty(PROP));
    }

    @Test
    public void useDefaultReportsAndOverwritesAnExistingValue() {
        System.setProperty(PROP, "some.other.Factory");
        String previous = ScilabXMLOutputFactory.useDefaultTransformerFactoryImpl();
        assertEquals("some.other.Factory", previous);
        assertEquals(IMPL, System.getProperty(PROP));
    }

    @Test
    public void restoreWithNullClearsTheProperty() {
        System.setProperty(PROP, "leftover");
        ScilabXMLOutputFactory.restoreTransformerFactoryImpl(null);
        assertNull(System.getProperty(PROP));
    }

    @Test
    public void restoreWithAValueSetsTheProperty() {
        System.clearProperty(PROP);
        ScilabXMLOutputFactory.restoreTransformerFactoryImpl("restored.Factory");
        assertEquals("restored.Factory", System.getProperty(PROP));
    }

    @Test
    public void newInstanceReturnsAUsableXMLOutputFactory() {
        XMLOutputFactory factory = ScilabXMLOutputFactory.newInstance();
        assertNotNull(factory);
        assertInstanceOf(XMLOutputFactory.class, factory);
        assertDoesNotThrow(() -> {
            factory.createXMLStreamWriter(new StringWriter());
        }, "the produced factory must be able to create a stream writer");
    }

    @Test
    public void newInstanceRestoresAnUnsetPropertyAfterwards() {
        System.clearProperty(PROP);
        ScilabXMLOutputFactory.newInstance();
        assertNull(System.getProperty(PROP));
    }

    @Test
    public void newInstanceRestoresAPreexistingPropertyAfterwards() {
        System.setProperty(PROP, "sentinel.Factory");
        ScilabXMLOutputFactory.newInstance();
        assertEquals("sentinel.Factory", System.getProperty(PROP));
    }
}
