/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Scilab Enterprises
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 *
 */

package org.scilab.modules.graphic_export;

import java.io.File;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * Hermetic unit tests for {@link Utils#checkWritePermission(File)}.
 *
 * The {@code Export.SUCCESS} / {@code Export.INVALID_FILE} /
 * {@code Export.IOEXCEPTION_ERROR} status codes are compile-time constants
 * (inlined by javac), so referencing them here does NOT load the heavyweight
 * {@code Export} class or any of the rendering stack — the tests stay hermetic.
 */
public class UtilsTest {

    @TempDir
    File tempDir;

    @Test
    public void creatingAFreshFileInAWritableDirectorySucceeds() {
        File f = new File(tempDir, "fresh.png");
        assertFalse(f.exists());
        assertEquals(Export.SUCCESS, Utils.checkWritePermission(f));
        // Side effect: the file is actually created by createNewFile().
        assertTrue(f.isFile());
    }

    @Test
    public void anAlreadyExistingWritableFileStillSucceeds() throws Exception {
        File f = new File(tempDir, "exists.png");
        assertTrue(f.createNewFile());
        // createNewFile() now returns false (already there) but no exception,
        // and the file remains writable => SUCCESS.
        assertEquals(Export.SUCCESS, Utils.checkWritePermission(f));
    }

    @Test
    public void aFileWhoseParentDirectoryIsMissingRaisesIoException() {
        // "ghost/" does not exist, so createNewFile() throws IOException.
        File f = new File(tempDir, "ghost" + File.separator + "child.png");
        assertEquals(Export.IOEXCEPTION_ERROR, Utils.checkWritePermission(f));
        assertFalse(f.exists());
    }

    @Test
    public void anExistingReadOnlyFileIsReportedInvalid() throws Exception {
        File f = new File(tempDir, "readonly.png");
        assertTrue(f.createNewFile());
        assertTrue(f.setWritable(false, false));

        // On some environments (e.g. running as root) write bits are ignored;
        // only assert the INVALID_FILE branch when the OS actually honours it.
        assumeFalse(f.canWrite(), "filesystem did not enforce read-only bit");

        assertEquals(Export.INVALID_FILE, Utils.checkWritePermission(f));

        // Restore so @TempDir cleanup can delete it.
        f.setWritable(true, false);
    }
}
