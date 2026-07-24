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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link LibraryReloader}.
 *
 * The focus is the pure, package-private {@code buildReloadCommand} string
 * builder (the top-level {@code genlib} reload recipe), plus the singleton /
 * enabled accessors and the null / non-directory guard branches of
 * {@code watch} / {@code unwatch} — none of which touch the interpreter, the
 * filesystem, or start any watch thread.
 */
class LibraryReloaderTest {

    // ------------------------------------------------------------------
    // buildReloadCommand — pure string generation
    // ------------------------------------------------------------------

    @Test
    void singleFunctionEmitsFullRecipe() {
        Set<String> funcs = new LinkedHashSet<String>();
        funcs.add("foo");

        String cmd = LibraryReloader.buildReloadCommand("mylib", "/path/to/dir", funcs);

        assertEquals(
            "mdelete('/path/to/dir/foo.bin');"
          + "mdelete('/path/to/dir/lib');"
          + "mdelete('/path/to/dir/names');"
          + "clear('mylib');"
          + "clear('foo');"
          + "genlib('mylib','/path/to/dir',%t,%f);"
          + "load('/path/to/dir/lib');"
          + "mprintf('Reloaded macro library %s.\\n','mylib');",
            cmd);
    }

    @Test
    void multipleFunctionsFollowIterationOrder() {
        Set<String> funcs = new LinkedHashSet<String>();
        funcs.add("a");
        funcs.add("b");

        String cmd = LibraryReloader.buildReloadCommand("L", "/d", funcs);

        assertEquals(
            "mdelete('/d/a.bin');"
          + "mdelete('/d/b.bin');"
          + "mdelete('/d/lib');"
          + "mdelete('/d/names');"
          + "clear('L');"
          + "clear('a');"
          + "clear('b');"
          + "genlib('L','/d',%t,%f);"
          + "load('/d/lib');"
          + "mprintf('Reloaded macro library %s.\\n','L');",
            cmd);
    }

    @Test
    void singleQuotesAreDoubledForScilabStrings() {
        Set<String> funcs = new LinkedHashSet<String>();
        funcs.add("f'g");

        String cmd = LibraryReloader.buildReloadCommand("it's", "/a'b", funcs);

        assertEquals(
            "mdelete('/a''b/f''g.bin');"
          + "mdelete('/a''b/lib');"
          + "mdelete('/a''b/names');"
          + "clear('it''s');"
          + "clear('f''g');"
          + "genlib('it''s','/a''b',%t,%f);"
          + "load('/a''b/lib');"
          + "mprintf('Reloaded macro library %s.\\n','it''s');",
            cmd);
    }

    @Test
    void emptyFunctionSetStillRebuildsTheLibrary() {
        String cmd = LibraryReloader.buildReloadCommand("L", "/d", Collections.<String>emptySet());

        // No per-function mdelete/clear lines, but the library artifacts are
        // still dropped and genlib + load still run.
        assertEquals(
            "mdelete('/d/lib');"
          + "mdelete('/d/names');"
          + "clear('L');"
          + "genlib('L','/d',%t,%f);"
          + "load('/d/lib');"
          + "mprintf('Reloaded macro library %s.\\n','L');",
            cmd);
    }

    @Test
    void structuralInvariantsHold() {
        Set<String> funcs = new LinkedHashSet<String>();
        funcs.add("one");
        funcs.add("two");
        funcs.add("three");

        String cmd = LibraryReloader.buildReloadCommand("lib", "/dir", funcs);

        assertTrue(cmd.startsWith("mdelete('"), "recipe starts by dropping stale artifacts");
        assertTrue(cmd.endsWith("');"), "recipe ends with a terminated statement");
        // one mdelete per function, plus the lib and names artifacts
        assertEquals(funcs.size() + 2, countOccurrences(cmd, "mdelete('"));
        // one clear per function, plus the library itself
        assertEquals(funcs.size() + 1, countOccurrences(cmd, "clear('"));
        assertEquals(1, countOccurrences(cmd, "genlib('"));
        assertEquals(1, countOccurrences(cmd, "load('"));
        // genlib is always invoked verbose(%t) + no-warn(%f)
        assertTrue(cmd.contains(",%t,%f);"));
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) >= 0) {
            count++;
            idx += needle.length();
        }
        return count;
    }

    // ------------------------------------------------------------------
    // singleton + enabled flag
    // ------------------------------------------------------------------

    @Test
    void getInstanceReturnsTheSameSingleton() {
        LibraryReloader first = LibraryReloader.getInstance();
        assertNotNull(first);
        assertSame(first, LibraryReloader.getInstance());
    }

    @Test
    void isEnabledByDefaultAndCanToggle() {
        LibraryReloader reloader = LibraryReloader.getInstance();
        boolean original = reloader.isEnabled();
        try {
            assertTrue(reloader.isEnabled(), "auto-reload is enabled by default");
            reloader.setEnabled(false);
            assertFalse(reloader.isEnabled());
            reloader.setEnabled(true);
            assertTrue(reloader.isEnabled());
        } finally {
            reloader.setEnabled(original);
        }
    }

    // ------------------------------------------------------------------
    // watch / unwatch guard branches (no live filesystem subscription)
    // ------------------------------------------------------------------

    @Test
    void watchIgnoresNullArguments() {
        LibraryReloader reloader = LibraryReloader.getInstance();
        assertDoesNotThrow(() -> reloader.watch(null, null));
        assertDoesNotThrow(() -> reloader.watch("lib", null));
        assertDoesNotThrow(() -> reloader.watch(null, "/tmp"));
    }

    @Test
    void watchIgnoresNonExistentDirectory() {
        LibraryReloader reloader = LibraryReloader.getInstance();
        // A path that is not an existing directory is dropped by the guard
        // before any FileSystemMonitor subscription is attempted.
        String missing = "/no/such/scilab/dir/" + System.nanoTime();
        assertDoesNotThrow(() -> reloader.watch("lib", missing));
    }

    @Test
    void unwatchIsSafeWhenNothingIsWatched() {
        LibraryReloader reloader = LibraryReloader.getInstance();
        assertDoesNotThrow(() -> reloader.unwatch(null));
        assertDoesNotThrow(() -> reloader.unwatch("/never/watched/" + System.nanoTime()));
    }
}
