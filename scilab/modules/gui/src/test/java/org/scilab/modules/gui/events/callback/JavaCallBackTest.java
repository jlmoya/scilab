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

package org.scilab.modules.gui.events.callback;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.AbstractAction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link JavaCallBack}.
 *
 * <p>{@code JavaCallBack} is an abstract {@link CommonCallBack} whose two static
 * factories ({@link JavaCallBack#create(String)} and
 * {@link JavaCallBack#createOutOfXclickAndXgetmouse(String)}) return anonymous
 * subclasses. Each subclass's {@code callBack()} parses the stored command as a
 * {@code fully.qualified.ClassName.methodName(optionalArgs)} string and invokes
 * the named method by pure-JVM reflection ({@link Class#forName(String)} +
 * {@link java.lang.reflect.Method#invoke}). No native Scilab runtime is touched,
 * so the whole class is testable in isolation.
 *
 * <p>To observe the reflective dispatch we point the callbacks at {@link Target},
 * a public reflection sink defined in this test whose {@code public static}
 * methods record their invocation in static fields. {@link #resetTarget()} clears
 * those fields before every test so order does not matter. The command strings
 * reference {@code Target} via its binary name (with the {@code $} nested-class
 * separator) obtained from {@link Class#getName()} — never a hard-coded literal —
 * so the tests survive the class being moved or renamed.
 *
 * <p>Several tests are explicitly named {@code *_defect}: they characterize
 * current, arguably-wrong behavior (an un-swallowed exception, silent argument
 * loss) so that a future change is forced to acknowledge it.
 */
public class JavaCallBackTest {

    /** Binary name (…$Target) that {@code Class.forName} resolves inside the callbacks. */
    private static final String TARGET = Target.class.getName();

    /** Distinctive value a recording field holds until a callback overwrites it. */
    private static final int SENTINEL = Integer.MIN_VALUE;

    /**
     * Public reflection sink invoked by the callbacks under test. Must be a
     * {@code public static} nested class with {@code public} methods so that
     * {@code Class.forName}/{@code getMethod}/{@code getMethods} see it and
     * {@code Method.invoke} needs no {@code setAccessible}.
     */
    public static class Target {

        static boolean pinged;
        static int recordedInt;
        static int recordedA;
        static int recordedB;
        static boolean stringMethodCalled;
        static String recordedString;
        static boolean instancePinged;

        static void reset() {
            pinged = false;
            recordedInt = SENTINEL;
            recordedA = SENTINEL;
            recordedB = SENTINEL;
            stringMethodCalled = false;
            recordedString = "unset";
            instancePinged = false;
        }

        public static void ping() {
            pinged = true;
        }

        public static void record(int v) {
            recordedInt = v;
        }

        public static void twoInts(int a, int b) {
            recordedA = a;
            recordedB = b;
        }

        public static void recordString(String s) {
            stringMethodCalled = true;
            recordedString = s;
        }

        /** Non-static on purpose: the callbacks can only reach static methods. */
        public void instancePing() {
            instancePinged = true;
        }
    }

    @BeforeEach
    public void resetTarget() {
        Target.reset();
    }

    // ------------------------------------------------------------------
    // create(): construction, identity, type hierarchy
    // ------------------------------------------------------------------

    @Test
    public void createReturnsJavaCallBackWithinTheSwingActionHierarchy() {
        JavaCallBack cb = JavaCallBack.create(TARGET + ".ping");
        assertInstanceOf(JavaCallBack.class, cb);
        assertInstanceOf(CommonCallBack.class, cb);
        assertInstanceOf(AbstractAction.class, cb);
        assertInstanceOf(ActionListener.class, cb);
    }

    @Test
    public void createStoresTheCommandVerbatim() {
        String command = TARGET + ".record(42)";
        assertEquals(command, JavaCallBack.create(command).getCommand());
    }

    @Test
    public void createReturnsAFreshInstanceEachCall() {
        JavaCallBack a = JavaCallBack.create(TARGET + ".ping");
        JavaCallBack b = JavaCallBack.create(TARGET + ".ping");
        assertNotSame(a, b);
    }

    @Test
    public void createToleratesNullCommandAtConstructionTime() {
        // Construction never dereferences the command, so this is fine even
        // though callBack() later cannot cope with it (see the *_defect test).
        assertNull(JavaCallBack.create(null).getCommand());
    }

    // ------------------------------------------------------------------
    // create().callBack(): no-argument dispatch
    // ------------------------------------------------------------------

    @Test
    public void callBackInvokesPublicStaticNoArgMethod() {
        JavaCallBack.create(TARGET + ".ping").callBack();
        assertTrue(Target.pinged);
    }

    @Test
    public void callBackInvokesNoArgMethodWrittenWithEmptyParentheses() {
        // "…ping()" routes through the argument branch, where getArguments()
        // sees an empty arg string and returns null → still a no-arg invoke.
        JavaCallBack.create(TARGET + ".ping()").callBack();
        assertTrue(Target.pinged);
    }

    @Test
    public void callBackIgnoresSuppliedArgumentsWhenTheMethodTakesNone() {
        JavaCallBack.create(TARGET + ".ping(whatever,ignored)").callBack();
        assertTrue(Target.pinged);
    }

    // ------------------------------------------------------------------
    // create().callBack(): int-argument conversion (getArguments + converter)
    // ------------------------------------------------------------------

    @Test
    public void callBackConvertsAndPassesADecimalIntArgument() {
        JavaCallBack.create(TARGET + ".record(42)").callBack();
        assertEquals(42, Target.recordedInt);
    }

    @Test
    public void callBackDecodesHexIntArgumentViaIntegerDecode() {
        JavaCallBack.create(TARGET + ".record(0x1F)").callBack();
        assertEquals(31, Target.recordedInt);
    }

    @Test
    public void callBackDecodesNegativeIntArgument() {
        JavaCallBack.create(TARGET + ".record(-7)").callBack();
        assertEquals(-7, Target.recordedInt);
    }

    @Test
    public void callBackTrimsWhitespaceAroundIntArguments() {
        JavaCallBack.create(TARGET + ".record(  42  )").callBack();
        assertEquals(42, Target.recordedInt);
    }

    @Test
    public void callBackConvertsEachOfSeveralIntArguments() {
        JavaCallBack.create(TARGET + ".twoInts( 3 , 4 )").callBack();
        assertEquals(3, Target.recordedA);
        assertEquals(4, Target.recordedB);
    }

    @Test
    public void callBackWithMoreArgumentsThanParametersUsesOnlyTheLeadingOnes() {
        // getArguments caps at min(paramCount, argCount): record takes one int,
        // so the trailing "6" is dropped and the call still succeeds.
        JavaCallBack.create(TARGET + ".record(5,6)").callBack();
        assertEquals(5, Target.recordedInt);
    }

    // ------------------------------------------------------------------
    // create().callBack(): documented conversion limits & swallowed failures
    // ------------------------------------------------------------------

    @Test
    public void callBackPassesNullForStringArgsBecauseOnlyIntHasAConverter_defect() {
        // No converter is registered for String.class, so the slot is left at its
        // default null: the method IS invoked, but always with null, never with
        // the literal text the user wrote.
        JavaCallBack.create(TARGET + ".recordString(hello)").callBack();
        assertTrue(Target.stringMethodCalled, "the method should still be invoked");
        assertNull(Target.recordedString, "the 'hello' text is silently lost");
    }

    @Test
    public void callBackSwallowsUnparseableIntArgumentAndLeavesTheMethodUncalled() {
        // Integer.decode("abc") fails, the converter returns null, and invoking a
        // primitive-int method with null throws IllegalArgumentException, which
        // callBack() catches. Net effect: no throw, no side effect.
        assertDoesNotThrow(() -> JavaCallBack.create(TARGET + ".record(abc)").callBack());
        assertEquals(SENTINEL, Target.recordedInt);
    }

    @Test
    public void callBackSwallowsWrongArgumentCountAndLeavesTheMethodUncalled() {
        // twoInts needs two ints; supplying one yields a length-1 array, so the
        // reflective invoke throws IllegalArgumentException (wrong arg count),
        // which is caught.
        assertDoesNotThrow(() -> JavaCallBack.create(TARGET + ".twoInts(3)").callBack());
        assertEquals(SENTINEL, Target.recordedA);
        assertEquals(SENTINEL, Target.recordedB);
    }

    @Test
    public void callBackCannotInvokeInstanceMethods() {
        // The invoke target is Class.class, never a Target instance, so an
        // instance method fails with IllegalArgumentException (caught) and its
        // side effect never happens.
        assertDoesNotThrow(() -> JavaCallBack.create(TARGET + ".instancePing").callBack());
        assertFalse(Target.instancePinged);
    }

    @Test
    public void callBackSwallowsClassNotFound() {
        assertDoesNotThrow(() ->
            JavaCallBack.create("com.example.Nope.method").callBack());
        assertFalse(Target.pinged);
    }

    @Test
    public void callBackSwallowsNoSuchMethodWhenClassExists() {
        assertDoesNotThrow(() ->
            JavaCallBack.create(TARGET + ".methodThatDoesNotExist").callBack());
        assertFalse(Target.pinged);
    }

    // ------------------------------------------------------------------
    // create().callBack(): defects — inputs the catch block does NOT cover
    // ------------------------------------------------------------------

    @Test
    public void callBackThrowsIndexOutOfBoundsForACommandWithoutADot_defect() {
        // With no '.', lastIndexOf(".") is -1 and cmd.substring(0, -1) throws
        // StringIndexOutOfBoundsException — an IndexOutOfBoundsException, which is
        // NOT among the exceptions callBack() catches, so it escapes.
        JavaCallBack cb = JavaCallBack.create("noDotCommand");
        assertThrows(IndexOutOfBoundsException.class, cb::callBack);
    }

    @Test
    public void callBackThrowsIndexOutOfBoundsForAnEmptyCommand_defect() {
        // "" is a degenerate no-dot command: "".substring(0, -1) throws.
        JavaCallBack cb = JavaCallBack.create("");
        assertThrows(IndexOutOfBoundsException.class, cb::callBack);
    }

    @Test
    public void callBackThrowsNullPointerForANullCommand_defect() {
        // callBack() dereferences the command (indexOf) without a null guard, and
        // NullPointerException is not caught.
        JavaCallBack cb = JavaCallBack.create(null);
        assertThrows(NullPointerException.class, cb::callBack);
    }

    // ------------------------------------------------------------------
    // createOutOfXclickAndXgetmouse(): construction & simpler dispatch
    // ------------------------------------------------------------------

    @Test
    public void xclickFactoryReturnsJavaCallBackWithinTheSwingActionHierarchy() {
        CommonCallBack cb = JavaCallBack.createOutOfXclickAndXgetmouse(TARGET + ".ping");
        assertInstanceOf(JavaCallBack.class, cb);
        assertInstanceOf(CommonCallBack.class, cb);
        assertInstanceOf(AbstractAction.class, cb);
    }

    @Test
    public void xclickFactoryStoresTheCommandVerbatim() {
        String command = TARGET + ".ping";
        assertEquals(command, JavaCallBack.createOutOfXclickAndXgetmouse(command).getCommand());
    }

    @Test
    public void xclickCallBackInvokesPublicStaticNoArgMethod() {
        JavaCallBack.createOutOfXclickAndXgetmouse(TARGET + ".ping").callBack();
        assertTrue(Target.pinged);
    }

    @Test
    public void xclickCallBackDoesNotParseParentheses_soParensFailSilently() {
        // Unlike create(), this variant treats the whole command as the method
        // name. "ping()" is therefore not a known method → NoSuchMethodException,
        // which is caught. Contrast with create()'s empty-parens handling.
        assertDoesNotThrow(() ->
            JavaCallBack.createOutOfXclickAndXgetmouse(TARGET + ".ping()").callBack());
        assertFalse(Target.pinged);
    }

    @Test
    public void xclickCallBackThrowsWhenAskedToInvokeAnInstanceMethod_defect() {
        // Unlike create()'s callBack (whose catch clause lists
        // IllegalArgumentException), this variant's catch block omits it.
        // getMethod("instancePing") resolves the public instance method, and
        // invoking it against the Class receiver throws an
        // IllegalArgumentException that escapes uncaught. The side effect still
        // never happens — the throw precedes the method body.
        CommonCallBack cb = JavaCallBack.createOutOfXclickAndXgetmouse(TARGET + ".instancePing");
        assertThrows(IllegalArgumentException.class, cb::callBack);
        assertFalse(Target.instancePinged);
    }

    @Test
    public void xclickCallBackSwallowsClassNotFound() {
        assertDoesNotThrow(() ->
            JavaCallBack.createOutOfXclickAndXgetmouse("com.example.Nope.method").callBack());
        assertFalse(Target.pinged);
    }

    @Test
    public void xclickCallBackThrowsIndexOutOfBoundsForACommandWithoutADot_defect() {
        CommonCallBack cb = JavaCallBack.createOutOfXclickAndXgetmouse("noDotCommand");
        assertThrows(IndexOutOfBoundsException.class, cb::callBack);
    }

    // ------------------------------------------------------------------
    // createOutOfXclickAndXgetmouse(): actionPerformed delegates to callBack
    // ------------------------------------------------------------------

    @Test
    public void xclickActionPerformedDelegatesToCallBack() {
        CommonCallBack cb = JavaCallBack.createOutOfXclickAndXgetmouse(TARGET + ".ping");
        // The overridden actionPerformed calls callBack() directly (no
        // GlobalEventWatcher path), so the reflective invoke fires.
        cb.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "go"));
        assertTrue(Target.pinged);
    }
}
