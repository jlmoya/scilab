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

package org.scilab.modules.xcos.palette;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;

import org.junit.jupiter.api.Test;
import org.scilab.modules.types.ScilabDouble;
import org.scilab.modules.types.ScilabString;
import org.scilab.modules.types.ScilabTList;
import org.scilab.modules.xcos.io.scicos.AbstractElement;
import org.scilab.modules.xcos.io.scicos.Element;
import org.scilab.modules.xcos.palette.model.PaletteBlock;
import org.scilab.modules.xcos.palette.model.PreLoaded;

/**
 * Hermetic unit tests for {@link PreLoadedElement}.
 *
 * <p><b>Native boundaries avoided.</b>
 * <ul>
 *   <li>{@code decode(...)} validation failures throw {@code ScicosFormatException}
 *       subtypes, whose static initialiser reaches the {@code native}
 *       {@code Messages.gettext(...)}; these tests never drive a validation
 *       failure.</li>
 *   <li>When a block icon file does <em>not</em> exist, {@code decode}
 *       schedules a Scilab interpreter call
 *       ({@code ScilabInterpreterManagement.requestScilabExec}) — also native.
 *       Every icon used here is a real temporary file, so that branch is never
 *       taken.</li>
 *   <li>{@code encode(...)} needs {@code Xcos.getInstance()} (the running GUI
 *       singleton) and is therefore out of scope for a hermetic unit test.</li>
 *   <li>{@code PreLoaded.toString()} (inherited from {@code PaletteNode}) also
 *       calls {@code Messages.gettext}; the assertions read {@code getName()}
 *       directly instead.</li>
 * </ul>
 */
public class PreLoadedElementTest {

    private static final String[] HEADER =
        {"palette", "name", "blockNames", "icons", "style"};

    /**
     * Build a structurally valid palette tlist that passes
     * {@code PreLoadedElement.validate()}: a 1x1 name, an Nx1 blockNames column,
     * an Nx1 icons column and an (unchecked) style field.
     */
    private static ScilabTList paletteData(String name, String[][] blockNames, String[][] icons) {
        ScilabTList d = new ScilabTList(HEADER);
        d.add(new ScilabString(name));                       // field 1: name (1x1)
        d.add(new ScilabString(blockNames));                 // field 2: blockNames (Nx1)
        d.add(new ScilabString(icons));                      // field 3: icons (Nx1)
        d.add(new ScilabString(new String[][] {{""}}));      // field 4: style (unused here)
        return d;
    }

    /** Create an existing on-disk file so the native icon-generation branch is skipped. */
    private static File existingIcon() throws Exception {
        File f = File.createTempFile("xcosPaletteIconTest", ".png");
        f.deleteOnExit();
        return f;
    }

    // ------------------------------------------------------------------
    // construction / type
    // ------------------------------------------------------------------

    @Test
    public void isAnAbstractElementForPreLoadedPalettes() {
        PreLoadedElement el = new PreLoadedElement();
        assertTrue(el instanceof AbstractElement, "must extend AbstractElement");
        assertTrue(el instanceof Element, "must implement Element");
    }

    // ------------------------------------------------------------------
    // canDecode
    // ------------------------------------------------------------------

    @Test
    public void canDecodeTrueForPaletteTypedTList() throws Exception {
        ScilabTList d = paletteData("p", new String[][] {{"b"}},
                                    new String[][] {{existingIcon().getAbsolutePath()}});
        assertTrue(new PreLoadedElement().canDecode(d));
    }

    @Test
    public void canDecodeFalseWhenTypeNameIsNotPalette() {
        ScilabTList d = new ScilabTList(new String[] {"mycustomtype", "name"});
        assertFalse(new PreLoadedElement().canDecode(d));
    }

    @Test
    public void canDecodeNullThrowsNPE() {
        assertThrows(NullPointerException.class, () -> new PreLoadedElement().canDecode(null));
    }

    @Test
    public void canDecodeEmptyTListThrowsIndexOutOfBounds() {
        assertThrows(IndexOutOfBoundsException.class,
                     () -> new PreLoadedElement().canDecode(new ScilabTList()));
    }

    @Test
    public void canDecodeNonTListThrowsClassCast() {
        assertThrows(ClassCastException.class,
                     () -> new PreLoadedElement().canDecode(new ScilabString("palette")));
    }

    @Test
    public void canDecodeNonStringHeaderThrowsClassCast() {
        ScilabTList d = new ScilabTList();
        d.add(new ScilabDouble(1)); // element 0 is not a ScilabString
        assertThrows(ClassCastException.class, () -> new PreLoadedElement().canDecode(d));
    }

    // ------------------------------------------------------------------
    // decode
    // ------------------------------------------------------------------

    @Test
    public void decodeIntoNullCreatesEnabledNamedPaletteWithOneBlock() throws Exception {
        String iconPath = existingIcon().getAbsolutePath();
        ScilabTList data = paletteData("MyPalette",
                                       new String[][] {{"BigSom"}},
                                       new String[][] {{iconPath}});

        PreLoaded palette = new PreLoadedElement().decode(data, null);

        assertNotNull(palette, "a null target must be replaced by a fresh PreLoaded");
        assertTrue(palette.isEnable(), "decode always enables the palette");
        assertEquals("MyPalette", palette.getName());

        assertEquals(1, palette.getBlock().size());
        PaletteBlock block = palette.getBlock().get(0);
        assertEquals("BigSom", block.getName());
        assertNotNull(block.getIcon());
        assertEquals(iconPath, block.getIcon().getPath());
        assertNull(block.getIcon().getVariable(), "the icon variable is explicitly nulled");
    }

    @Test
    public void decodeReusesTheProvidedTargetAndAppendsBlocks() throws Exception {
        String iconPath = existingIcon().getAbsolutePath();
        ScilabTList data = paletteData("Fresh",
                                       new String[][] {{"Added"}},
                                       new String[][] {{iconPath}});

        PreLoaded existing = new PreLoaded();
        existing.setName("Stale");
        PaletteBlock preExisting = new PaletteBlock();
        preExisting.setName("AlreadyThere");
        existing.getBlock().add(preExisting);

        PreLoaded result = new PreLoadedElement().decode(data, existing);

        assertSame(existing, result, "a non-null target is filled in place and returned");
        assertEquals("Fresh", result.getName(), "the name is overwritten from the data");
        // decode appends rather than replacing, so the pre-existing block survives.
        assertEquals(2, result.getBlock().size());
        assertEquals("AlreadyThere", result.getBlock().get(0).getName());
        assertEquals("Added", result.getBlock().get(1).getName());
    }

    @Test
    public void decodeDecodesEveryBlockOfAColumnVector() throws Exception {
        String icon1 = existingIcon().getAbsolutePath();
        String icon2 = existingIcon().getAbsolutePath();
        ScilabTList data = paletteData("Multi",
                                       new String[][] {{"b1"}, {"b2"}},
                                       new String[][] {{icon1}, {icon2}});

        PreLoaded palette = new PreLoadedElement().decode(data, null);

        assertEquals("Multi", palette.getName());
        assertEquals(2, palette.getBlock().size());
        assertEquals("b1", palette.getBlock().get(0).getName());
        assertEquals("b2", palette.getBlock().get(1).getName());
        assertEquals(icon1, palette.getBlock().get(0).getIcon().getPath());
        assertEquals(icon2, palette.getBlock().get(1).getIcon().getPath());
    }
}
