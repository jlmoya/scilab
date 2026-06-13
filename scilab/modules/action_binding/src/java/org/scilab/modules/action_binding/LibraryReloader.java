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

package org.scilab.modules.action_binding;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.scilab.modules.commons.filemonitor.FileSystemMonitor;

/**
 * Interpreter-tier hot reload: watches the source directories of loaded macro
 * libraries and, when a {@code .sci} changes (e.g. edited by Claude in the
 * embedded terminal, or by a build), re-runs {@code genlib} so the updated
 * function is live in the interpreter - not just on disk.
 *
 * The reload is queued through {@link InterpreterManagement#putCommandInScilabQueue}
 * so it runs at the interpreter prompt (idle-safe; workspace variables are
 * untouched, a function mid-execution finishes on the old code). The queued
 * command runs at the TOP-LEVEL scope, which is required: clearing a library /
 * function only busts the cache at the scope where {@code clear} runs, and
 * {@code genlib} needs the stale {@code .bin}/{@code lib}/{@code names} removed
 * and the library + its changed functions cleared before it picks up new code.
 *
 * @author Jose Moya
 */
public final class LibraryReloader {

    /** How long to coalesce a burst of saves before reloading a library. */
    private static final long COALESCE_MS = 400;

    private static final LibraryReloader INSTANCE = new LibraryReloader();

    private final FileSystemMonitor monitor = FileSystemMonitor.getInstance();
    private final Map<Path, String> dirToLib = new ConcurrentHashMap<Path, String>();
    private final Map<Path, FileSystemMonitor.FileSystemListener> listeners =
        new ConcurrentHashMap<Path, FileSystemMonitor.FileSystemListener>();
    private final Map<Path, Set<String>> pendingFuncs = new ConcurrentHashMap<Path, Set<String>>();
    private final Map<Path, ScheduledFuture<?>> pendingFlush = new ConcurrentHashMap<Path, ScheduledFuture<?>>();
    private final ScheduledExecutorService scheduler =
        Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "scilab-library-reloader");
                t.setDaemon(true);
                return t;
            }
        });

    private volatile boolean enabled = true;

    private LibraryReloader() { }

    public static LibraryReloader getInstance() {
        return INSTANCE;
    }

    private static Path key(String dir) {
        return Paths.get(dir).toAbsolutePath().normalize();
    }

    /** Enable / disable auto-reload globally (the "Off" mode). */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Watch {@code dir} (the macros source directory of library {@code libName})
     * and hot-reload it when a {@code .sci} under it changes.
     */
    public synchronized void watch(String libName, String dir) {
        if (libName == null || dir == null) {
            return;
        }
        final Path k = key(dir);
        if (!k.toFile().isDirectory()) {
            return;
        }
        dirToLib.put(k, libName);
        if (!listeners.containsKey(k)) {
            FileSystemMonitor.FileSystemListener l = new FileSystemMonitor.FileSystemListener() {
                @Override
                public void fileChanged(FileSystemMonitor.FileChangeEvent event) {
                    onEvent(k, event);
                }
            };
            listeners.put(k, l);
            monitor.subscribe(k, l);
        }
    }

    /** Stop watching {@code dir}. */
    public synchronized void unwatch(String dir) {
        if (dir == null) {
            return;
        }
        final Path k = key(dir);
        FileSystemMonitor.FileSystemListener l = listeners.remove(k);
        if (l != null) {
            monitor.unsubscribe(k, l);
        }
        dirToLib.remove(k);
        pendingFuncs.remove(k);
    }

    private void onEvent(Path dir, FileSystemMonitor.FileChangeEvent event) {
        if (!enabled) {
            return;
        }
        String fileName = event.getPath().getFileName().toString();
        if (!fileName.endsWith(".sci")) {
            return; // ignore genlib's own .bin / lib / names writes
        }
        String func = fileName.substring(0, fileName.length() - ".sci".length());
        synchronized (pendingFuncs) {
            Set<String> set = pendingFuncs.get(dir);
            if (set == null) {
                set = new HashSet<String>();
                pendingFuncs.put(dir, set);
            }
            set.add(func);
        }
        scheduleFlush(dir);
    }

    private synchronized void scheduleFlush(final Path dir) {
        ScheduledFuture<?> prev = pendingFlush.get(dir);
        if (prev != null) {
            prev.cancel(false);
        }
        pendingFlush.put(dir, scheduler.schedule(new Runnable() {
            public void run() {
                flush(dir);
            }
        }, COALESCE_MS, TimeUnit.MILLISECONDS));
    }

    private void flush(Path dir) {
        String lib = dirToLib.get(dir);
        Set<String> funcs;
        synchronized (pendingFuncs) {
            funcs = pendingFuncs.remove(dir);
        }
        if (lib == null || funcs == null || funcs.isEmpty()) {
            return;
        }
        InterpreterManagement.putCommandInScilabQueue(buildReloadCommand(lib, dir.toString(), funcs));
    }

    /**
     * Build the raw, top-level reload command (proven recipe): drop the stale
     * built artifacts, clear the library and its changed functions, then genlib +
     * load. Names are single-quote escaped for Scilab strings.
     */
    static String buildReloadCommand(String lib, String dir, Set<String> funcs) {
        String d = q(dir);
        String l = q(lib);
        StringBuilder cmd = new StringBuilder();
        for (String f : funcs) {
            cmd.append("mdelete('").append(d).append("/").append(q(f)).append(".bin');");
        }
        cmd.append("mdelete('").append(d).append("/lib');");
        cmd.append("mdelete('").append(d).append("/names');");
        cmd.append("clear('").append(l).append("');");
        for (String f : funcs) {
            cmd.append("clear('").append(q(f)).append("');");
        }
        cmd.append("genlib('").append(l).append("','").append(d).append("',%t,%f);");
        cmd.append("load('").append(d).append("/lib');");
        cmd.append("mprintf('Reloaded macro library %s.\\n','").append(l).append("');");
        return cmd.toString();
    }

    /** Escape a value for embedding inside a Scilab single-quoted string. */
    private static String q(String s) {
        return s.replace("'", "''");
    }
}
