/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 *
 */

package org.scilab.modules.commons.gui;

import java.awt.Color;
import java.awt.image.BufferedImage;

import javax.swing.ImageIcon;
import javax.swing.UIManager;

/**
 * Recolours monochrome toolbar icons to match the current look and feel.
 *
 * Scilab's small action icons are painted as PURE BLACK artwork on transparency.
 * That is invisible against a dark theme: measured on the file-browser filter bar,
 * case-sensitive, regex, clear and filter all have a mean luminance of 0.0 over
 * their opaque pixels, so on a #282828 panel there is nothing to see.
 *
 * Because the artwork is monochrome-on-alpha, it does not need redrawing or a
 * second set of files: replacing the RGB of every pixel while KEEPING ITS ALPHA
 * reproduces the glyph exactly, anti-aliasing included, in whatever colour the
 * theme wants. In a light theme the target is near-black, so the result is
 * indistinguishable from the original file.
 *
 * Icons that are NOT monochrome are returned untouched. Flat-tinting a coloured
 * icon would destroy it, so {@link #isMonochrome} decides, and anything it is not
 * sure about is left alone.
 */
public final class ThemedIcons {

    /** Pixels this faint are treated as background and ignored. */
    private static final int ALPHA_FLOOR = 32;

    /** Max chroma (max(r,g,b) - min(r,g,b)) still considered greyscale. */
    private static final int CHROMA_TOLERANCE = 16;

    private ThemedIcons() { }

    /**
     * Load an icon and adapt it to the current look and feel.
     *
     * @param path icon file, typically from FindIconHelper.findIcon
     * @return a themed icon, or the icon unchanged when it is not monochrome
     */
    public static ImageIcon load(String path) {
        return adapt(new ImageIcon(path));
    }

    /**
     * Adapt an existing icon to the current look and feel.
     */
    public static ImageIcon adapt(ImageIcon icon) {
        if (icon == null || icon.getIconWidth() <= 0 || icon.getIconHeight() <= 0) {
            return icon;
        }
        BufferedImage src = toBuffered(icon);
        if (!isMonochrome(src)) {
            return icon;
        }
        return new ImageIcon(tint(src, foreground()));
    }

    /** The colour toolbar glyphs should take: the theme's ordinary label colour. */
    private static Color foreground() {
        Object c = UIManager.get("Label.foreground");
        return (c instanceof Color) ? (Color) c : Color.BLACK;
    }

    private static BufferedImage toBuffered(ImageIcon icon) {
        BufferedImage img = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(),
                                              BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = img.createGraphics();
        try {
            icon.paintIcon(null, g, 0, 0);
        } finally {
            g.dispose();
        }
        return img;
    }

    /**
     * True when every sufficiently opaque pixel is greyscale. Colour icons return
     * false and are then left exactly as they are.
     */
    static boolean isMonochrome(BufferedImage img) {
        boolean sawPixel = false;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int argb = img.getRGB(x, y);
                if (((argb >>> 24) & 0xFF) < ALPHA_FLOOR) {
                    continue;
                }
                sawPixel = true;
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                int chroma = Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b));
                if (chroma > CHROMA_TOLERANCE) {
                    return false;
                }
            }
        }
        // A fully transparent image has nothing to recolour.
        return sawPixel;
    }

    /**
     * Replace RGB with {@code colour} while preserving each pixel's alpha, which is
     * what keeps the glyph shape and its anti-aliased edges intact.
     */
    static BufferedImage tint(BufferedImage img, Color colour) {
        BufferedImage out = new BufferedImage(img.getWidth(), img.getHeight(),
                                              BufferedImage.TYPE_INT_ARGB);
        int rgb = colour.getRGB() & 0x00FFFFFF;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int alpha = img.getRGB(x, y) & 0xFF000000;
                out.setRGB(x, y, alpha | rgb);
            }
        }
        return out;
    }
}
