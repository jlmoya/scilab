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

package org.scilab.modules.preferences.Component;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Files;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Hermetic unit tests for the {@link FileSelector} preference component. The
 * href sensor validates the current path against the real filesystem
 * ({@code checkPath}): with {@code check-entry} on it only reports a path that
 * exists AND matches the file/directory mode, and with it off it reports any
 * non-empty path. The interactive file-chooser button is display-bound and is
 * not exercised. Note: within this package {@code File} is the Scilab component,
 * so {@link java.io.File} is written fully-qualified throughout.
 */
public class FileSelectorTest {

    private static Element el(String name, String... kv) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element e = doc.createElement(name);
        for (int i = 0; i + 1 < kv.length; i += 2) {
            e.setAttribute(kv[i], kv[i + 1]);
        }
        return e;
    }

    @Test
    public void freshSelectorHasNoHref() throws Exception {
        FileSelector c = new FileSelector(el("FileSelector"));
        assertNull(c.href(), "an empty text field is not a valid path");
        assertNull(c.choose());
        assertEquals("FileSelector", c.toString());
    }

    @Test
    public void actuatorsAreTheDocumentedSet() throws Exception {
        assertArrayEquals(new String[] {"enable", "href", "desc", "mask", "dir-selection", "check-entry"},
                          new FileSelector(el("FileSelector")).actuators());
    }

    @Test
    public void hrefOfAnExistingFileIsReported(@TempDir java.nio.file.Path dir) throws Exception {
        java.io.File file = Files.createFile(dir.resolve("real.sce")).toFile();
        FileSelector c = new FileSelector(el("FileSelector"));
        c.href(file.getAbsolutePath());
        assertEquals(file.getAbsolutePath(), c.href());
        assertEquals(file.getAbsolutePath(), c.choose());
        assertEquals("FileSelector href='" + file.getAbsolutePath() + "'", c.toString());
    }

    @Test
    public void anExistingFileIsRejectedWhenDirectorySelectionIsOn(@TempDir java.nio.file.Path dir) throws Exception {
        java.io.File file = Files.createFile(dir.resolve("real.sce")).toFile();
        FileSelector c = new FileSelector(el("FileSelector", "dir-selection", "true"));
        c.href(file.getAbsolutePath());
        assertNull(c.href(), "a regular file does not satisfy dir-selection='true'");
    }

    @Test
    public void anExistingDirectoryIsAcceptedWhenDirectorySelectionIsOn(@TempDir java.nio.file.Path dir) throws Exception {
        FileSelector c = new FileSelector(el("FileSelector", "dir-selection", "true"));
        c.href(dir.toFile().getAbsolutePath());
        assertEquals(dir.toFile().getAbsolutePath(), c.href());
    }

    @Test
    public void checkEntryOffAcceptsAnyNonEmptyPath() throws Exception {
        FileSelector c = new FileSelector(el("FileSelector", "check-entry", "false"));
        c.href("/no/such/path/anywhere");
        assertEquals("/no/such/path/anywhere", c.href(),
                     "with check-entry='false' the path is echoed back unvalidated");
    }

    @Test
    public void refreshAppliesANewHref(@TempDir java.nio.file.Path dir) throws Exception {
        java.io.File file = Files.createFile(dir.resolve("later.sce")).toFile();
        FileSelector c = new FileSelector(el("FileSelector"));
        c.refresh(el("FileSelector", "href", file.getAbsolutePath()));
        assertEquals(file.getAbsolutePath(), c.href());
    }
}
