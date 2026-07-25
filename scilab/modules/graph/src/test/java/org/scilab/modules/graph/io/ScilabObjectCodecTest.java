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

package org.scilab.modules.graph.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.scilab.modules.graph.io.ScilabObjectCodec.UnrecognizeFormatException;
import org.scilab.modules.types.ScilabBoolean;
import org.scilab.modules.types.ScilabDouble;
import org.scilab.modules.types.ScilabInteger;
import org.scilab.modules.types.ScilabList;
import org.scilab.modules.types.ScilabString;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

import com.mxgraph.io.mxCodecRegistry;

/**
 * Hermetic unit tests for {@link ScilabObjectCodec}: the shared static
 * binary-serialization toggle and the protected DOM attribute helpers.
 *
 * The abstract base is exercised through a tiny concrete subclass built exactly
 * the way ScilabObjectCodec.register() builds the production codecs. DOM nodes
 * are produced with the JDK parser, so nothing here needs a running Scilab.
 */
public class ScilabObjectCodecTest {

    /** Minimal concrete codec so the abstract base can be instantiated. */
    private static final class TestCodec extends ScilabObjectCodec {
        TestCodec() {
            super(new ScilabString(), null, null, null);
        }
    }

    private static NamedNodeMap attributes(String... keyThenValue) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element element = doc.createElement("data");
        for (int i = 0; i < keyThenValue.length; i += 2) {
            element.setAttribute(keyThenValue[i], keyThenValue[i + 1]);
        }
        return element.getAttributes();
    }

    /*
     * ----- protected attribute helpers -----
     */

    @Test
    public void getIntegerAttributeParsesValue() throws Exception {
        TestCodec codec = new TestCodec();
        assertEquals(5, codec.getIntegerAttribute(attributes("height", "5"), "height"));
        assertEquals(-7, codec.getIntegerAttribute(attributes("width", "-7"), "width"));
    }

    @Test
    public void getIntegerAttributeMissingThrows() throws Exception {
        TestCodec codec = new TestCodec();
        NamedNodeMap attrs = attributes("height", "5");
        assertThrows(UnrecognizeFormatException.class,
                     () -> codec.getIntegerAttribute(attrs, "width"));
    }

    @Test
    public void getIntegerAttributeNonNumericThrowsUnrecognizeFormat() throws Exception {
        // A NumberFormatException is caught and rethrown wrapped as the codec's type.
        TestCodec codec = new TestCodec();
        NamedNodeMap attrs = attributes("height", "not-a-number");
        assertThrows(UnrecognizeFormatException.class,
                     () -> codec.getIntegerAttribute(attrs, "height"));
    }

    @Test
    public void getBooleanAttributeParsesTrueAndFalse() throws Exception {
        TestCodec codec = new TestCodec();
        assertTrue(codec.getBooleanAttribute(attributes("binary", "true"), "binary"));
        assertFalse(codec.getBooleanAttribute(attributes("binary", "false"), "binary"));
    }

    @Test
    public void getBooleanAttributeMissingIsFalseNotThrown() throws Exception {
        TestCodec codec = new TestCodec();
        assertFalse(codec.getBooleanAttribute(attributes("other", "x"), "binary"));
    }

    @Test
    public void getBooleanAttributeNonBooleanIsFalse() throws Exception {
        // Boolean.parseBoolean treats anything other than "true" as false.
        TestCodec codec = new TestCodec();
        assertFalse(codec.getBooleanAttribute(attributes("binary", "yes"), "binary"));
    }

    @Test
    public void heightWidthColumnLineHelpersDelegateToNamedAttributes() throws Exception {
        TestCodec codec = new TestCodec();
        NamedNodeMap attrs = attributes("height", "3", "width", "4", "column", "1", "line", "2");
        assertEquals(3, codec.getHeight(attrs));
        assertEquals(4, codec.getWidth(attrs));
        assertEquals(1, codec.getColumnIndex(attrs));
        assertEquals(2, codec.getLineIndex(attrs));
    }

    @Test
    public void getHeightMissingThrows() throws Exception {
        TestCodec codec = new TestCodec();
        NamedNodeMap attrs = attributes("width", "4");
        assertThrows(UnrecognizeFormatException.class, () -> codec.getHeight(attrs));
    }

    /*
     * ----- static binary-serialization toggle -----
     */

    @Test
    public void enableWithProvidedListIsReturnedByDisable() {
        ScilabList myList = new ScilabList();
        try {
            Object lock = ScilabObjectCodec.enableBinarySerialization(myList);
            assertNotNull(lock);
            assertSame(myList, ScilabObjectCodec.getBinaryObjects());

            ScilabList returned = ScilabObjectCodec.disableBinarySerialization();
            assertSame(myList, returned);
            assertNull(ScilabObjectCodec.getBinaryObjects());
        } finally {
            // Ensure the shared static state is reset even if an assertion fails.
            ScilabObjectCodec.disableBinarySerialization();
        }
    }

    @Test
    public void enableWithNullCreatesFreshListAndReturnsSharedLock() {
        try {
            Object lock = ScilabObjectCodec.enableBinarySerialization(null);
            assertNotNull(lock);
            assertNotNull(ScilabObjectCodec.getBinaryObjects());
            // The returned monitor is the same shared lock across calls.
            assertSame(lock, ScilabObjectCodec.enableBinarySerialization(null));
        } finally {
            ScilabObjectCodec.disableBinarySerialization();
        }
        assertNull(ScilabObjectCodec.getBinaryObjects());
    }

    /*
     * ----- UnrecognizeFormatException type -----
     */

    @Test
    public void unrecognizeFormatExceptionWrapsCause() {
        Exception cause = new IllegalStateException("boom");
        UnrecognizeFormatException wrapped = new UnrecognizeFormatException(cause);
        assertSame(cause, wrapped.getCause());
        assertNull(new UnrecognizeFormatException().getCause());
    }

    /*
     * ----- register() -----
     */

    @Test
    public void registerPopulatesTheCodecRegistryWithTheScalarCodecs() {
        // register() constructs and registers one codec per Scilab scalar type.
        // The registry keys each codec under mxCodecRegistry.getName(template),
        // so we resolve with that exact same name computation. Asserting the
        // concrete subclass (not a bare mxObjectCodec) proves register() ran and
        // not the registry's reflective auto-registration fallback.
        ScilabObjectCodec.register();

        assertTrue(mxCodecRegistry.getCodec(mxCodecRegistry.getName(new ScilabString()))
                   instanceof ScilabStringCodec);
        assertTrue(mxCodecRegistry.getCodec(mxCodecRegistry.getName(new ScilabBoolean()))
                   instanceof ScilabBooleanCodec);
        assertTrue(mxCodecRegistry.getCodec(mxCodecRegistry.getName(new ScilabDouble()))
                   instanceof ScilabDoubleCodec);
        assertTrue(mxCodecRegistry.getCodec(mxCodecRegistry.getName(new ScilabInteger()))
                   instanceof ScilabIntegerCodec);
    }
}
