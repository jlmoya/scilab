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
package org.scilab.modules.xcos.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link XcosMessages}.
 *
 * <p><b>Scope &amp; native boundary.</b> Almost every field of {@code XcosMessages}
 * is initialized from {@code Messages.gettext(...)}, and {@code Messages.gettext}
 * delegates straight to the native {@code MessagesJNI.gettext} (a
 * {@code System.loadLibrary}-backed JNI call). Because those initializers run
 * inside the class's {@code <clinit>}, <em>any</em> active use of
 * {@code XcosMessages} (touching a non-constant field, or calling
 * {@link XcosMessages#isMacOsPopupTrigger}) forces class initialization and would
 * require the Scilab native runtime. Those members are therefore intentionally
 * <em>not</em> covered here.</p>
 *
 * <p>What <em>is</em> covered are the handful of fields declared as compile-time
 * constant expressions ({@code static final String} initialized from string
 * literals). Per JLS 13.4.9 / 12.4.1 the compiler inlines such constants into
 * the referencing class, so reading them neither loads nor initializes
 * {@code XcosMessages} -- keeping this test fully hermetic while still pinning
 * the public API contract for those values (notably the {@code %s} format
 * strings used to build "From"/"Goto" block labels).</p>
 */
public class XcosMessagesTest {

    @Test
    @DisplayName("DOTS is the three-character ellipsis used to build \"...\" suffixes")
    public void dotsConstant() {
        assertEquals("...", XcosMessages.DOTS);
        assertEquals(3, XcosMessages.DOTS.length());
    }

    @Test
    @DisplayName("EMPTY_INFO is the empty string (info-bar reset value)")
    public void emptyInfoConstant() {
        assertEquals("", XcosMessages.EMPTY_INFO);
        assertTrue(XcosMessages.EMPTY_INFO.isEmpty());
    }

    @Test
    @DisplayName("COPYRIGHT_INRIA is the fixed (non-localized) INRIA copyright line")
    public void copyrightConstant() {
        assertEquals("Copyright (c) 1989-2009 (INRIA)", XcosMessages.COPYRIGHT_INRIA);
    }

    @Test
    @DisplayName("BLOCK_FROM is a single-%s format string")
    public void blockFromFormat() {
        assertEquals("From %s", XcosMessages.BLOCK_FROM);
        assertEquals("From label", String.format(XcosMessages.BLOCK_FROM, "label"));
    }

    @Test
    @DisplayName("BLOCK_GOTO is a single-%s format string")
    public void blockGotoFormat() {
        assertEquals("Goto %s", XcosMessages.BLOCK_GOTO);
        assertEquals("Goto label", String.format(XcosMessages.BLOCK_GOTO, "label"));
    }

    @Test
    @DisplayName("From/Goto labels differ only by their verb (round-trip a sample tag)")
    public void fromAndGotoAreDistinctButParallel() {
        String tag = "myTag";
        String from = String.format(XcosMessages.BLOCK_FROM, tag);
        String goto_ = String.format(XcosMessages.BLOCK_GOTO, tag);
        assertTrue(from.endsWith(tag) && goto_.endsWith(tag));
        assertTrue(!from.equals(goto_));
        assertTrue(from.startsWith("From") && goto_.startsWith("Goto"));
    }
}
