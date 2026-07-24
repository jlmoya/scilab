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

package org.scilab.modules.commons;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;
import org.scilab.modules.commons.xml.XConfigurationListener;

/**
 * Hermetic unit tests for the pure surface of {@link ScilabGeneralPrefs}.
 *
 * <p>Only the singleton accessor and {@code openPreferences} are exercised: the
 * remaining methods ({@code getDesktopFont}, {@code configurationChanged}) route
 * through the native {@code ScilabCommons}/{@code XConfiguration} layer and are
 * not hermetically loadable. {@code openPreferences} reflectively looks up the
 * (absent) preferences module and is expected to no-op silently - the same
 * behaviour Scilab exhibits in MN mode.
 */
public class ScilabGeneralPrefsTest {

    @Test
    public void getInstanceReturnsANonNullSingleton() {
        ScilabGeneralPrefs first = ScilabGeneralPrefs.getInstance();
        assertNotNull(first);
        assertSame(first, ScilabGeneralPrefs.getInstance());
    }

    @Test
    public void theInstanceIsAnXConfigurationListener() {
        assertInstanceOf(XConfigurationListener.class, ScilabGeneralPrefs.getInstance());
    }

    @Test
    public void openPreferencesIsASilentNoOpWhenThePreferencesModuleIsAbsent() {
        // org.scilab.modules.preferences.ScilabPreferences is not on the commons
        // test classpath, so the reflective lookup swallows ClassNotFoundException.
        assertDoesNotThrow(() -> ScilabGeneralPrefs.openPreferences("//general/body/environment"));
    }

    @Test
    public void openPreferencesToleratesANullPath() {
        assertDoesNotThrow(() -> ScilabGeneralPrefs.openPreferences(null));
    }
}
