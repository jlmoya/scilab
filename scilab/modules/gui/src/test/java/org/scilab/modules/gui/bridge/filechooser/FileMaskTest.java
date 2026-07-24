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

package org.scilab.modules.gui.bridge.filechooser;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Hermetic unit tests for {@link FileMask}, the {@code javax.swing} file
 * filter used by the graphic-export file chooser. Every method operates on
 * plain {@link String}s and {@link File} handles (no I/O beyond the file
 * name, and no native runtime), so the tests run without a display.
 *
 * <p>Several tests are deliberate <em>characterization</em> tests: they pin
 * down the current (arguably buggy) behavior of {@code getDescription()} and
 * the array constructor so that a future change is caught rather than
 * silently allowed.</p>
 */
class FileMaskTest {

    /* ------------------------------------------------------------------ */
    /* Default constructor: no filters => accept everything.              */
    /* ------------------------------------------------------------------ */

    @Test
    void defaultConstructorHasNoFilters() {
        FileMask mask = new FileMask();
        // With an empty filter list getExtensionFromFilter returns null.
        assertNull(mask.getExtensionFromFilter());
    }

    @Test
    void defaultConstructorAcceptsAnyRegularFile() {
        // filters.size() == 0 => accept() short-circuits to true for a
        // non-directory file, even one that does not exist on disk.
        FileMask mask = new FileMask();
        assertTrue(mask.accept(new File("whatever.xyz")));
        assertTrue(mask.accept(new File("no_extension_at_all")));
    }

    /* ------------------------------------------------------------------ */
    /* Single-extension constructor lowercases the filter.               */
    /* ------------------------------------------------------------------ */

    @Test
    void singleExtensionConstructorLowercasesFilter() {
        FileMask mask = new FileMask("JPG", "JPEG Images");
        // The stored filter is lowercased at construction time.
        assertEquals("jpg", mask.getExtensionFromFilter());
        // ...so it matches regardless of the file-name case.
        assertTrue(mask.accept(new File("photo.jpg")));
        assertTrue(mask.accept(new File("photo.JPG")));
        assertTrue(mask.accept(new File("photo.Jpg")));
    }

    @Test
    void singleExtensionConstructorRejectsOtherExtensions() {
        FileMask mask = new FileMask("jpg", "JPEG Images");
        assertFalse(mask.accept(new File("photo.png")));
        // No extension => getExtension returns null => rejected.
        assertFalse(mask.accept(new File("README")));
    }

    @Test
    void singleExtensionConstructorNullExtensionThrows() {
        // extension.toLowerCase() is called with no null guard. The cast
        // disambiguates from the String[] overload.
        assertThrows(NullPointerException.class,
                     () -> new FileMask((String) null, "desc"));
    }

    /* ------------------------------------------------------------------ */
    /* Array constructor.                                                 */
    /* ------------------------------------------------------------------ */

    @Test
    void arrayConstructorAcceptsAnyListedExtension() {
        FileMask mask = new FileMask(new String[] {"jpg", "png"}, "Images");
        assertTrue(mask.accept(new File("a.jpg")));
        assertTrue(mask.accept(new File("b.png")));
        assertFalse(mask.accept(new File("c.gif")));
    }

    @Test
    void arrayConstructorExtensionFromFilterReturnsFirst() {
        FileMask mask = new FileMask(new String[] {"jpg", "png"}, "Images");
        assertEquals("jpg", mask.getExtensionFromFilter());
    }

    @Test
    void arrayConstructorDoesNotLowercaseExtensionsDefect() {
        // DEFECT CHARACTERIZATION: unlike the single-extension constructor,
        // the array constructor stores extensions verbatim (no toLowerCase).
        // accept() lowercases the file's extension before comparing, so an
        // upper-case entry can never match => the filter is dead.
        FileMask mask = new FileMask(new String[] {"JPG"}, "Images");
        assertEquals("JPG", mask.getExtensionFromFilter());
        // file extension "jpg" != stored "JPG" => rejected.
        assertFalse(mask.accept(new File("photo.jpg")));
        assertFalse(mask.accept(new File("photo.JPG")));
    }

    /* ------------------------------------------------------------------ */
    /* accept(): directory and null handling.                            */
    /* ------------------------------------------------------------------ */

    @Test
    void acceptDirectoryAlwaysTrue(@TempDir File dir) {
        // Directories are always accepted so the user can navigate into
        // them, regardless of the configured filters.
        FileMask mask = new FileMask("jpg", "JPEG Images");
        assertTrue(dir.isDirectory(), "precondition: TempDir is a directory");
        assertTrue(mask.accept(dir));
    }

    @Test
    void acceptNullFileReturnsFalse() {
        FileMask mask = new FileMask("jpg", "JPEG Images");
        assertFalse(mask.accept((File) null));
    }

    /* ------------------------------------------------------------------ */
    /* Static getExtension(): the extension-parsing rules.               */
    /* ------------------------------------------------------------------ */

    @Test
    void getExtensionReturnsLastSegmentLowercased() {
        assertEquals("gz", FileMask.getExtension(new File("archive.tar.gz")));
        assertEquals("jpg", FileMask.getExtension(new File("PHOTO.JPG")));
        assertEquals("b", FileMask.getExtension(new File("a.b")));
    }

    @Test
    void getExtensionNoDotReturnsNull() {
        assertNull(FileMask.getExtension(new File("noextension")));
    }

    @Test
    void getExtensionLeadingDotReturnsNull() {
        // lastIndexOf('.') == 0, and the guard requires i > 0.
        assertNull(FileMask.getExtension(new File(".bashrc")));
    }

    @Test
    void getExtensionTrailingDotReturnsNull() {
        // The dot is the last character (i == length - 1) => no extension.
        assertNull(FileMask.getExtension(new File("trailingdot.")));
    }

    @Test
    void getExtensionNullFileReturnsNull() {
        assertNull(FileMask.getExtension(null));
    }

    /* ------------------------------------------------------------------ */
    /* clearExtensions().                                                 */
    /* ------------------------------------------------------------------ */

    @Test
    void clearExtensionsEmptiesTheFilterAndAcceptsEverything() {
        FileMask mask = new FileMask(new String[] {"jpg", "png"}, "Images");
        assertFalse(mask.accept(new File("c.gif")));

        mask.clearExtensions();

        assertNull(mask.getExtensionFromFilter());
        // Empty filter list => everything is accepted again.
        assertTrue(mask.accept(new File("c.gif")));
    }

    /* ------------------------------------------------------------------ */
    /* getDescription(): quirky formatting + a caching defect.           */
    /* ------------------------------------------------------------------ */

    @Test
    void getDescriptionWithProvidedDescriptionIgnoresExtensionList() {
        // CHARACTERIZATION: when a description is supplied, getDescription()
        // returns it verbatim; the "(.ext, .ext)" list is NEVER appended
        // because the builder branch only runs when description == null.
        FileMask mask = new FileMask("jpg", "JPEG Images");
        assertEquals("JPEG Images", mask.getDescription());
        // Repeated calls stay stable in this branch.
        assertEquals("JPEG Images", mask.getDescription());
    }

    @Test
    void getDescriptionNullDescriptionBuildsExtensionList() {
        // description == null triggers the "(.jpg, .png)" builder.
        FileMask mask = new FileMask(new String[] {"jpg", "png"}, null);
        assertEquals("(.jpg, .png)", mask.getDescription());
    }

    @Test
    void getDescriptionEmptyFilterYieldsEmptyParentheses() {
        // Default constructor: description == null, no filters.
        FileMask mask = new FileMask();
        assertEquals("()", mask.getDescription());
    }

    @Test
    void getDescriptionCachingDefectReturnsNullOnSecondCall() {
        // DEFECT CHARACTERIZATION: the first call returns the built string
        // but stores an INCOMPLETE value in fullDescription (missing the
        // closing paren) and never re-enters the builder. On the second
        // call fullDescription != null so the method falls through to
        // `return description`, which is null. The description therefore
        // changes from a real string to null between identical calls.
        FileMask mask = new FileMask(new String[] {"jpg", "png"}, null);
        assertEquals("(.jpg, .png)", mask.getDescription());
        assertNull(mask.getDescription());
    }
}
