/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Jose Moya
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 *
 */

package org.scilab.modules.gui.utils;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.scilab.modules.gui.filechooser.FileChooserInfos;

/**
 * Hermetic unit tests for {@link SciFileFilter}, the generic Swing file
 * filter used by Scilab's file-selection dialogs.
 *
 * <p><b>Native-runtime boundary.</b> When {@code maskdescription == null}
 * the constructor localizes every description through
 * {@code Messages.gettext()}, which delegates to a native JNI method. That
 * path therefore CANNOT run hermetically and is intentionally left
 * untested here. Every test below passes a non-null description, which
 * bypasses {@code gettext} entirely while still exercising the full
 * regexp-building and {@link SciFileFilter#accept(File)} logic (identical
 * for both constructor branches).</p>
 *
 * <p>The platform matters: {@code accept()} is case-insensitive on Windows
 * and case-sensitive elsewhere. All assertions use case-consistent names so
 * they hold on every platform.</p>
 */
class SciFileFilterTest {

    @BeforeEach
    void resetSingleton() {
        // accept() mutates the FileChooserInfos singleton's filterIndex;
        // reset it so the side-effect assertions are deterministic.
        FileChooserInfos.getInstance().init();
    }

    /* ------------------------------------------------------------------ */
    /* Description + extensions (non-null description branch).            */
    /* ------------------------------------------------------------------ */

    @Test
    void getDescriptionReturnsSuppliedDescriptionVerbatim() {
        SciFileFilter f = new SciFileFilter("*.sci", "My Scilab files", 0);
        assertEquals("My Scilab files", f.getDescription());
    }

    @Test
    void simpleLowercaseMaskDerivesExtensionArray() {
        // fileMask matches \*\.[a-z]+  =>  extensions = { substring(2) }.
        SciFileFilter f = new SciFileFilter("*.png", "desc", 0);
        assertArrayEquals(new String[] {"png"}, f.getExtensions());
    }

    @Test
    void wildcardInMiddleMaskLeavesExtensionsNull() {
        // "*.sc*" does NOT match \*\.[a-z]+ (the trailing '*' is not [a-z]),
        // so with a supplied description no extension array is derived.
        SciFileFilter f = new SciFileFilter("*.sc*", "desc", 0);
        assertNull(f.getExtensions());
    }

    @Test
    void uppercaseMaskLeavesExtensionsNull() {
        // The derivation pattern requires [a-z]+; an upper-case mask misses.
        SciFileFilter f = new SciFileFilter("*.SCI", "desc", 0);
        assertNull(f.getExtensions());
    }

    /* ------------------------------------------------------------------ */
    /* accept(): directories, matches, and non-matches.                  */
    /* ------------------------------------------------------------------ */

    @Test
    void acceptDirectoryAlwaysTrue(@TempDir File dir) {
        SciFileFilter f = new SciFileFilter("*.sci", "desc", 0);
        assertTrue(dir.isDirectory(), "precondition: TempDir is a directory");
        assertTrue(f.accept(dir));
    }

    @Test
    void acceptMatchesFilesWithTheMaskExtension() {
        SciFileFilter f = new SciFileFilter("*.sci", "desc", 0);
        assertTrue(f.accept(new File("script.sci")));
        assertTrue(f.accept(new File("deep/path/to/other.sci")));
    }

    @Test
    void acceptRejectsFilesWithADifferentExtension() {
        SciFileFilter f = new SciFileFilter("*.sci", "desc", 0);
        assertFalse(f.accept(new File("script.sce")));
        assertFalse(f.accept(new File("notes.txt")));
    }

    @Test
    void acceptEmptyMaskReturnsTrueForEverything() {
        // Bug 2861: an empty file mask must accept all files. An empty mask
        // string stays empty through the regexp transforms.
        SciFileFilter f = new SciFileFilter("", "desc", 0);
        assertTrue(f.accept(new File("anything.bin")));
        assertTrue(f.accept(new File("no_extension")));
    }

    /* ------------------------------------------------------------------ */
    /* The "*.*" special case (bug 7285) + singleton side effect.         */
    /* ------------------------------------------------------------------ */

    @Test
    void acceptStarDotStarAcceptsEvenExtensionlessFiles() {
        // "*.*" transforms to the regexp ".*\..*", which the code special-
        // cases to accept ALL files, including ones without an extension.
        SciFileFilter f = new SciFileFilter("*.*", "All files", 5);
        assertTrue(f.accept(new File("has.extension")));
        assertTrue(f.accept(new File("no_extension")));
    }

    @Test
    void acceptStarDotStarPublishesFilterIndexPlusOne() {
        // Side effect: accept() writes (filterIndex + 1) into the singleton.
        SciFileFilter f = new SciFileFilter("*.*", "All files", 5);
        f.accept(new File("some.file"));
        assertEquals(6, FileChooserInfos.getInstance().getFilterIndex());
    }

    @Test
    void acceptPublishesFilterIndexPlusOneOnMatch() {
        SciFileFilter f = new SciFileFilter("*.sci", "desc", 3);
        assertTrue(f.accept(new File("x.sci")));
        assertEquals(4, FileChooserInfos.getInstance().getFilterIndex());
    }

    @Test
    void acceptPublishesFilterIndexEvenWhenFileIsRejected() {
        // CHARACTERIZATION: the singleton is updated BEFORE the regexp test,
        // so a rejected file still moves the published filter index.
        SciFileFilter f = new SciFileFilter("*.sci", "desc", 7);
        assertFalse(f.accept(new File("note.txt")));
        assertEquals(8, FileChooserInfos.getInstance().getFilterIndex());
    }

    /* ------------------------------------------------------------------ */
    /* The "all" pseudo-mask expands to the Scilab family of extensions.  */
    /* ------------------------------------------------------------------ */

    @Test
    void allMaskAcceptsTheScilabFileFamily() {
        // "all" is rewritten to (*.sci)|(*.sce)|(*.tst)|(*.start)|(*.quit)|(*.dem).
        SciFileFilter f = new SciFileFilter("all", "All Scilab files", 0);
        assertTrue(f.accept(new File("a.sci")));
        assertTrue(f.accept(new File("b.sce")));
        assertTrue(f.accept(new File("c.tst")));
        assertTrue(f.accept(new File("d.start")));
        assertTrue(f.accept(new File("e.quit")));
        assertTrue(f.accept(new File("f.dem")));
    }

    @Test
    void allMaskRejectsUnrelatedExtensions() {
        SciFileFilter f = new SciFileFilter("all", "All Scilab files", 0);
        assertFalse(f.accept(new File("g.txt")));
        assertFalse(f.accept(new File("h.png")));
    }
}
