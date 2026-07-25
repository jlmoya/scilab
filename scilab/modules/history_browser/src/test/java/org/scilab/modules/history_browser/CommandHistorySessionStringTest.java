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

package org.scilab.modules.history_browser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link CommandHistory.SessionString}, the tiny
 * package-private value holder {@code CommandHistory} wraps around every
 * "session banner" line (the {@code // -- ... -- //} rows) so its tree cell
 * renderer can colour those nodes green while leaving ordinary command nodes
 * (plain {@code String} user objects) untouched.
 *
 * <h2>Why loading {@code CommandHistory} here is safe</h2>
 * The sibling {@code CommandHistoryTabFactoryTest} deliberately avoids the
 * {@code CommandHistory} class because its <em>static initializer</em>
 * registers a tab factory (and builds a Swing {@code Timer}) — side effects no
 * hermetic test wants. Reaching the nested {@code SessionString} does force the
 * JVM to <em>load</em> {@code CommandHistory}, but loading is not the same as
 * initializing: per JLS 12.4.1 creating an instance of a {@code static} nested
 * class triggers initialization of that nested class only, never of its
 * enclosing class, and {@code SessionString} references nothing of
 * {@code CommandHistory}'s own state. This was verified empirically with
 * {@code -Xlog:class+init}: constructing a {@code SessionString} initialized
 * {@code CommandHistory$SessionString} but left {@code CommandHistory} itself
 * uninitialized, so the tab-factory registration never ran and no native
 * library ({@code scilocalization} et al.) was loaded. These tests therefore
 * touch neither a live Scilab engine, nor a display, nor JNI.
 *
 * <p>{@code SessionString} is reached through its enclosing type name from
 * within the same package, which also grants access to its package-private
 * constructor, {@code toString()} and backing field.
 */
class CommandHistorySessionStringTest {

    /** A representative session banner, in the exact shape CommandHistory wraps. */
    private static final String BANNER = "// -- 18/07/2026 15:30:00 -- //";

    @Test
    void toStringReturnsTheWrappedTextVerbatim() {
        assertEquals("disp(\"hello\")", new CommandHistory.SessionString("disp(\"hello\")").toString());
    }

    @Test
    void realSessionBannerIsPreservedExactly() {
        // This is the only kind of value the production code ever wraps: the
        // renderer relies on toString() reproducing the banner unchanged.
        assertEquals(BANNER, new CommandHistory.SessionString(BANNER).toString());
    }

    @Test
    void emptyStringIsPreservedExactly() {
        assertEquals("", new CommandHistory.SessionString("").toString());
    }

    @Test
    void whitespaceAndNewlinesAreNotTrimmedOrAltered() {
        // History content can carry leading/trailing spaces and embedded
        // newlines; the holder must not normalize them (getSelectedCommands
        // reassembles commands verbatim from these user objects).
        String messy = "  a = 1;\n\tb = 2;  \n";
        assertEquals(messy, new CommandHistory.SessionString(messy).toString());
    }

    @Test
    void unicodeIsPreservedExactly() {
        String unicode = "// -- séance 5 août: θ = π/2 -- //";
        assertEquals(unicode, new CommandHistory.SessionString(unicode).toString());
    }

    @Test
    void toStringIsStableAcrossRepeatedCalls() {
        CommandHistory.SessionString ss = new CommandHistory.SessionString(BANNER);
        String first = ss.toString();
        assertEquals(first, ss.toString());
        assertSame(first, ss.toString(), "toString returns the same String reference it was given");
    }

    @Test
    void distinctInstancesWithEqualTextAreNotEqual() {
        // Characterization: SessionString overrides neither equals nor
        // hashCode, so it keeps Object identity semantics. That is exactly what
        // CommandHistory needs — each session node is a distinct tree user
        // object even when two sessions happen to share a banner string.
        CommandHistory.SessionString a = new CommandHistory.SessionString(BANNER);
        CommandHistory.SessionString b = new CommandHistory.SessionString(BANNER);
        assertNotSame(a, b);
        assertNotEquals(a, b, "value-equality is intentionally NOT provided; identity is used");
    }

    @Test
    void instanceEqualsItselfAndHasStableHashCode() {
        CommandHistory.SessionString a = new CommandHistory.SessionString(BANNER);
        assertEquals(a, a, "identity equality must be reflexive");
        assertEquals(a.hashCode(), a.hashCode(), "identity hashCode must be stable");
    }

    @Test
    void nullTextYieldsNullToString_quirk() {
        // Defect/quirk characterization: the constructor performs no null
        // check, so a null argument survives into toString() and is returned
        // as-is. A toString() override that can return null is unusual and
        // would surface as a NullPointerException / literal "null" in the tree
        // renderer; production code never does this (it only wraps real banner
        // lines), but the class does not defend against it.
        assertNull(new CommandHistory.SessionString(null).toString());
    }

    @Test
    void wrappedTextFieldIsMutable_notAValueObject() {
        // Characterization: the backing field is a non-final, package-visible
        // String, so SessionString is a mutable holder rather than a true
        // immutable value object. toString() reflects the field's current
        // value, so mutating it after construction changes what toString
        // reports. Documented here so the (in-package) coupling is deliberate.
        CommandHistory.SessionString ss = new CommandHistory.SessionString("before");
        assertEquals("before", ss.toString());
        ss.s = "after";
        assertEquals("after", ss.toString());
    }
}
