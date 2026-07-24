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

package org.scilab.modules.console;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link SciCompletionManager#separateFilesDirectories}.
 *
 * <p>The manager's {@code getCompletionItems}/{@code addItemsToDictionary} paths
 * reach into the native completion engine ({@code Completion.*}) and the JNI
 * {@code Messages.gettext}, so they are out of scope. {@code separateFilesDirectories}
 * however is pure: it partitions a dictionary into files and directories purely
 * by whether each entry ends with the platform file separator, so it can be
 * driven with no console, no engine and no display. The separator is read from
 * the running platform so the test is portable.
 */
public class SciCompletionManagerTest {

    private static final String SEP = System.getProperty("file.separator");

    private SciCompletionManager manager;
    private ArrayList<String> files;
    private ArrayList<String> dirs;

    @BeforeEach
    public void setUp() {
        manager = new SciCompletionManager();
        files = new ArrayList<String>();
        dirs = new ArrayList<String>();
    }

    @Test
    public void entriesEndingWithTheSeparatorAreDirectoriesTheRestAreFiles() {
        String[] input = {"foo.sci", "bar" + SEP, "baz.sce", "sub" + SEP};
        manager.separateFilesDirectories(input, files, dirs);

        assertEquals(Arrays.asList("foo.sci", "baz.sce"), files);
        assertEquals(Arrays.asList("bar" + SEP, "sub" + SEP), dirs);
    }

    @Test
    public void anEmptyDictionaryLeavesBothOutputListsEmpty() {
        manager.separateFilesDirectories(new String[0], files, dirs);
        assertTrue(files.isEmpty());
        assertTrue(dirs.isEmpty());
    }

    @Test
    public void aDictionaryOfOnlyFilesFillsOnlyTheFilesList() {
        String[] input = {"a.sci", "b.sce", "c"};
        manager.separateFilesDirectories(input, files, dirs);
        assertEquals(3, files.size());
        assertTrue(dirs.isEmpty());
    }

    @Test
    public void aDictionaryOfOnlyDirectoriesFillsOnlyTheDirectoriesList() {
        String[] input = {"one" + SEP, "two" + SEP};
        manager.separateFilesDirectories(input, files, dirs);
        assertTrue(files.isEmpty());
        assertEquals(2, dirs.size());
    }

    @Test
    public void aBareSeparatorCountsAsADirectory() {
        manager.separateFilesDirectories(new String[] {SEP}, files, dirs);
        assertTrue(files.isEmpty());
        assertEquals(Arrays.asList(SEP), dirs);
    }

    @Test
    public void theMethodAppendsToPrePopulatedListsRatherThanReplacingThem() {
        files.add("already-a-file");
        dirs.add("already-a-dir" + SEP);
        manager.separateFilesDirectories(new String[] {"new.sci", "newdir" + SEP}, files, dirs);

        assertEquals(Arrays.asList("already-a-file", "new.sci"), files);
        assertEquals(Arrays.asList("already-a-dir" + SEP, "newdir" + SEP), dirs);
    }

    @Test
    public void separatorInTheMiddleDoesNotMakeItADirectory() {
        // Only a *trailing* separator matters; an interior one is just a path fragment.
        String[] input = {"a" + SEP + "b.sci"};
        manager.separateFilesDirectories(input, files, dirs);
        assertEquals(Arrays.asList("a" + SEP + "b.sci"), files);
        assertTrue(dirs.isEmpty());
    }
}
