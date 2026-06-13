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

package org.scilab.modules.commons.filemonitor;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Process-wide file-system monitor shared by the Scilab GUI components (SciNotes,
 * and - via the interpreter - loaded library reload).
 *
 * Each subscriber registers a {@link FileSystemListener} for a single directory
 * and is notified (off the EDT - each listener must marshal its own Swing work)
 * of changes to files <em>directly</em> in that directory. Watching is
 * deliberately <strong>non-recursive</strong>: the subscribers only care about
 * the files in the directory they name (SciNotes watches the parent directory of
 * its open files; the library reloader watches a macros directory), so there is
 * no reason to descend. A recursive watcher would, when handed a large tree (a
 * project root, {@code $HOME}, ...), walk and watch the whole subtree - tens of
 * seconds of CPU and a flood of irrelevant events.
 *
 * It uses the JDK {@link WatchService}. On Linux this is inotify-backed (prompt);
 * on macOS it polls the watched directories - cheap, because only single
 * directories are registered, never trees. Best-effort high sensitivity reduces
 * the macOS poll interval when available.
 *
 * Two hygiene mechanisms avoid feedback loops and bursts:
 * <ul>
 *   <li>{@link #suppress(Path)} - a short window during which events for a given
 *       path are dropped, so Scilab's own writes (e.g. a SciNotes save) do not
 *       bounce back as "external" changes;</li>
 *   <li>a per-(path,type) debounce that coalesces the rapid duplicate events a
 *       single save typically produces.</li>
 * </ul>
 *
 * @author Jose Moya
 */
public final class FileSystemMonitor {

    /** Kind of change reported to listeners. */
    public enum ChangeType { CREATE, MODIFY, DELETE, OVERFLOW }

    /** A single change in a watched directory. */
    public static final class FileChangeEvent {
        private final Path path;
        private final ChangeType type;

        public FileChangeEvent(Path path, ChangeType type) {
            this.path = path;
            this.type = type;
        }

        /** @return the absolute path that changed. */
        public Path getPath() {
            return path;
        }

        /** @return the kind of change. */
        public ChangeType getType() {
            return type;
        }

        @Override
        public String toString() {
            return type + ":" + path;
        }
    }

    /** Subscriber callback. Invoked off the EDT. */
    public interface FileSystemListener {
        void fileChanged(FileChangeEvent event);
    }

    private static final long SUPPRESS_WINDOW_MS = 4000;
    private static final long DEBOUNCE_MS = 150;

    private static final FileSystemMonitor INSTANCE = new FileSystemMonitor();

    // Best-effort high sensitivity for the (polling) macOS WatchService; an empty
    // array (default sensitivity) on any JDK where the modifier is unavailable.
    private static final WatchEvent.Modifier[] SENSITIVITY = highSensitivity();

    private final Map<Path, WatchKey> keysByRoot = new ConcurrentHashMap<Path, WatchKey>();
    private final Map<WatchKey, Path> rootsByKey = new ConcurrentHashMap<WatchKey, Path>();
    private final Map<Path, CopyOnWriteArrayList<FileSystemListener>> listeners =
        new ConcurrentHashMap<Path, CopyOnWriteArrayList<FileSystemListener>>();
    private final Map<Path, Long> suppressedUntil = new ConcurrentHashMap<Path, Long>();
    private final Map<String, Long> lastDispatch = new ConcurrentHashMap<String, Long>();

    // Registration is done off the caller thread (callers are often on the Swing
    // EDT, e.g. SciNotes loadFile): registering a directory snapshots its entries,
    // which must never block the UI.
    private final ExecutorService registerExecutor =
        Executors.newSingleThreadExecutor(new ThreadFactory() {
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "scilab-fsmonitor-register");
                t.setDaemon(true);
                return t;
            }
        });

    private volatile WatchService watchService;

    private FileSystemMonitor() { }

    public static FileSystemMonitor getInstance() {
        return INSTANCE;
    }

    @SuppressWarnings("unchecked")
    private static WatchEvent.Modifier[] highSensitivity() {
        try {
            WatchEvent.Modifier high =
                (WatchEvent.Modifier) Enum.valueOf(
                    (Class<? extends Enum>) Class.forName("com.sun.nio.file.SensitivityWatchEventModifier"),
                    "HIGH");
            return new WatchEvent.Modifier[] { high };
        } catch (Throwable ignore) {
            return new WatchEvent.Modifier[0];
        }
    }

    private static Path normalize(Path p) {
        return p.toAbsolutePath().normalize();
    }

    private synchronized void ensureStarted() {
        if (watchService != null) {
            return;
        }
        try {
            watchService = FileSystems.getDefault().newWatchService();
        } catch (IOException ex) {
            System.err.println("FileSystemMonitor: cannot create watch service: " + ex);
            return;
        }
        Thread t = new Thread(new Runnable() {
            public void run() {
                runLoop();
            }
        }, "scilab-fsmonitor");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Watch the directory {@code root} (non-recursively) and deliver changes to
     * files directly in it to {@code listener}. Several listeners may share a
     * directory (one watch key is used per directory).
     * @param root the directory to watch (must exist and be a directory)
     * @param listener the callback
     */
    public synchronized void subscribe(Path root, FileSystemListener listener) {
        if (root == null || listener == null) {
            return;
        }
        final Path key = normalize(root);
        if (!key.toFile().isDirectory()) {
            return;
        }
        listeners.computeIfAbsent(key, k -> new CopyOnWriteArrayList<FileSystemListener>()).addIfAbsent(listener);
        ensureStarted();
        if (watchService != null && !keysByRoot.containsKey(key)) {
            registerExecutor.submit(new Runnable() {
                public void run() {
                    register(key);
                }
            });
        }
    }

    private void register(Path key) {
        WatchService ws = watchService;
        if (ws == null) {
            return;
        }
        WatchKey wk;
        try {
            wk = key.register(ws,
                              new WatchEvent.Kind[] {
                                  StandardWatchEventKinds.ENTRY_CREATE,
                                  StandardWatchEventKinds.ENTRY_MODIFY,
                                  StandardWatchEventKinds.ENTRY_DELETE
                              },
                              SENSITIVITY);
        } catch (Throwable ex) {
            System.err.println("FileSystemMonitor: cannot watch " + key + ": " + ex);
            return;
        }
        synchronized (this) {
            if (!listeners.containsKey(key)) {
                // unsubscribed while we were registering - drop the key
                wk.cancel();
                return;
            }
            keysByRoot.put(key, wk);
            rootsByKey.put(wk, key);
        }
    }

    /**
     * Stop delivering changes in {@code root} to {@code listener}; cancels the
     * watch key when its last listener leaves.
     */
    public synchronized void unsubscribe(Path root, FileSystemListener listener) {
        if (root == null || listener == null) {
            return;
        }
        final Path key = normalize(root);
        CopyOnWriteArrayList<FileSystemListener> ls = listeners.get(key);
        if (ls != null) {
            ls.remove(listener);
            if (ls.isEmpty()) {
                listeners.remove(key);
                WatchKey wk = keysByRoot.remove(key);
                if (wk != null) {
                    rootsByKey.remove(wk);
                    wk.cancel();
                }
            }
        }
    }

    /**
     * Drop change events for {@code path} for a short window. Call this right
     * before Scilab writes the file itself so the resulting event is not reported
     * back as an external change.
     */
    public void suppress(Path path) {
        if (path != null) {
            suppressedUntil.put(normalize(path), System.currentTimeMillis() + SUPPRESS_WINDOW_MS);
        }
    }

    private boolean isSuppressed(Path path, long now) {
        Long until = suppressedUntil.get(path);
        if (until == null) {
            return false;
        }
        if (now > until) {
            suppressedUntil.remove(path);
            return false;
        }
        return true;
    }

    private static ChangeType map(WatchEvent.Kind<?> kind) {
        if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
            return ChangeType.CREATE;
        }
        if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
            return ChangeType.MODIFY;
        }
        if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
            return ChangeType.DELETE;
        }
        return ChangeType.OVERFLOW;
    }

    private void runLoop() {
        final WatchService ws = watchService;
        while (true) {
            WatchKey wk;
            try {
                wk = ws.take();
            } catch (InterruptedException ie) {
                return;
            } catch (ClosedWatchServiceException ce) {
                return;
            }
            Path root = rootsByKey.get(wk);
            for (WatchEvent<?> event : wk.pollEvents()) {
                ChangeType type = map(event.kind());
                Object ctx = event.context();
                Path full = (root != null && ctx instanceof Path)
                            ? normalize(root.resolve((Path) ctx)) : null;
                dispatch(root, full, type);
            }
            if (!wk.reset()) {
                // directory gone or no longer accessible - forget it
                Path gone = rootsByKey.remove(wk);
                if (gone != null) {
                    keysByRoot.remove(gone);
                }
            }
        }
    }

    private void dispatch(Path root, Path path, ChangeType type) {
        if (root == null || path == null) {
            return;
        }
        final long now = System.currentTimeMillis();
        if (isSuppressed(path, now)) {
            return;
        }
        // coalesce the burst of duplicate events a single write produces
        final String dkey = type + " " + path;
        Long last = lastDispatch.get(dkey);
        if (last != null && now - last < DEBOUNCE_MS) {
            return;
        }
        lastDispatch.put(dkey, now);

        final FileChangeEvent fce = new FileChangeEvent(path, type);
        List<FileSystemListener> ls = listeners.get(root);
        if (ls != null) {
            for (FileSystemListener l : ls) {
                try {
                    l.fileChanged(fce);
                } catch (Throwable t) {
                    System.err.println("FileSystemMonitor: listener failed: " + t);
                }
            }
        }
    }
}
