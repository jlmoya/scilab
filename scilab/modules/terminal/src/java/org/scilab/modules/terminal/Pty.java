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

import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.StringArray;
import com.sun.jna.ptr.IntByReference;

import java.io.IOException;

/**
 * Minimal POSIX PTY over libSystem via JNA - no pty4j, no forkpty.
 *
 * We avoid forkpty() because it fork()s the multi-threaded JVM and then runs JVM
 * bytecode in the child before exec (unsafe). Instead: posix_openpt() to get a master,
 * then posix_spawn() (an atomic fork+exec in C) with file actions that open the slave
 * as fd 0/1/2 under a new session (POSIX_SPAWN_SETSID) so it becomes the controlling tty.
 *
 * Ported from the Phase-1 terminal spike; proven on macOS arm64 + JDK 25.
 */
public final class Pty {

    public interface C extends Library {
        C I = Native.load("c", C.class); // libSystem.B.dylib on macOS

        int posix_openpt(int flags);
        int grantpt(int fd);
        int unlockpt(int fd);
        Pointer ptsname(int fd);

        int posix_spawn(IntByReference pid, String path,
                        Pointer file_actions, Pointer attrp,
                        StringArray argv, StringArray envp);
        int posix_spawn_file_actions_init(Pointer fa);
        int posix_spawn_file_actions_destroy(Pointer fa);
        int posix_spawn_file_actions_addopen(Pointer fa, int fildes, String path, int oflag, int mode);
        int posix_spawn_file_actions_adddup2(Pointer fa, int fildes, int newfildes);
        int posix_spawn_file_actions_addclose(Pointer fa, int fildes);
        int posix_spawnattr_init(Pointer attr);
        int posix_spawnattr_destroy(Pointer attr);
        int posix_spawnattr_setflags(Pointer attr, short flags);

        long read(int fd, Pointer buf, long nbyte);
        long write(int fd, Pointer buf, long nbyte);
        int close(int fd);
        // ioctl is variadic: on arm64 the third arg MUST be passed as a vararg (stack),
        // not a fixed register arg, or JNA corrupts memory. Declare it with Object...
        int ioctl(int fd, long request, Object... args);
        int waitpid(int pid, IntByReference status, int options);
        int kill(int pid, int sig);
        String strerror(int errnum);
    }

    // Darwin constants
    private static final int   O_RDWR             = 0x0002;
    private static final int   O_NOCTTY           = 0x20000;
    private static final short POSIX_SPAWN_SETSID = 0x0400;
    private static final long  TIOCSWINSZ         = 0x80087467L;
    private static final long  FIONREAD           = 0x4004667FL;
    private static final int   SIGHUP             = 1;
    private static final int   SIGKILL            = 9;
    private static final int   WNOHANG            = 1;

    private int masterFd = -1;
    private int childPid = -1;

    /** Spawn {@code shell} with the given argv/envp on a fresh PTY of size rows x cols. */
    public void start(String shell, String[] argv, String[] envp, int rows, int cols) throws IOException {
        C c = C.I;
        int master = c.posix_openpt(O_RDWR | O_NOCTTY);
        if (master < 0) {
            throw new IOException("posix_openpt failed");
        }
        if (c.grantpt(master) != 0) {
            throw new IOException("grantpt failed");
        }
        if (c.unlockpt(master) != 0) {
            throw new IOException("unlockpt failed");
        }
        Pointer namePtr = c.ptsname(master);
        if (namePtr == null) {
            throw new IOException("ptsname returned null");
        }
        String slavePath = namePtr.getString(0);

        // posix_spawn_file_actions_t / posix_spawnattr_t are pointer-sized opaque on Darwin.
        Memory fa = new Memory(Native.POINTER_SIZE);
        Memory attr = new Memory(Native.POINTER_SIZE);
        c.posix_spawn_file_actions_init(fa);
        c.posix_spawnattr_init(attr);
        c.posix_spawnattr_setflags(attr, POSIX_SPAWN_SETSID);
        c.posix_spawn_file_actions_addclose(fa, master);
        // Opening the slave by path as fd 0 in the new session makes it the controlling tty.
        c.posix_spawn_file_actions_addopen(fa, 0, slavePath, O_RDWR, 0);
        c.posix_spawn_file_actions_adddup2(fa, 0, 1);
        c.posix_spawn_file_actions_adddup2(fa, 0, 2);

        IntByReference pidRef = new IntByReference();
        StringArray argvArr = new StringArray(nullTerminate(argv));
        StringArray envpArr = new StringArray(nullTerminate(envp));
        int rc = c.posix_spawn(pidRef, shell, fa, attr, argvArr, envpArr);
        c.posix_spawn_file_actions_destroy(fa);
        c.posix_spawnattr_destroy(attr);
        if (rc != 0) {
            throw new IOException("posix_spawn failed: rc=" + rc + " (" + c.strerror(rc) + ")");
        }

        this.masterFd = master;
        this.childPid = pidRef.getValue();
        setWinSize(rows, cols);
    }

    private static String[] nullTerminate(String[] a) {
        String[] r = new String[a.length + 1];
        System.arraycopy(a, 0, r, 0, a.length);
        r[a.length] = null; // NULL pointer terminator for argv/envp
        return r;
    }

    public void setWinSize(int rows, int cols) {
        Memory ws = new Memory(8); // struct winsize { u_short row, col, xpixel, ypixel; }
        ws.setShort(0, (short) rows);
        ws.setShort(2, (short) cols);
        ws.setShort(4, (short) 0);
        ws.setShort(6, (short) 0);
        C.I.ioctl(masterFd, TIOCSWINSZ, ws);
    }

    /** Read up to len bytes into buf[off..]; returns count, or &lt;=0 at EOF. */
    public int read(byte[] buf, int off, int len) {
        Memory m = new Memory(len);
        long n = C.I.read(masterFd, m, len);
        if (n > 0) {
            m.read(0, buf, off, (int) n);
        }
        return (int) n;
    }

    /** Bytes immediately available on the master (FIONREAD); 0 if none/unknown. */
    public int available() {
        Memory m = new Memory(4);
        return C.I.ioctl(masterFd, FIONREAD, m) == 0 ? m.getInt(0) : 0;
    }

    public void writeBytes(byte[] data, int off, int len) {
        Memory m = new Memory(len);
        m.write(0, data, off, len);
        C.I.write(masterFd, m, len);
    }

    public int waitFor() {
        IntByReference status = new IntByReference();
        C.I.waitpid(childPid, status, 0);
        return status.getValue();
    }

    public void close() {
        if (masterFd >= 0) {
            C.I.close(masterFd);
            masterFd = -1;
        }
    }

    /**
     * Terminate the child shell and release the PTY. SIGHUP the child (a login
     * shell hangs up and exits), close the master fd (which both hangs up the
     * slave and unblocks any thread blocked in read() with EOF), then reap the
     * child - SIGKILL as a last resort if it ignored SIGHUP. Idempotent and
     * safe to call from any thread (e.g. a JVM shutdown hook). Without this the
     * orphaned shell + a reader blocked on the master fd keep the JVM from
     * exiting at Scilab quit.
     */
    public synchronized void terminate() {
        final int pid = childPid;
        if (pid > 0) {
            C.I.kill(pid, SIGHUP);
        }
        close();
        if (pid > 0) {
            IntByReference status = new IntByReference();
            if (C.I.waitpid(pid, status, WNOHANG) == 0) {
                // still alive after SIGHUP -> force it and reap
                C.I.kill(pid, SIGKILL);
                C.I.waitpid(pid, status, 0);
            }
            childPid = -1;
        }
    }

    public int masterFd() {
        return masterFd;
    }

    public int pid() {
        return childPid;
    }
}
