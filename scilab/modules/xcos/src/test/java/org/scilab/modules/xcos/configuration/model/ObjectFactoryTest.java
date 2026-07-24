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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.xml.bind.JAXBElement;
import javax.xml.namespace.QName;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for the JAXB {@link ObjectFactory}.
 *
 * <p>
 * The factory is trivial glue: two no-arg {@code create*} methods returning
 * fresh value objects, and one {@code createSettings} that wraps a
 * {@link SettingType} in a {@link JAXBElement} bound to the {@code settings}
 * root element. The JAXB API jar is on the (compile-scope) classpath, so
 * {@link JAXBElement} resolves without any native runtime.
 */
public class ObjectFactoryTest {

    private final ObjectFactory factory = new ObjectFactory();

    @Test
    public void createSettingTypeReturnsNonNullInstance() {
        assertNotNull(factory.createSettingType());
    }

    @Test
    public void createSettingTypeReturnsAFreshInstanceEachCall() {
        assertNotSame(factory.createSettingType(), factory.createSettingType());
    }

    @Test
    public void createDocumentTypeReturnsNonNullInstance() {
        assertNotNull(factory.createDocumentType());
    }

    @Test
    public void createDocumentTypeReturnsAFreshInstanceEachCall() {
        assertNotSame(factory.createDocumentType(), factory.createDocumentType());
    }

    @Test
    public void createSettingsWrapsTheGivenValue() {
        SettingType value = factory.createSettingType();

        JAXBElement<SettingType> element = factory.createSettings(value);

        assertNotNull(element);
        assertSame(value, element.getValue());
        assertEquals(SettingType.class, element.getDeclaredType());
    }

    @Test
    public void createSettingsUsesTheSettingsQName() {
        JAXBElement<SettingType> element = factory.createSettings(factory.createSettingType());

        assertEquals(new QName("", "settings"), element.getName());
        assertEquals("settings", element.getName().getLocalPart());
        assertEquals("", element.getName().getNamespaceURI());
    }

    @Test
    public void createSettingsProducesAGlobalScopeElement() {
        JAXBElement<SettingType> element = factory.createSettings(factory.createSettingType());
        assertTrue(element.isGlobalScope());
    }

    @Test
    public void createSettingsAcceptsANullValue() {
        JAXBElement<SettingType> element = factory.createSettings(null);

        assertNotNull(element);
        assertNull(element.getValue());
        assertEquals(new QName("", "settings"), element.getName());
        assertEquals(SettingType.class, element.getDeclaredType());
    }
}
