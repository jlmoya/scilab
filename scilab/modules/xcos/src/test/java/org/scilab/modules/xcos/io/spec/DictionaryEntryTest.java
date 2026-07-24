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
package org.scilab.modules.xcos.io.spec;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.ObjectOutputStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link DictionaryEntry}.
 *
 * <p>{@code DictionaryEntry} is the {@link Entry} that serializes the block
 * dictionary ({@code dictionary/dictionary.ser}) inside an {@code .xcos}
 * package. As with {@link ContentEntry}, the media-type / full-path pair is the
 * file-format routing contract and is pinned below.</p>
 *
 * <p>{@code setup}/{@code store} and the happy path of {@code load} require an
 * {@code XcosPackage} (its constructor calls a native version timestamp) holding
 * a live {@code ScilabList} dictionary, so they are covered by integration
 * tests, not here. The <em>error path</em> of {@code load} is, however, fully
 * hermetic and is characterized below.</p>
 */
public class DictionaryEntryTest {

    @Test
    @DisplayName("getMediaType() is the fixed bin/ser media type")
    public void mediaTypeIsBinSer() {
        assertEquals("bin/ser", new DictionaryEntry().getMediaType());
    }

    @Test
    @DisplayName("getFullPath() is the fixed dictionary/dictionary.ser member")
    public void fullPathIsDictionarySer() {
        assertEquals("dictionary/dictionary.ser", new DictionaryEntry().getFullPath());
    }

    @Test
    @DisplayName("DictionaryEntry is an Entry")
    public void implementsEntry() {
        assertTrue(Entry.class.isInstance(new DictionaryEntry()));
    }

    @Test
    @DisplayName("the format identifiers are stable across calls and instances")
    public void identifiersAreConstant() {
        DictionaryEntry a = new DictionaryEntry();
        DictionaryEntry b = new DictionaryEntry();

        assertEquals(a.getMediaType(), a.getMediaType());
        assertEquals(a.getFullPath(), a.getFullPath());
        assertEquals(a.getMediaType(), b.getMediaType());
        assertEquals(a.getFullPath(), b.getFullPath());
    }

    /**
     * Characterization: {@code load} wraps its body in a
     * {@code catch (IOException | ClassNotFoundException)} that only logs. A
     * corrupt stream fails inside the {@code ObjectInputStream} constructor
     * (bad/short stream header {@literal ->} {@code IOException}) before {@code
     * pack} is ever dereferenced, so {@code load} swallows it and returns
     * normally even though it declares {@code throws IOException} and even
     * though {@code setup} was never called.
     */
    @Test
    @DisplayName("load swallows a corrupt/empty stream (logs, does not throw)")
    public void loadSwallowsCorruptStream() {
        DictionaryEntry entry = new DictionaryEntry(); // no setup(): pack is null
        InputStream empty = new ByteArrayInputStream(new byte[0]);

        // entry and encoding arguments are ignored on this path.
        assertDoesNotThrow(() -> entry.load(null, empty, "UTF-8"));
    }

    /**
     * Characterization of a latent precondition: once the stream header is
     * valid, {@code ObjectInputStream} construction succeeds and {@code load}
     * immediately dereferences {@code pack} ({@code pack.getDictionary()}).
     * With no prior {@code setup(XcosPackage)}, {@code pack} is {@code null},
     * so a {@code NullPointerException} escapes (it is not an
     * {@code IOException}/{@code ClassNotFoundException}, so the catch does not
     * cover it). This documents that {@code load} has an unchecked
     * {@code setup}-first precondition.
     */
    @Test
    @DisplayName("load without setup() throws NPE once the stream header is valid")
    public void loadWithoutSetupThrowsNpeOnValidHeader() throws Exception {
        // A minimal, well-formed object-stream header (magic + version) is
        // enough: ObjectInputStream's constructor consumes only those bytes,
        // then load reaches pack.getDictionary() before any readObject().
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.flush();
        InputStream validHeader = new ByteArrayInputStream(baos.toByteArray());

        DictionaryEntry entry = new DictionaryEntry(); // deliberately no setup()

        assertThrows(NullPointerException.class,
                     () -> entry.load(null, validHeader, "UTF-8"));
    }
}
