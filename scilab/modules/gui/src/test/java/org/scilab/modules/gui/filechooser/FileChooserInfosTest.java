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

package org.scilab.modules.gui.filechooser;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link FileChooserInfos}, the lazy singleton that
 * carries the state of the last file-chooser dialog. It is a plain
 * value-holder with no native or UI dependencies.
 *
 * <p>Because it is a process-wide singleton, {@link #reset()} restores the
 * documented defaults via {@code init()} before every test so ordering (and
 * any state left behind by other test classes sharing the JVM) cannot make
 * these tests flaky.</p>
 */
class FileChooserInfosTest {

    @BeforeEach
    void reset() {
        FileChooserInfos.getInstance().init();
    }

    @Test
    void getInstanceIsANonNullStableSingleton() {
        FileChooserInfos a = FileChooserInfos.getInstance();
        FileChooserInfos b = FileChooserInfos.getInstance();
        assertNotNull(a);
        assertSame(a, b, "getInstance() must always return the same object");
    }

    @Test
    void initEstablishesDocumentedDefaults() {
        FileChooserInfos infos = FileChooserInfos.getInstance();
        assertNull(infos.getTitleBox());
        assertNull(infos.getMask());
        assertNull(infos.getMaskDescription());
        assertNull(infos.getInitialDirectory());
        assertEquals(0, infos.getSelectionSize());
        assertNull(infos.getSelection());
        // Multiple selection defaults to TRUE (an easy-to-miss default).
        assertTrue(infos.isMultipleSelection());
        assertNull(infos.getSelectionPathName());
        assertEquals(0, infos.getFilterIndex());
        assertEquals("void", infos.getMenuCallback());
    }

    @Test
    void initResetsPreviouslyMutatedFields() {
        FileChooserInfos infos = FileChooserInfos.getInstance();

        // Move every reset-tracked field away from its default.
        infos.setTitleBox("Open");
        infos.setMask(new String[] {"*.sci"});
        infos.setMaskDescription(new String[] {"Scilab"});
        infos.setInitialDirectory("/tmp");
        infos.setSelectionSize(7);
        infos.setSelection(new String[] {"a", "b"});
        infos.setMultipleSelection(false);
        infos.setSelectionPathName("/tmp/a");
        infos.setFilterIndex(4);
        infos.setMenuCallback("myCallback");

        infos.init();

        assertNull(infos.getTitleBox());
        assertNull(infos.getMask());
        assertNull(infos.getMaskDescription());
        assertNull(infos.getInitialDirectory());
        assertEquals(0, infos.getSelectionSize());
        assertNull(infos.getSelection());
        assertTrue(infos.isMultipleSelection());
        assertNull(infos.getSelectionPathName());
        assertEquals(0, infos.getFilterIndex());
        assertEquals("void", infos.getMenuCallback());
    }

    @Test
    void initDoesNotClearSelectionFileNamesDefect() {
        // DEFECT CHARACTERIZATION: init() resets every field EXCEPT
        // selectionFileNames, which is left dangling across a "reset".
        FileChooserInfos infos = FileChooserInfos.getInstance();
        String[] files = {"one.sci", "two.sce"};
        infos.setSelectionFileNames(files);

        infos.init();

        assertArrayEquals(files, infos.getSelectionFileNames(),
                          "init() unexpectedly leaves selectionFileNames untouched");
    }

    @Test
    void titleBoxRoundTrips() {
        FileChooserInfos infos = FileChooserInfos.getInstance();
        infos.setTitleBox("Choose a file");
        assertEquals("Choose a file", infos.getTitleBox());
    }

    @Test
    void maskRoundTrips() {
        FileChooserInfos infos = FileChooserInfos.getInstance();
        String[] mask = {"*.sci", "*.sce"};
        infos.setMask(mask);
        assertArrayEquals(mask, infos.getMask());
        assertSame(mask, infos.getMask(), "getter exposes the stored array reference");
    }

    @Test
    void maskDescriptionRoundTrips() {
        FileChooserInfos infos = FileChooserInfos.getInstance();
        String[] desc = {"Scilab files", "All files"};
        infos.setMaskDescription(desc);
        assertArrayEquals(desc, infos.getMaskDescription());
    }

    @Test
    void initialDirectoryRoundTrips() {
        FileChooserInfos infos = FileChooserInfos.getInstance();
        infos.setInitialDirectory("/home/user/work");
        assertEquals("/home/user/work", infos.getInitialDirectory());
    }

    @Test
    void selectionSizeRoundTrips() {
        FileChooserInfos infos = FileChooserInfos.getInstance();
        infos.setSelectionSize(42);
        assertEquals(42, infos.getSelectionSize());
    }

    @Test
    void selectionRoundTrips() {
        FileChooserInfos infos = FileChooserInfos.getInstance();
        String[] selection = {"/a/b.sci", "/a/c.sce"};
        infos.setSelection(selection);
        assertArrayEquals(selection, infos.getSelection());
    }

    @Test
    void selectionFileNamesRoundTrips() {
        FileChooserInfos infos = FileChooserInfos.getInstance();
        String[] names = {"b.sci", "c.sce"};
        infos.setSelectionFileNames(names);
        assertArrayEquals(names, infos.getSelectionFileNames());
    }

    @Test
    void multipleSelectionRoundTrips() {
        FileChooserInfos infos = FileChooserInfos.getInstance();
        infos.setMultipleSelection(false);
        assertFalse(infos.isMultipleSelection());
        infos.setMultipleSelection(true);
        assertTrue(infos.isMultipleSelection());
    }

    @Test
    void selectionPathNameRoundTrips() {
        FileChooserInfos infos = FileChooserInfos.getInstance();
        infos.setSelectionPathName("/a/b.sci");
        assertEquals("/a/b.sci", infos.getSelectionPathName());
    }

    @Test
    void filterIndexRoundTrips() {
        FileChooserInfos infos = FileChooserInfos.getInstance();
        infos.setFilterIndex(3);
        assertEquals(3, infos.getFilterIndex());
    }

    @Test
    void menuCallbackRoundTrips() {
        FileChooserInfos infos = FileChooserInfos.getInstance();
        infos.setMenuCallback("exec('foo.sce')");
        assertEquals("exec('foo.sce')", infos.getMenuCallback());
    }
}
