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

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.scilab.modules.preferences.XCommonManager;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Hermetic unit tests for the {@link Icon} preference component (a
 * {@link javax.swing.JLabel} wrapping an {@link javax.swing.ImageIcon}).
 *
 * <p>The {@code src} sensor/actuator round-trips through the icon's
 * <em>description</em> field, so the assertions hold regardless of whether the
 * underlying image file actually resolves on disk ({@code ImageIcon} tolerates a
 * missing path). No native code, display or running Scilab is required.
 */
public class IconTest {

    private static Element el(String name, String... kv) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element e = doc.createElement(name);
        for (int i = 0; i + 1 < kv.length; i += 2) {
            e.setAttribute(kv[i], kv[i + 1]);
        }
        return e;
    }

    @Test
    public void constructorStoresSrcAsIconDescription() throws Exception {
        Icon c = new Icon(el("Icon", "src", "foo.png"));
        assertEquals("foo.png", c.src());
    }

    @Test
    public void srcActuatorAndSensorRoundTrip() throws Exception {
        Icon c = new Icon(el("Icon", "src", "foo.png"));
        c.src("bar.png");
        assertEquals("bar.png", c.src());
    }

    @Test
    public void refreshUpdatesTheSrc() throws Exception {
        Icon c = new Icon(el("Icon", "src", "foo.png"));
        c.refresh(el("Icon", "src", "baz.png"));
        assertEquals("baz.png", c.src());
    }

    @Test
    public void absentSrcEchoesTheNavSentinel() throws Exception {
        // No 'src' attribute => getAttribute returns NAV, which becomes the icon description.
        Icon c = new Icon(el("Icon"));
        assertEquals(XCommonManager.NAV, c.src());
    }

    @Test
    public void actuatorsAreTheDocumentedSet() throws Exception {
        assertArrayEquals(new String[] {"src"}, new Icon(el("Icon", "src", "x.png")).actuators());
    }

    @Test
    public void toStringIsTheClassLabel() throws Exception {
        assertEquals("Icon", new Icon(el("Icon", "src", "x.png")).toString());
    }
}
