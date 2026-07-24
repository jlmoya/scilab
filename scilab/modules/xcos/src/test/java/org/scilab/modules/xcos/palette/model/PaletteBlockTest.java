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

package org.scilab.modules.xcos.palette.model;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link PaletteBlock}.
 *
 * <p>
 * A {@code PaletteBlock} is a plain data holder for a palette entry: a
 * {@code name} plus two {@link VariablePath}s ({@code data} and {@code icon}).
 * It is not part of the {@link javax.swing.tree.TreeNode} hierarchy.
 *
 * <p>
 * {@link PaletteBlock#getLoadedIcon(int, int)} is only exercised on its
 * hermetic branch: when the icon path resolves to a non-existent file the method
 * short-circuits and returns an empty placeholder {@link ImageIcon} without
 * reaching the Batik SVG transcoder or the AWT scaling pipeline. Loading a real
 * raster/SVG icon would require a bundled asset (and, for SVG, Batik), so those
 * branches are intentionally not covered here.
 */
public class PaletteBlockTest {

    @Test
    public void freshBlockHasNullFields() {
        PaletteBlock b = new PaletteBlock();
        assertNull(b.getData());
        assertNull(b.getIcon());
        assertNull(b.getName());
    }

    @Test
    public void nameRoundTrips() {
        PaletteBlock b = new PaletteBlock();
        b.setName("BIGSOM_f");
        assertEquals("BIGSOM_f", b.getName());
        b.setName(null);
        assertNull(b.getName());
    }

    @Test
    public void dataRoundTrips() {
        PaletteBlock b = new PaletteBlock();
        VariablePath data = new VariablePath();
        data.setPath("/blocks/BIGSOM_f.h5");

        b.setData(data);
        assertSame(data, b.getData());

        b.setData(null);
        assertNull(b.getData());
    }

    @Test
    public void iconRoundTrips() {
        PaletteBlock b = new PaletteBlock();
        VariablePath icon = new VariablePath();
        icon.setPath("/blocks/BIGSOM_f.png");

        b.setIcon(icon);
        assertSame(icon, b.getIcon());

        b.setIcon(null);
        assertNull(b.getIcon());
    }

    @Test
    public void isPlainDataHolderNotATreeNode() {
        // PaletteBlock lives beside the tree model, it is not a node of it.
        // PaletteBlock and PaletteNode are unrelated classes, so the static
        // `instanceof` form is a compile-time error ("inconvertible types");
        // Class.isInstance is the reflective equivalent that verifies the same
        // runtime fact.
        assertFalse(PaletteNode.class.isInstance(new PaletteBlock()));
    }

    /**
     * When the icon path does not resolve to an existing file,
     * {@link PaletteBlock#getLoadedIcon(int, int)} returns an empty placeholder
     * {@link ImageIcon} (icon width/height == -1) via the early-return branch,
     * never touching Batik or the graphics pipeline.
     */
    @Test
    public void getLoadedIconReturnsEmptyPlaceholderForMissingFile() {
        final String missing = "/nonexistent/scilab/palette/icon_9Z7Q7X.png";
        Assumptions.assumeFalse(new File(missing).exists(), "the sentinel icon path must not exist");

        PaletteBlock b = new PaletteBlock();
        VariablePath icon = new VariablePath();
        icon.setPath(missing);
        b.setIcon(icon);

        Icon result = b.getLoadedIcon(16, 16);

        assertNotNull(result);
        assertInstanceOf(ImageIcon.class, result);
        ImageIcon img = (ImageIcon) result;
        assertEquals(-1, img.getIconWidth(), "placeholder icon has no loaded image");
        assertEquals(-1, img.getIconHeight(), "placeholder icon has no loaded image");
    }

    /**
     * Defect characterization: {@link PaletteBlock#getLoadedIcon(int, int)}
     * dereferences {@link #getIcon()} with no null guard, so a block whose icon
     * was never set fails with a {@link NullPointerException}.
     */
    @Test
    public void getLoadedIconWithNullIconThrowsNpe_defectCharacterization() {
        PaletteBlock b = new PaletteBlock();
        assertThrows(NullPointerException.class, () -> b.getLoadedIcon(16, 16));
    }
}
