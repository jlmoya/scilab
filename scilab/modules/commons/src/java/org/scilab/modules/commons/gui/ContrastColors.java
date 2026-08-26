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

/**
 * Keeps configured foreground colours readable against whatever background is
 * actually in force.
 *
 * SciNotes colours its syntax with 25 configurable colours chosen for a white
 * page: Identifier and MacroInFile are #000000, Operator #5C5C5C, Url and Mail
 * #0000FF, LaTeX #8B2252. On a dark editor those range from hard to read to
 * literally invisible -- black text on a #282828 background.
 *
 * Shipping a second hand-picked palette was rejected for two reasons: it would
 * have to be kept in step with the light one forever, and it would silently
 * discard any colour the USER had customised. Adjusting the configured colour
 * instead keeps the author's intent -- a red keyword stays red -- and adapts a
 * user's own choices just as well as the defaults.
 *
 * The adjustment always preserves HUE. It moves brightness first, by the smallest
 * amount that reaches the target contrast, and only falls back to reducing
 * saturation when brightness cannot help -- which happens for a colour already at
 * maximum brightness, such as pure red. A colour that is already readable is
 * returned UNCHANGED, which is why a light theme is unaffected: every default
 * already passes there.
 *
 * Contrast is the WCAG 2.x ratio, (L1 + 0.05) / (L2 + 0.05) over relative
 * luminance.
 */
public final class ContrastColors {

    /** WCAG AA for normal text. Source code is small and dense, so it earns the full ratio. */
    public static final double MIN_CONTRAST = 4.5;

    private ContrastColors() { }

    /**
     * Return {@code fg} if it is already readable on {@code bg}, otherwise the
     * nearest brighter or darker variant of it that is.
     */
    public static Color readable(Color fg, Color bg) {
        return readable(fg, bg, MIN_CONTRAST);
    }

    public static Color readable(Color fg, Color bg, double minContrast) {
        if (fg == null || bg == null || contrast(fg, bg) >= minContrast) {
            return fg;
        }

        // Move away from the background: lighten on a dark background, darken on a
        // light one. Going the other way can never reach the target.
        boolean lighten = luminance(bg) < 0.5;

        float[] hsb = Color.RGBtoHSB(fg.getRed(), fg.getGreen(), fg.getBlue(), null);
        Color best = fg;
        double bestContrast = contrast(fg, bg);

        // Pass 1: brightness. 100 steps is finer than 8-bit colour can express, so the
        // first step that passes is effectively the minimal change.
        for (int i = 1; i <= 100; i++) {
            float b = lighten ? hsb[2] + (1f - hsb[2]) * (i / 100f)
                              : hsb[2] * (1f - i / 100f);
            Color candidate = Color.getHSBColor(hsb[0], hsb[1], clamp(b));
            double c = contrast(candidate, bg);
            if (c > bestContrast) {
                bestContrast = c;
                best = candidate;
            }
            if (c >= minContrast) {
                return candidate;
            }
        }

        // Pass 2: saturation. Brightness alone cannot rescue a fully saturated colour
        // that is ALREADY at maximum brightness -- pure red #FF0000 (the
        // ExternalVariable token) is exactly that case: it sits at brightness 1.0, so
        // pass 1 cannot move it, and it still fails against a dark editor. Washing the
        // colour towards white raises luminance while holding the hue, so red stays
        // recognisably red. Only done when lightening; on a light background pass 1
        // can always darken far enough.
        if (lighten) {
            for (int i = 1; i <= 100; i++) {
                float sat = hsb[1] * (1f - i / 100f);
                Color candidate = Color.getHSBColor(hsb[0], clamp(sat), 1f);
                double c = contrast(candidate, bg);
                if (c > bestContrast) {
                    bestContrast = c;
                    best = candidate;
                }
                if (c >= minContrast) {
                    return candidate;
                }
            }
        }

        // Nothing reached the target: return the most readable variant found rather
        // than an unrelated colour.
        return best;
    }

    /** WCAG contrast ratio between two colours; symmetric, in [1, 21]. */
    public static double contrast(Color a, Color b) {
        double la = luminance(a);
        double lb = luminance(b);
        double hi = Math.max(la, lb);
        double lo = Math.min(la, lb);
        return (hi + 0.05) / (lo + 0.05);
    }

    /** WCAG relative luminance. */
    public static double luminance(Color c) {
        return 0.2126 * channel(c.getRed())
             + 0.7152 * channel(c.getGreen())
             + 0.0722 * channel(c.getBlue());
    }

    private static double channel(int v) {
        double s = v / 255.0;
        return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    }

    private static float clamp(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
}
