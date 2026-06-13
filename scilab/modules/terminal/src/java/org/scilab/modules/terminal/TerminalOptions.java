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

package org.scilab.modules.terminal;

import java.awt.Font;

import org.w3c.dom.Document;

import org.scilab.modules.commons.xml.XConfiguration;
import static org.scilab.modules.commons.xml.XConfiguration.XConfAttribute;

/**
 * Reads the Terminal preferences pane (modules/terminal/etc/XConfiguration-terminal.xml)
 * and the terminal-font item of the Fonts pane. Each getter falls back to a sane
 * default if preferences are unavailable (e.g. headless / not yet configured).
 *
 * @author Jose Moya
 */
public final class TerminalOptions {

    private static final String SETTINGS_PATH = "//terminal/body/terminal-settings";
    private static final String FONT_PATH = "//fonts/body/fonts/item[@xconf-uid=\"terminal-font\"]";

    private TerminalOptions() { }

    /** @return the configured terminal settings, or defaults on any error. */
    public static TerminalSettings getSettings() {
        try {
            Document doc = XConfiguration.getXConfigurationDocument();
            TerminalSettings[] s = XConfiguration.get(TerminalSettings.class, doc, SETTINGS_PATH);
            if (s != null && s.length > 0) {
                return s[0];
            }
        } catch (Throwable ignore) { }
        return new TerminalSettings();
    }

    /** @return the configured terminal font, or Monospaced 13 on any error. */
    public static Font getFont() {
        try {
            Document doc = XConfiguration.getXConfigurationDocument();
            TerminalFont[] f = XConfiguration.get(TerminalFont.class, doc, FONT_PATH);
            if (f != null && f.length > 0 && f[0].font != null) {
                return f[0].font;
            }
        } catch (Throwable ignore) { }
        return new Font("Monospaced", Font.PLAIN, 13);
    }

    /**
     * Terminal settings POJO (shell, starting directory, scrollback, audible bell).
     */
    @XConfAttribute
    public static class TerminalSettings {

        public String shell = "";
        public String startDir = "";
        public int scrollback = 10000;
        public boolean audibleBell = true;

        private TerminalSettings() { }

        @XConfAttribute(attributes = {"shell", "start-dir", "scrollback-lines", "audible-bell"})
        private void set(String shell, String startDir, int scrollback, boolean audibleBell) {
            this.shell = shell;
            this.startDir = startDir;
            this.scrollback = scrollback;
            this.audibleBell = audibleBell;
        }
    }

    /**
     * Terminal font POJO (mirrors the Console/SciNotes font readers).
     */
    @XConfAttribute
    public static class TerminalFont {

        public Font font;

        private TerminalFont() { }

        @XConfAttribute(tag = "item", attributes = {"font-face", "font-name", "font-size", "desktop"})
        private void set(String fontFace, String fontName, int fontSize, boolean desktop) {
            this.font = new Font(fontName, Font.PLAIN, fontSize);
            int style = Font.PLAIN;
            if (fontFace.contains("bold")) {
                style = style | Font.BOLD;
            }
            if (fontFace.contains("italic")) {
                style = style | Font.ITALIC;
            }
            if (style != Font.PLAIN) {
                this.font = this.font.deriveFont(style);
            }
        }
    }
}
