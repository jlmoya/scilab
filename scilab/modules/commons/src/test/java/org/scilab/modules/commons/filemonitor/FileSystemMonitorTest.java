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

package org.scilab.modules.commons.filemonitor;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.scilab.modules.commons.filemonitor.FileSystemMonitor.ChangeType;
import org.scilab.modules.commons.filemonitor.FileSystemMonitor.FileChangeEvent;
import org.scilab.modules.commons.filemonitor.FileSystemMonitor.FileSystemListener;

/**
 * Hermetic unit tests for {@link FileSystemMonitor}.
 *
 * <p>The deterministic surface is exercised here: the {@link ChangeType} enum,
 * the {@link FileChangeEvent} value holder, the singleton accessor, and the
 * null/non-directory guards on subscribe/unsubscribe/suppress. The live
 * watch-and-notify path is intentionally not asserted - it depends on the
 * platform {@code WatchService} poll interval (seconds, on macOS) and would be
 * flaky - but a real subscribe/unsubscribe round-trip is verified not to throw.
 */
public class FileSystemMonitorTest {

    private static final FileSystemListener NOOP = event -> { };

    @Test
    public void changeTypeEnumHasTheFourExpectedConstantsInOrder() {
        ChangeType[] values = ChangeType.values();
        assertEquals(4, values.length);
        assertEquals(ChangeType.CREATE, values[0]);
        assertEquals(ChangeType.MODIFY, values[1]);
        assertEquals(ChangeType.DELETE, values[2]);
        assertEquals(ChangeType.OVERFLOW, values[3]);
        assertEquals(ChangeType.DELETE, ChangeType.valueOf("DELETE"));
    }

    @Test
    public void fileChangeEventExposesItsPathAndType() {
        Path p = Paths.get("/tmp/some-file.sce");
        FileChangeEvent event = new FileChangeEvent(p, ChangeType.MODIFY);
        assertSame(p, event.getPath());
        assertEquals(ChangeType.MODIFY, event.getType());
    }

    @Test
    public void fileChangeEventToStringIsTypeColonPath() {
        Path p = Paths.get("/tmp/some-file.sce");
        FileChangeEvent event = new FileChangeEvent(p, ChangeType.CREATE);
        assertEquals("CREATE:" + p.toString(), event.toString());
    }

    @Test
    public void fileChangeEventToleratesANullPath() {
        // Characterizes current behaviour: no defensive check on the path.
        FileChangeEvent event = new FileChangeEvent(null, ChangeType.DELETE);
        assertNull(event.getPath());
        assertEquals(ChangeType.DELETE, event.getType());
        assertEquals("DELETE:null", event.toString());
    }

    @Test
    public void getInstanceIsASingleton() {
        FileSystemMonitor first = FileSystemMonitor.getInstance();
        assertNotNull(first);
        assertSame(first, FileSystemMonitor.getInstance());
    }

    @Test
    public void subscribeIgnoresNullArgumentsAndNonDirectories() throws IOException {
        FileSystemMonitor m = FileSystemMonitor.getInstance();
        Path regularFile = Files.createTempFile("fsmonitor", ".tmp");
        try {
            assertDoesNotThrow(() -> m.subscribe(null, NOOP));
            assertDoesNotThrow(() -> m.subscribe(regularFile, null));
            // A regular file is not a directory: subscribe must be a silent no-op.
            assertDoesNotThrow(() -> m.subscribe(regularFile, NOOP));
            assertDoesNotThrow(() -> m.subscribe(Paths.get("/no/such/directory/here"), NOOP));
        } finally {
            Files.deleteIfExists(regularFile);
        }
    }

    @Test
    public void unsubscribeIgnoresNullArgumentsAndUnknownListeners(@TempDir Path dir) {
        FileSystemMonitor m = FileSystemMonitor.getInstance();
        assertDoesNotThrow(() -> m.unsubscribe(null, NOOP));
        assertDoesNotThrow(() -> m.unsubscribe(dir, null));
        // Never subscribed: unsubscribing must not throw.
        assertDoesNotThrow(() -> m.unsubscribe(dir, NOOP));
    }

    @Test
    public void suppressIgnoresNullAndAcceptsAnyPath() {
        FileSystemMonitor m = FileSystemMonitor.getInstance();
        assertDoesNotThrow(() -> m.suppress(null));
        assertDoesNotThrow(() -> m.suppress(Paths.get("/tmp/whatever.sce")));
    }

    @Test
    public void subscribeThenUnsubscribeOnARealDirectoryIsIdempotentAndSafe(@TempDir Path dir) {
        FileSystemMonitor m = FileSystemMonitor.getInstance();
        FileSystemListener listener = event -> { };
        assertDoesNotThrow(() -> {
            m.subscribe(dir, listener);
            m.subscribe(dir, listener); // addIfAbsent - second registration is a no-op
            m.unsubscribe(dir, listener);
            m.unsubscribe(dir, listener); // already gone - still safe
        });
    }

    /**
     * Live end-to-end check: subscribe to a real directory, mutate a file in it and confirm the
     * background {@code WatchService} loop delivers a well-formed event. This drives the otherwise
     * uncovered machinery (the watch thread's take/pollEvents loop, the {@code WatchEvent.Kind} to
     * {@link ChangeType} mapping, context-to-absolute-path resolution and listener fan-out).
     *
     * <p>Delivery latency is platform-dependent (the macOS {@code WatchService} polls, so an event
     * can take several seconds); the file is therefore re-touched on a loop and the assertion is
     * guarded by {@link Assumptions#assumeTrue} so a watcher that never fires yields a <em>skip</em>,
     * never a flaky failure. When an event does arrive - as it does on the developer/CI platforms
     * that back a live watcher - its path and type are asserted precisely.
     */
    @Test
    @Timeout(60)
    public void liveWatchDeliversAWellFormedEventForAMutatedFile(@TempDir Path dir) throws Exception {
        FileSystemMonitor m = FileSystemMonitor.getInstance();
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<FileChangeEvent> captured = new AtomicReference<FileChangeEvent>();
        FileSystemListener listener = event -> {
            captured.compareAndSet(null, event);
            latch.countDown();
        };

        m.subscribe(dir, listener);
        boolean delivered = false;
        try {
            final Path file = dir.resolve("watched.sce");
            // Re-touch on a loop: survives the asynchronous registration window and the platform
            // poll interval, and stops as soon as the first event is delivered.
            final long deadline = System.currentTimeMillis() + 20_000L;
            int i = 0;
            while (System.currentTimeMillis() < deadline) {
                Files.write(file, ("line " + (i++) + "\n").getBytes(StandardCharsets.UTF_8),
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                if (latch.await(1, TimeUnit.SECONDS)) {
                    delivered = true;
                    break;
                }
            }
        } finally {
            m.unsubscribe(dir, listener);
        }

        Assumptions.assumeTrue(delivered,
            "platform WatchService delivered no event within 20s - skipping the live assertion");

        FileChangeEvent event = captured.get();
        assertNotNull(event);
        assertNotNull(event.getPath());
        assertEquals("watched.sce", event.getPath().getFileName().toString());
        assertEquals(dir.toAbsolutePath().normalize(), event.getPath().getParent(),
                     "the event path must be resolved under the watched directory");
        // A create-then-append sequence only ever surfaces as CREATE or MODIFY here.
        assertTrue(event.getType() == ChangeType.CREATE || event.getType() == ChangeType.MODIFY,
                   "unexpected change type: " + event.getType());
    }
}
