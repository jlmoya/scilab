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

import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.TtyConnector;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Adapts our JNA {@link Pty} to JediTerm's {@link TtyConnector}.
 *
 * JediTerm reads CHARS (it expects the connector to decode), so we wrap the PTY's
 * raw bytes in an InputStreamReader(UTF-8) - the same approach JediTerm's own
 * ProcessTtyConnector uses (which we can't extend, since it requires a java.lang.Process
 * and we posix_spawn'd instead).
 */
public final class PtyTtyConnector implements TtyConnector {
    private final Pty pty;
    private final InputStream in;
    private final InputStreamReader reader;
    private volatile boolean connected = true;

    public PtyTtyConnector(Pty pty) {
        this.pty = pty;
        this.in = new InputStream() {
            @Override public int read() throws IOException {
                byte[] b = new byte[1];
                int n = read(b, 0, 1);
                return n <= 0 ? -1 : (b[0] & 0xff);
            }
            @Override public int read(byte[] b, int off, int len) {
                int n = pty.read(b, off, len);
                if (n <= 0) {
                    connected = false;
                    return -1;
                }
                return n;
            }
            @Override public int available() {
                return pty.available();
            }
        };
        this.reader = new InputStreamReader(in, StandardCharsets.UTF_8);
    }

    @Override public int read(char[] buf, int offset, int length) throws IOException {
        return reader.read(buf, offset, length);
    }

    @Override public void write(byte[] bytes) {
        pty.writeBytes(bytes, 0, bytes.length);
    }

    @Override public void write(String string) {
        write(string.getBytes(StandardCharsets.UTF_8));
    }

    @Override public boolean isConnected() {
        return connected;
    }

    @Override public boolean ready() throws IOException {
        return reader.ready() || pty.available() > 0;
    }

    @Override public void resize(TermSize size) {
        pty.setWinSize(size.getRows(), size.getColumns());
    }

    @Override public int waitFor() {
        return pty.waitFor();
    }

    @Override public String getName() {
        return "scilab-terminal";
    }

    @Override public void close() {
        connected = false;
        pty.close();
    }
}
