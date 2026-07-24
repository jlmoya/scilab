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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.scilab.modules.types.ScilabDouble;
import org.scilab.modules.types.ScilabString;
import org.scilab.modules.types.ScilabTList;
import org.scilab.modules.xcos.io.scicos.AbstractElement;
import org.scilab.modules.xcos.io.scicos.Element;

import com.mxgraph.util.mxConstants;
import com.mxgraph.view.mxStylesheet;

/**
 * Hermetic unit tests for {@link StyleElement}.
 *
 * <p><b>Native boundary.</b> The validation failures raised by
 * {@code StyleElement.decode(...)} are {@code ScicosFormatException} subtypes
 * whose static initialiser calls {@code Messages.gettext(...)}, a
 * {@code native} method. Constructing those exceptions is therefore not
 * hermetic, so these tests never drive {@code decode} into a validation
 * failure. Instead they exercise:
 * <ul>
 *   <li>{@link StyleElement#canDecode(org.scilab.modules.types.ScilabType)},
 *       which never throws a {@code ScicosFormatException};</li>
 *   <li>the {@code decode} path on <em>well-formed</em> input, which either
 *       hits the {@code into == null} guard (a plain {@link NullPointerException})
 *       or applies the style loop against a real {@link mxStylesheet}
 *       (jgraphx is pure Java).</li>
 * </ul>
 */
public class StyleElementTest {

    private static final String[] HEADER =
        {"palette", "name", "blockNames", "icons", "style"};

    /**
     * Build a structurally valid palette tlist that passes
     * {@code StyleElement.validate()}: header + name + (Nx1) blockNames +
     * icons + (Nx1) style, for a total size of 5.
     */
    private static ScilabTList validData(String blockName, String style) {
        ScilabTList d = new ScilabTList(HEADER);
        d.add(new ScilabString("aName"));                                 // field 1: name
        d.add(new ScilabString(new String[][] {{blockName}}));            // field 2: blockNames
        d.add(new ScilabString(new String[][] {{"/unused/icon.png"}}));   // field 3: icons
        d.add(new ScilabString(new String[][] {{style}}));                // field 4: style
        return d;
    }

    // ------------------------------------------------------------------
    // construction / type
    // ------------------------------------------------------------------

    @Test
    public void isAnAbstractElementForStylesheets() {
        StyleElement el = new StyleElement();
        assertTrue(el instanceof AbstractElement, "must extend AbstractElement");
        assertTrue(el instanceof Element, "must implement Element");
    }

    // ------------------------------------------------------------------
    // canDecode
    // ------------------------------------------------------------------

    @Test
    public void canDecodeTrueForPaletteTypedTList() {
        assertTrue(new StyleElement().canDecode(validData("blk", "s")));
    }

    @Test
    public void canDecodeFalseWhenTypeNameIsNotPalette() {
        ScilabTList d = new ScilabTList(new String[] {"custom", "name"});
        assertFalse(new StyleElement().canDecode(d));
    }

    @Test
    public void canDecodeNullThrowsNPE() {
        // (ScilabTList) null succeeds, then data.get(0) dereferences null.
        assertThrows(NullPointerException.class, () -> new StyleElement().canDecode(null));
    }

    @Test
    public void canDecodeEmptyTListThrowsIndexOutOfBounds() {
        assertThrows(IndexOutOfBoundsException.class,
                     () -> new StyleElement().canDecode(new ScilabTList()));
    }

    @Test
    public void canDecodeNonTListThrowsClassCast() {
        assertThrows(ClassCastException.class,
                     () -> new StyleElement().canDecode(new ScilabString("palette")));
    }

    @Test
    public void canDecodeNonStringHeaderThrowsClassCast() {
        ScilabTList d = new ScilabTList();
        d.add(new ScilabDouble(1)); // element 0 is not a ScilabString
        assertThrows(ClassCastException.class, () -> new StyleElement().canDecode(d));
    }

    // ------------------------------------------------------------------
    // decode
    // ------------------------------------------------------------------

    @Test
    public void decodeWithValidDataButNullTargetThrowsGuardNPE() {
        // validate() passes for well-formed data, then the explicit null-guard fires.
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> new StyleElement().decode(validData("blk", "s"), null));
        assertEquals("No place to decode data", ex.getMessage(),
                     "must be the explicit guard, not an incidental NPE");
    }

    @Test
    public void decodeReturnsTheSameStylesheetInstance() throws Exception {
        mxStylesheet sheet = new mxStylesheet();
        Object result = new StyleElement().decode(validData("newBlk", "plainStyle"), sheet);
        assertSame(sheet, result, "decode fills and returns the supplied stylesheet");
    }

    @Test
    public void decodeInsertsAStyleForAnUnknownBlockName() throws Exception {
        mxStylesheet sheet = new mxStylesheet();
        assertFalse(sheet.getStyles().containsKey("newBlk"));

        new StyleElement().decode(validData("newBlk", "plainStyle"), sheet);

        assertTrue(sheet.getStyles().containsKey("newBlk"),
                   "a style must be registered for the decoded block name");
        assertNotNull(sheet.getStyles().get("newBlk"));
    }

    @Test
    public void decodeDoesNotOverwriteAnAlreadyDefinedStyle() throws Exception {
        mxStylesheet sheet = new mxStylesheet();
        Map<String, Object> preExisting = new HashMap<>();
        preExisting.put(mxConstants.STYLE_FILLCOLOR, "#123456");
        sheet.putCellStyle("blk", preExisting);

        new StyleElement().decode(validData("blk", "ignoredBecauseAlreadyPresent"), sheet);

        // The "do not update the style if it already exists" branch must keep the
        // original map untouched.
        assertSame(preExisting, sheet.getStyles().get("blk"));
        assertEquals("#123456", sheet.getStyles().get("blk").get(mxConstants.STYLE_FILLCOLOR));
    }
}
