/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 */

package org.scilab.modules.gui.ged;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.awt.Color;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * GED sets its chrome colours imperatively, which means they do NOT follow a
 * look and feel change on their own -- a plain Color is not a UIResource, so
 * updateComponentTreeUI() leaves it untouched. ContentLayout compensates by
 * tagging each themed component with a role and re-applying by role.
 *
 * These tests pin that contract: themed at build time, and re-themed on demand.
 */
public class ContentLayoutThemeTest {

    private Object savedFieldBackground;
    private boolean saved;

    @AfterEach
    public void restoreLaf() {
        if (saved) {
            UIManager.put("TextField.background", savedFieldBackground);
            saved = false;
        }
    }

    private void setFieldBackground(Color c) {
        if (!saved) {
            savedFieldBackground = UIManager.get("TextField.background");
            saved = true;
        }
        UIManager.put("TextField.background", c);
    }

    @Test
    public void labelTakesItsBackgroundFromTheLookAndFeel() {
        final Color themed = new Color(0x102030);
        setFieldBackground(themed);

        final JLabel label = new JLabel();
        new ContentLayout().addJLabel(new JPanel(), label, "text", 0, 0, 0);

        assertEquals(themed, label.getBackground(),
                     "GED label should take the look and feel field background, not a hardcoded white");
    }

    @Test
    public void refreshThemeReappliesAfterTheLookAndFeelChanges() {
        setFieldBackground(new Color(0xFFFFFF));

        final JPanel parent = new JPanel();
        final JLabel label = new JLabel();
        new ContentLayout().addJLabel(parent, label, "text", 0, 0, 0);
        assertEquals(new Color(0xFFFFFF), label.getBackground());

        // Simulate the switch to a dark theme, then the refresh the L&F
        // listener performs. Without the refresh the label would stay white --
        // which is exactly the bug this mechanism exists to prevent.
        final Color dark = new Color(0x1E1E1E);
        setFieldBackground(dark);
        assertNotEquals(dark, label.getBackground(),
                        "a plain Color must not follow the look and feel by itself");

        ContentLayout.refreshTheme(parent);
        assertEquals(dark, label.getBackground(), "refreshTheme should re-apply the new theme colour");
    }

    @Test
    public void untaggedComponentsAreLeftAlone() {
        // refreshTheme walks whole trees, so it must not repaint components that
        // never opted in -- notably the colour swatches, which show DATA.
        final JPanel parent = new JPanel();
        final JLabel swatch = new JLabel();
        final Color data = new Color(0xC03030);
        swatch.setBackground(data);
        parent.add(swatch);

        assertNull(swatch.getClientProperty("ged.themeRole"), "precondition: no role");
        setFieldBackground(new Color(0x1E1E1E));
        ContentLayout.refreshTheme(parent);

        assertEquals(data, swatch.getBackground(), "an untagged swatch must keep the colour it is displaying");
    }
}
