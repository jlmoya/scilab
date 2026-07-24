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

package org.scilab.modules.gui.messagebox;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import org.scilab.modules.gui.messagebox.ScilabModalDialog.AnswerOption;
import org.scilab.modules.gui.messagebox.ScilabModalDialog.ButtonType;
import org.scilab.modules.gui.messagebox.ScilabModalDialog.IconType;

/**
 * Hermetic unit tests for {@link ScilabModalDialog}.
 *
 * <p>{@code ScilabModalDialog} is a static-only facade whose {@code show(...)}
 * overloads all funnel into a single method that instantiates a
 * {@code SwingScilabMessageBox} (a {@code JDialog}) and calls
 * {@code displayAndWait()} on a modal dialog. That path needs a live Swing/GUI
 * runtime (it would throw {@code HeadlessException} or block on user input),
 * so the {@code show} methods themselves cannot be exercised hermetically.</p>
 *
 * <p>What <em>is</em> hermetic — and is the public contract callers actually
 * depend on — is the three nested enums ({@link ButtonType}, {@link IconType},
 * {@link AnswerOption}) and the class/constructor shape. These tests pin the
 * enum constant sets, their order (ordinals are load-bearing: the {@code show}
 * switch statements map button indices to answers positionally), the
 * {@code valueOf} contract, and the utility-class invariants (final class,
 * single private constructor, and every {@code show} overload being a public
 * static method returning {@link AnswerOption}).</p>
 */
class ScilabModalDialogTest {

    // ------------------------------------------------------------------
    // ButtonType
    // ------------------------------------------------------------------

    @Test
    void buttonTypeHasExactlyTheDocumentedConstantsInOrder() {
        assertArrayEquals(
            new String[] {"OK", "OK_CANCEL", "YES_NO", "YES_NO_CANCEL",
                          "CANCEL_OR_SAVE_AND_EXECUTE"},
            names(ButtonType.values()),
            "ButtonType constant set/order is part of the public contract");
    }

    @Test
    void buttonTypeOrdinalsAreStable() {
        assertEquals(0, ButtonType.OK.ordinal());
        assertEquals(1, ButtonType.OK_CANCEL.ordinal());
        assertEquals(2, ButtonType.YES_NO.ordinal());
        assertEquals(3, ButtonType.YES_NO_CANCEL.ordinal());
        assertEquals(4, ButtonType.CANCEL_OR_SAVE_AND_EXECUTE.ordinal());
    }

    @Test
    void buttonTypeValueOfRoundTripsEveryConstant() {
        for (ButtonType b : ButtonType.values()) {
            assertSame(b, ButtonType.valueOf(b.name()));
        }
    }

    @Test
    void buttonTypeValueOfRejectsUnknownName() {
        assertThrows(IllegalArgumentException.class,
                     () -> ButtonType.valueOf("NOT_A_BUTTON"));
    }

    @Test
    void buttonTypeValueOfRejectsNull() {
        assertThrows(NullPointerException.class, () -> ButtonType.valueOf(null));
    }

    // ------------------------------------------------------------------
    // IconType
    // ------------------------------------------------------------------

    @Test
    void iconTypeHasExactlyTheDocumentedConstantsInOrder() {
        assertArrayEquals(
            new String[] {"ERROR_ICON", "INFORMATION_ICON", "PASSWORD_ICON",
                          "QUESTION_ICON", "WARNING_ICON", "SCILAB_ICON"},
            names(IconType.values()),
            "IconType constant set/order is part of the public contract");
    }

    @Test
    void iconTypeOrdinalsAreStable() {
        assertEquals(0, IconType.ERROR_ICON.ordinal());
        assertEquals(1, IconType.INFORMATION_ICON.ordinal());
        assertEquals(2, IconType.PASSWORD_ICON.ordinal());
        assertEquals(3, IconType.QUESTION_ICON.ordinal());
        assertEquals(4, IconType.WARNING_ICON.ordinal());
        assertEquals(5, IconType.SCILAB_ICON.ordinal());
    }

    @Test
    void iconTypeValueOfRoundTripsEveryConstant() {
        for (IconType i : IconType.values()) {
            assertSame(i, IconType.valueOf(i.name()));
        }
    }

    @Test
    void iconTypeValueOfRejectsUnknownName() {
        assertThrows(IllegalArgumentException.class,
                     () -> IconType.valueOf("RAINBOW_ICON"));
    }

    @Test
    void iconTypeValueOfRejectsNull() {
        assertThrows(NullPointerException.class, () -> IconType.valueOf(null));
    }

    // ------------------------------------------------------------------
    // AnswerOption
    // ------------------------------------------------------------------

    @Test
    void answerOptionHasExactlyTheDocumentedConstantsInOrder() {
        assertArrayEquals(
            new String[] {"OK_OPTION", "CANCEL_OPTION", "YES_OPTION",
                          "NO_OPTION", "SAVE_EXECUTE_OPTION"},
            names(AnswerOption.values()),
            "AnswerOption constant set/order is part of the public contract");
    }

    @Test
    void answerOptionOrdinalsAreStable() {
        assertEquals(0, AnswerOption.OK_OPTION.ordinal());
        assertEquals(1, AnswerOption.CANCEL_OPTION.ordinal());
        assertEquals(2, AnswerOption.YES_OPTION.ordinal());
        assertEquals(3, AnswerOption.NO_OPTION.ordinal());
        assertEquals(4, AnswerOption.SAVE_EXECUTE_OPTION.ordinal());
    }

    @Test
    void answerOptionValueOfRoundTripsEveryConstant() {
        for (AnswerOption a : AnswerOption.values()) {
            assertSame(a, AnswerOption.valueOf(a.name()));
        }
    }

    @Test
    void answerOptionValueOfRejectsUnknownName() {
        assertThrows(IllegalArgumentException.class,
                     () -> AnswerOption.valueOf("MAYBE_OPTION"));
    }

    @Test
    void answerOptionValueOfRejectsNull() {
        assertThrows(NullPointerException.class, () -> AnswerOption.valueOf(null));
    }

    @Test
    void valuesReturnsAFreshDefensiveCopy() {
        // JLS guarantees a new array per call; a caller mutating it must not
        // corrupt the shared enum state.
        AnswerOption[] first = AnswerOption.values();
        AnswerOption[] second = AnswerOption.values();
        assertNotSame(first, second);
        assertArrayEquals(first, second);
        first[0] = AnswerOption.NO_OPTION;
        assertSame(AnswerOption.OK_OPTION, AnswerOption.values()[0],
                   "mutating the returned array must not affect the enum");
    }

    // ------------------------------------------------------------------
    // Utility-class structure
    // ------------------------------------------------------------------

    @Test
    void classIsPublicAndFinal() {
        int mod = ScilabModalDialog.class.getModifiers();
        assertTrue(Modifier.isPublic(mod), "class should be public");
        assertTrue(Modifier.isFinal(mod), "static-only facade should be final");
    }

    @Test
    void soleConstructorIsPrivateAndNoArg() {
        Constructor<?>[] ctors = ScilabModalDialog.class.getDeclaredConstructors();
        assertEquals(1, ctors.length, "expected a single constructor");
        assertEquals(0, ctors[0].getParameterCount(), "constructor should be no-arg");
        assertTrue(Modifier.isPrivate(ctors[0].getModifiers()),
                   "constructor of a static-only facade should be private");
    }

    @Test
    void privateConstructorIsInvocableViaReflection() throws Exception {
        // The constructor body is empty and has no side effects, so exercising
        // it stays hermetic while documenting that instantiation is possible
        // (only) through reflection.
        Constructor<ScilabModalDialog> ctor =
            ScilabModalDialog.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        assertNotNull(ctor.newInstance());
    }

    @Test
    void everyShowOverloadIsPublicStaticAndReturnsAnswerOption() {
        Method[] shows = Stream.of(ScilabModalDialog.class.getDeclaredMethods())
                         .filter(m -> m.getName().equals("show"))
                         .toArray(Method[]::new);

        assertTrue(shows.length > 0, "expected at least one show(...) overload");
        for (Method m : shows) {
            int mod = m.getModifiers();
            assertTrue(Modifier.isPublic(mod), m + " should be public");
            assertTrue(Modifier.isStatic(mod), m + " should be static");
            assertEquals(AnswerOption.class, m.getReturnType(),
                         m + " should return AnswerOption");
        }
    }

    @Test
    void keyPublicEntryPointOverloadsExist() throws Exception {
        // The two most-used facade entry points documented in the Javadoc.
        assertNotNull(ScilabModalDialog.class.getMethod(
                          "show",
                          org.scilab.modules.gui.tab.SimpleTab.class, String.class));
        assertNotNull(ScilabModalDialog.class.getMethod(
                          "show",
                          java.awt.Component.class, String[].class, String.class,
                          IconType.class, ButtonType.class));
    }

    private static String[] names(Enum<?>[] constants) {
        return Arrays.stream(constants).map(Enum::name).toArray(String[]::new);
    }
}
