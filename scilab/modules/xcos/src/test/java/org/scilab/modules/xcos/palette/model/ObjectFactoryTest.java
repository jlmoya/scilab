/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Scilab test coverage
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 *
 */

package org.scilab.modules.xcos.palette.model;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.xml.bind.annotation.XmlRegistry;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for the JAXB {@link ObjectFactory}.
 *
 * The factory only allocates plain schema-derived POJOs, so no native runtime
 * is required.
 */
public class ObjectFactoryTest {

    private final ObjectFactory factory = new ObjectFactory();

    @Test
    public void constructorProducesUsableFactory() {
        assertNotNull(new ObjectFactory());
    }

    @Test
    public void createPaletteBlock() {
        PaletteBlock block = factory.createPaletteBlock();
        assertNotNull(block);
    }

    @Test
    public void createCategory() {
        Category category = factory.createCategory();
        assertNotNull(category);
    }

    @Test
    public void createCustom() {
        Custom custom = factory.createCustom();
        assertNotNull(custom);
    }

    @Test
    public void createPreLoaded() {
        PreLoaded preLoaded = factory.createPreLoaded();
        assertNotNull(preLoaded);
    }

    @Test
    public void createVariablePath() {
        VariablePath path = factory.createVariablePath();
        assertNotNull(path);
    }

    /**
     * Each factory call must return a fresh instance; the factory keeps no
     * shared state.
     */
    @Test
    public void eachCallReturnsADistinctInstance() {
        assertNotSame(factory.createPaletteBlock(), factory.createPaletteBlock());
        assertNotSame(factory.createCategory(), factory.createCategory());
        assertNotSame(factory.createCustom(), factory.createCustom());
        assertNotSame(factory.createPreLoaded(), factory.createPreLoaded());
        assertNotSame(factory.createVariablePath(), factory.createVariablePath());
    }

    /**
     * The class is a JAXB registry; the {@link XmlRegistry} marker is part of
     * its public contract.
     */
    @Test
    public void isAnnotatedAsXmlRegistry() {
        assertTrue(ObjectFactory.class.isAnnotationPresent(XmlRegistry.class));
    }
}
