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

package org.scilab.modules.preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.scilab.modules.preferences.ScilabPreferences.ToolboxInfos;

/**
 * Hermetic unit tests for {@link ScilabPreferences} and its nested value object
 * {@link ScilabPreferences.ToolboxInfos}.
 *
 * <p>Only the pure, GUI-free surface is covered. {@code openPreferences} posts to
 * the Swing thread and drives {@code XConfigManager}, so it is left out. The
 * validation-failure branches of {@code addToolboxInfos} call
 * {@code Messages.gettext} (a native JNI call), so tests deliberately drive only
 * the happy path with real files created under a JUnit {@code @TempDir}.
 */
public class ScilabPreferencesTest {

    // ----- ToolboxInfos value object ----------------------------------------

    @Test
    public void toolboxInfosExposesItsThreeFields() {
        ToolboxInfos ti = new ToolboxInfos("myTbx", "/some/path", "/some/path/prefs.xml");
        assertEquals("myTbx", ti.getName());
        assertEquals("/some/path", ti.getPath());
        assertEquals("/some/path/prefs.xml", ti.getPrefFile());
    }

    @Test
    public void toolboxInfosToStringLabelsEveryField() {
        ToolboxInfos ti = new ToolboxInfos("N", "P", "F");
        assertEquals("Toolbox: N\nPath: P\nPreference file: F", ti.toString());
    }

    @Test
    public void toolboxInfosPerformsNoValidationOnNulls() {
        // The constructor stores whatever it is given, including nulls.
        ToolboxInfos ti = new ToolboxInfos(null, null, null);
        assertNull(ti.getName());
        assertNull(ti.getPath());
        assertNull(ti.getPrefFile());
        assertEquals("Toolbox: null\nPath: null\nPreference file: null", ti.toString());
    }

    // ----- add / get / remove round trip ------------------------------------

    @Test
    public void addThenGetThenRemoveRoundTrip(@TempDir File tempDir) throws Exception {
        File pref = new File(tempDir, "prefs.xml");
        assertTrue(pref.createNewFile(), "precondition: preference file created");

        String path = tempDir.getAbsolutePath();
        try {
            ScilabPreferences.addToolboxInfos("roundTripTbx", path, pref.getAbsolutePath());

            ToolboxInfos found = findByPath(path);
            assertTrue(found != null, "the freshly added toolbox is listed");
            assertEquals("roundTripTbx", found.getName());
            assertEquals(pref.getAbsolutePath(), found.getPrefFile());
        } finally {
            ScilabPreferences.removeToolboxInfos(path);
        }

        assertNull(findByPath(path), "after removal the toolbox is no longer listed");
    }

    @Test
    public void addingSamePathTwiceReplacesTheEntry(@TempDir File tempDir) throws Exception {
        File pref = new File(tempDir, "prefs.xml");
        assertTrue(pref.createNewFile());
        String path = tempDir.getAbsolutePath();
        String prefPath = pref.getAbsolutePath();

        try {
            ScilabPreferences.addToolboxInfos("first", path, prefPath);
            ScilabPreferences.addToolboxInfos("second", path, prefPath);

            // The map is keyed by path, so the second add overwrites the first.
            assertEquals(1, countByPath(path), "path is the map key: no duplicate entry");
            assertEquals("second", findByPath(path).getName());
        } finally {
            ScilabPreferences.removeToolboxInfos(path);
        }
    }

    @Test
    public void removingAnUnknownPathIsANoOp() {
        // Should not throw even though nothing matches.
        ScilabPreferences.removeToolboxInfos("/definitely/not/registered/" + System.nanoTime());
        assertFalse(findByPath("/definitely/not/registered") != null);
    }

    private static ToolboxInfos findByPath(String path) {
        List<ToolboxInfos> all = ScilabPreferences.getToolboxesInfos();
        for (ToolboxInfos ti : all) {
            if (path.equals(ti.getPath())) {
                return ti;
            }
        }
        return null;
    }

    private static int countByPath(String path) {
        int n = 0;
        for (ToolboxInfos ti : ScilabPreferences.getToolboxesInfos()) {
            if (path.equals(ti.getPath())) {
                n++;
            }
        }
        return n;
    }
}
