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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.artenum.rosetta.interfaces.core.CompletionItem;

/**
 * Hermetic unit tests for {@link SciCompletionManager}.
 *
 * <p>The manager's {@code getCompletionItems} path reaches into the native
 * completion engine ({@code Completion.*}) and the branch of {@code
 * addItemsToDictionary} that actually builds items calls the JNI {@code
 * Messages.gettext}, so those are out of scope. What is hermetic:
 * {@code separateFilesDirectories} (pure partitioning by the platform file
 * separator), the guard branches of {@code addItemsToDictionary} that never
 * reach the interpreter (null and empty inputs), and the two collaborator
 * setters. Everything runs with no console, no engine and no display.
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

    // --- addItemsToDictionary: the compiler-free guard branches -------------

    @Test
    public void addItemsToDictionaryIgnoresANullItemArray() {
        // items == null short-circuits before the dictionary (still null here) or
        // the JNI Messages.gettext is ever touched.
        assertNull(manager.dictionary);
        assertDoesNotThrow(() -> manager.addItemsToDictionary("File", null));
        assertNull(manager.dictionary, "a null item array must not create a dictionary");
    }

    @Test
    public void addItemsToDictionaryAddsNothingForAnEmptyItemArray() {
        // An empty (but non-null) array enters the guard yet runs the loop zero
        // times, so no CompletionItemImpl is built and gettext is never called.
        manager.dictionary = new ArrayList<CompletionItem>();
        manager.addItemsToDictionary("Directory", new String[0]);
        assertTrue(manager.dictionary.isEmpty());
    }

    // --- collaborator setters ----------------------------------------------

    @Test
    public void setInterpretorIsANoOpForTheScilabImplementation() {
        // Scilab does its own interpretation, so this setter deliberately does
        // nothing and must tolerate a null argument.
        assertDoesNotThrow(() -> manager.setInterpretor(null));
    }

    @Test
    public void setInputParsingManagerStoresTheManager() throws Exception {
        SciInputParsingManager ipm = new SciInputParsingManager();
        manager.setInputParsingManager(ipm);

        Field f = SciCompletionManager.class.getDeclaredField("inputParsingManager");
        f.setAccessible(true);
        assertSame(ipm, f.get(manager));
    }
}
