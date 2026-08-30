/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 *
 */

package org.scilab.modules.guibuilder.ui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pins the one property {@link AtomicFileWriter} exists for: a save that
 * cannot complete must never leave the original file damaged.
 *
 * <p>The read-only-directory setup below is chosen for a precise, measured
 * reason rather than the obvious-looking one. A plain {@code Files.write}
 * onto a file that already exists and is itself still writable does NOT
 * fail when only its parent directory is read-only -- directory permissions
 * gate creating, deleting or renaming entries, not rewriting the bytes of a
 * file you can already open (verified directly against this JDK: {@code
 * Files.write} happily truncates and overwrites an existing file inside a
 * read-only directory). What a read-only directory reliably blocks is
 * creating a *new* entry -- exactly the temp file this class's safe path
 * depends on. So this is a faithful test of this class's own guarantee
 * (nothing is touched unless the replacement can be produced and swapped
 * in), even though it is not a reproduction of the original bug report's
 * literal "truncated then failed" sequence -- that specific interleaving
 * (truncate succeeds, the subsequent write does not) needs a fault
 * mid-write, such as a full disk or a killed process, that a permission bit
 * cannot simulate portably.
 */
public class AtomicFileWriterTest {

    private static final byte[] ORIGINAL = "the original, unsaved content".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path dir;

    private Path target;

    @BeforeEach
    public void createOriginalFile() throws IOException {
        target = dir.resolve("design.sce");
        Files.write(target, ORIGINAL);
    }

    @AfterEach
    public void restoreWritePermissionSoJUnitCanCleanUp() {
        dir.toFile().setWritable(true, false);
    }

    @Test
    public void writingReplacesTheContentExactly() throws Exception {
        byte[] replacement = "the new content".getBytes(StandardCharsets.UTF_8);
        AtomicFileWriter.write(target, replacement);
        assertArrayEquals(replacement, Files.readAllBytes(target));
    }

    @Test
    public void writingLeavesNoTemporaryFileBehindOnSuccess() throws Exception {
        AtomicFileWriter.write(target, "replacement".getBytes(StandardCharsets.UTF_8));
        try (var listing = Files.list(dir)) {
            assertEquals(1, listing.count(), "only the target file should remain in " + dir);
        }
    }

    /**
     * The regression this class exists to fix. Before {@code AtomicFileWriter}
     * existed, {@code GuiDesignerTab.onSave()} called {@code Files.write}
     * directly on the target path: under this exact setup that call does not
     * throw at all (see the class javadoc) and silently replaces the file's
     * content -- so a bug here would surface as a wrong-content write with no
     * error raised, not as a stack trace. Both assertions below are the ones
     * that catch that: a real failure must be reported, AND the original must
     * survive it untouched.
     */
    @Test
    public void aFailedWriteLeavesTheOriginalFileCompletelyUntouched() {
        dir.toFile().setWritable(false, false);

        assertThrows(IOException.class,
                     () -> AtomicFileWriter.write(target, "should never land".getBytes(StandardCharsets.UTF_8)));

        dir.toFile().setWritable(true, false);
        assertArrayEquals(ORIGINAL, readQuietly(target), "a failed save must not touch the original bytes");
    }

    @Test
    public void aFailedMoveCleansUpItsOwnTemporaryFile() throws Exception {
        // A regular file can never be moved onto an existing, non-empty
        // directory: this fails the *move* specifically, after the temp file
        // has already been written successfully -- the other half of "clean
        // up the temp file if the move fails" that the read-only-directory
        // test above cannot reach (there, temp-file creation itself is what
        // fails, so there is nothing yet to clean up).
        Path targetIsActuallyADirectory = dir.resolve("adirectory");
        Files.createDirectory(targetIsActuallyADirectory);
        Files.createFile(targetIsActuallyADirectory.resolve("occupant"));

        assertThrows(IOException.class,
                     () -> AtomicFileWriter.write(targetIsActuallyADirectory, "x".getBytes(StandardCharsets.UTF_8)));

        try (var listing = Files.list(dir)) {
            assertTrue(listing.allMatch(p -> p.equals(target) || p.equals(targetIsActuallyADirectory)),
                       "no stray temp file should remain after a failed move");
        }
    }

    private static byte[] readQuietly(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new AssertionError("could not read back " + path, e);
        }
    }
}
