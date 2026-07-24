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

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link ContainerConverter}, the abstract base shared by
 * the container-only converters (JAR/PDF/PS).
 *
 * <p>The class contributes only two things of its own: it stores the constructor's
 * {@code outputDirectory}/{@code language} into {@code protected final} fields, and
 * it provides an empty {@link ContainerConverter#registerAllExternalXMLHandlers()}
 * (container backends have no external XML handlers to register). A tiny concrete
 * subclass in this same package exercises both. The protected fields are read
 * directly — legal here because the test lives in {@code org.scilab.modules.helptools}.
 */
public class ContainerConverterTest {

    /** Minimal concrete container converter — convert()/install() are the only abstract leftovers. */
    private static final class FakeContainerConverter extends ContainerConverter {
        boolean converted;
        boolean installed;

        FakeContainerConverter(String outputDirectory, String language) {
            super(outputDirectory, language);
        }

        @Override
        public void convert() {
            converted = true;
        }

        @Override
        public void install() {
            installed = true;
        }
    }

    @Test
    public void constructorStoresOutputDirectoryAndLanguage() {
        FakeContainerConverter c = new FakeContainerConverter("/tmp/out", "fr_FR");
        assertEquals("/tmp/out", c.outputDirectory);
        assertEquals("fr_FR", c.language);
    }

    @Test
    public void nullArgumentsAreStoredVerbatim() {
        FakeContainerConverter c = new FakeContainerConverter(null, null);
        assertNull(c.outputDirectory);
        assertNull(c.language);
    }

    @Test
    public void registerAllExternalXMLHandlersIsANoOp() {
        FakeContainerConverter c = new FakeContainerConverter("out", "en_US");
        // The base implementation is deliberately empty and must not throw.
        assertDoesNotThrow(c::registerAllExternalXMLHandlers);
    }

    @Test
    public void subclassHooksAreReachableThroughTheConverterContract() throws Exception {
        FakeContainerConverter c = new FakeContainerConverter("out", "en_US");
        Converter asConverter = c;
        asConverter.convert();
        asConverter.install();
        assertTrue(c.converted);
        assertTrue(c.installed);
    }
}
