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

package org.scilab.modules.preferences.Component;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.scilab.modules.preferences.XCommonManager;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Hermetic unit tests for the {@link Image} preference component (a
 * {@link javax.swing.JLabel} carrying an icon). The url actuator performs
 * {@code $SCI} substitution and swallows malformed/unloadable URLs. Tests use a
 * {@code $SCI/...} value (which resolves to a scheme-less path &rarr; caught
 * {@code MalformedURLException}) and a {@code file:} URL to a nonexistent file
 * (fails fast, no network) so nothing ever touches the network.
 */
public class ImageTest {

    private static Element el(String name, String... kv) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element e = doc.createElement(name);
        for (int i = 0; i + 1 < kv.length; i += 2) {
            e.setAttribute(kv[i], kv[i + 1]);
        }
        return e;
    }

    @Test
    public void actuatorsAreJustUrl() throws Exception {
        assertArrayEquals(new String[] {"url"}, new Image(el("Image")).actuators());
    }

    @Test
    public void withoutAUrlAttributeTheUrlStaysTheNavSentinel() throws Exception {
        // refresh feeds NAV into url(); it has no "$SCI" so substitution is a no-op,
        // and new URL(NAV) throws MalformedURLException which the actuator swallows.
        Image c = new Image(el("Image"));
        assertEquals(XCommonManager.NAV, c.url());
        assertTrue(c.toString().startsWith("Image url:"));
    }

    @Test
    public void dollarSciIsExpandedInTheUrl() throws Exception {
        Image c = new Image(el("Image"));
        c.url("$SCI/modules/preferences/etc/logo.png");
        assertFalse(c.url().contains("$SCI"), "the $SCI token must be substituted away");
        assertTrue(c.url().endsWith("/modules/preferences/etc/logo.png"));
    }

    @Test
    public void aWellFormedFileUrlIsStoredVerbatim() throws Exception {
        Image c = new Image(el("Image"));
        c.url("file:/tmp/scilab-image-test-does-not-exist.png");
        assertEquals("file:/tmp/scilab-image-test-does-not-exist.png", c.url());
        assertEquals("Image url: file:/tmp/scilab-image-test-does-not-exist.png", c.toString());
    }
}
