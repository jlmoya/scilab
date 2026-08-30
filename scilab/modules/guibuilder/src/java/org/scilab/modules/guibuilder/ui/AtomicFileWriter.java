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

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Writes bytes to a file without ever leaving it half-written.
 *
 * <p>{@code Files.write(target, bytes)} opens {@code target} with {@code
 * TRUNCATE_EXISTING}, which empties it as part of the same call that then
 * writes the new bytes. Truncating and writing are two steps sharing one
 * file: if anything stops the second -- a full disk, a revoked permission, a
 * killed process -- the first has already happened, and the file that used
 * to hold the user's design now holds nothing.
 *
 * <p>This writes the new bytes to a sibling temporary file first, then
 * swaps it into place with {@link Files#move}. {@code target} is never
 * opened for writing at all: either the swap fully succeeds, or {@code
 * target} is exactly what it was before this method was called. The
 * temporary file is created in {@code target}'s own directory rather than
 * the platform's shared temp directory, because {@link
 * StandardCopyOption#ATOMIC_MOVE} is only atomic -- and on most systems only
 * possible at all -- between two paths on the same file store.
 */
final class AtomicFileWriter {

    private AtomicFileWriter() {
    }

    /**
     * @param target the file to replace; untouched unless this method returns normally
     * @param bytes the complete new content
     * @throws IOException if the replacement could not be produced or swapped in;
     *                      {@code target} is guaranteed unchanged in that case
     */
    static void write(Path target, byte[] bytes) throws IOException {
        Path directory = target.toAbsolutePath().getParent();
        Path tmp = Files.createTempFile(directory, target.getFileName().toString(), ".tmp");
        try {
            Files.write(tmp, bytes);
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                // Same-directory moves are atomic on every filesystem this
                // matters for (see the class javadoc); this fallback is for
                // the rare filesystem that cannot do even that, not for a
                // cross-filesystem move -- target's own directory is always
                // where tmp was just created.
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            Files.deleteIfExists(tmp);
            throw e;
        }
    }
}
