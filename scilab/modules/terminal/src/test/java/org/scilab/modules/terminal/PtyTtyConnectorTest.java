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

package org.scilab.modules.terminal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hermetic unit tests for the pure-Java surface of {@link PtyTtyConnector}, the
 * JediTerm {@code TtyConnector} adapter over our {@link Pty}.
 *
 * <p>The connector is built over a fresh (unstarted) {@link Pty}. Construction only
 * wires an {@code InputStreamReader} over the PTY (no read is issued), {@code
 * getName()} is a constant, {@code isConnected()} reads a flag, and {@code close()}
 * flips that flag then calls {@code pty.close()} - which is a guarded no-op on an
 * unstarted PTY. So none of the exercised paths touch libc. The byte/char I/O and
 * {@code resize()} paths (which do call into the PTY) are deliberately not tested.
 */
public class PtyTtyConnectorTest {

    @Test
    public void nameIsTheStableConnectorId() {
        PtyTtyConnector connector = new PtyTtyConnector(new Pty());
        assertEquals("scilab-terminal", connector.getName());
    }

    @Test
    public void aFreshConnectorReportsConnected() {
        PtyTtyConnector connector = new PtyTtyConnector(new Pty());
        assertTrue(connector.isConnected(), "a newly built connector starts connected");
    }

    @Test
    public void closeMarksDisconnectedAndIsIdempotent() {
        PtyTtyConnector connector = new PtyTtyConnector(new Pty());
        // close() sets connected=false and calls pty.close(); on a fresh PTY that
        // inner close is a guarded no-op, keeping this hermetic.
        connector.close();
        assertFalse(connector.isConnected(), "close() disconnects the connector");

        assertDoesNotThrow(connector::close, "close() is safe to call again");
        assertFalse(connector.isConnected());
    }
}
