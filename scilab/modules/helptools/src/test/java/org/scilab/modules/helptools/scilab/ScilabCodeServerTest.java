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

package org.scilab.modules.helptools.scilab;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link ScilabCodeServer}.
 *
 * <p>The server itself binds a {@code ServerSocket} and blocks in an accept loop,
 * which is out of scope for a unit test. What <em>is</em> pure and worth pinning:
 *
 * <ul>
 *   <li>{@code parseCommandLine} — the {@code private static} option/value tokenizer
 *       that turns an {@code argv} array into a map. It performs no I/O, so it is
 *       reached here through reflection and exercised across every branch: short
 *       ({@code -x}) and long ({@code --x}) options, option-takes-value, two options
 *       in a row (first gets an empty value), a bare positional {@code input}, the
 *       trailing-option flush, and the "second bare argument" error that yields
 *       {@code null}; and</li>
 *   <li>the early-return branches of {@code main} — {@code -help}, missing
 *       {@code -port}, non-numeric {@code -maxhandlers}, non-numeric {@code -port} —
 *       every one of which returns <em>before</em> a socket is ever opened. Each is
 *       run under a timeout so that a regression which reached the accept loop would
 *       fail the test instead of hanging the suite.</li>
 * </ul>
 */
public class ScilabCodeServerTest {

    @SuppressWarnings("unchecked")
    private static Map<String, String> parse(String... args) throws Exception {
        Method m = ScilabCodeServer.class.getDeclaredMethod("parseCommandLine", String[].class);
        m.setAccessible(true);
        return (Map<String, String>) m.invoke(null, (Object) args);
    }

    // ---- parseCommandLine: options and values --------------------------

    @Test
    public void shortOptionWithValueIsCaptured() throws Exception {
        Map<String, String> map = parse("-port", "1234");
        assertEquals("1234", map.get("port"));
        assertEquals(1, map.size());
    }

    @Test
    public void longOptionWithValueDropsBothDashes() throws Exception {
        Map<String, String> map = parse("--maxhandlers", "5");
        assertEquals("5", map.get("maxhandlers"));
        assertFalse(map.containsKey("-maxhandlers"));
    }

    @Test
    public void trailingOptionGetsAnEmptyValue() throws Exception {
        // A flag with nothing after it (e.g. -help) is flushed to "" at end of loop.
        Map<String, String> map = parse("-help");
        assertTrue(map.containsKey("help"));
        assertEquals("", map.get("help"));
    }

    @Test
    public void optionImmediatelyFollowedByOptionLeavesFirstEmpty() throws Exception {
        Map<String, String> map = parse("-a", "-b", "value");
        assertEquals("", map.get("a"), "an option interrupted by the next option keeps an empty value");
        assertEquals("value", map.get("b"));
    }

    @Test
    public void twoBareOptionsBothEmpty() throws Exception {
        Map<String, String> map = parse("-a", "-b");
        assertEquals("", map.get("a"));
        assertEquals("", map.get("b"));
        assertEquals(2, map.size());
    }

    // ---- parseCommandLine: positional input ----------------------------

    @Test
    public void singleBareArgumentBecomesInput() throws Exception {
        Map<String, String> map = parse("master.xml");
        assertEquals("master.xml", map.get("input"));
    }

    @Test
    public void optionValueThenTrailingOptionCoexist() throws Exception {
        Map<String, String> map = parse("-port", "80", "-help");
        assertEquals("80", map.get("port"));
        assertEquals("", map.get("help"));
    }

    @Test
    public void secondBareArgumentIsRejectedWithNull() throws Exception {
        // The parser accepts exactly one positional "input"; a second one is a hard error.
        assertNull(parse("first", "second"));
    }

    @Test
    public void emptyArgvYieldsEmptyMap() throws Exception {
        Map<String, String> map = parse();
        assertNotNull(map);
        assertTrue(map.isEmpty());
    }

    @Test
    public void loneDashIsTreatedAsPositionalNotOption() throws Exception {
        // "-" has length 1, so the (length >= 2) guard fails and it is a bare arg.
        Map<String, String> map = parse("-");
        assertEquals("-", map.get("input"));
    }

    // ---- main: early-return branches (never open a socket) -------------

    @Test
    public void mainHelpReturnsPromptly() {
        assertTimeoutPreemptively(Duration.ofSeconds(5),
                                  () -> ScilabCodeServer.main(new String[] {"-help"}));
    }

    @Test
    public void mainWithoutPortReturnsPromptly() {
        assertTimeoutPreemptively(Duration.ofSeconds(5),
                                  () -> ScilabCodeServer.main(new String[0]));
    }

    @Test
    public void mainWithNonNumericMaxHandlersReturnsBeforeBinding() {
        // maxhandlers is validated before the port/socket, so a bad value bails out early.
        assertTimeoutPreemptively(Duration.ofSeconds(5),
                                  () -> ScilabCodeServer.main(new String[] {"-port", "9", "-maxhandlers", "xx"}));
    }

    @Test
    public void mainWithNonNumericPortReturnsBeforeBinding() {
        assertTimeoutPreemptively(Duration.ofSeconds(5),
                                  () -> ScilabCodeServer.main(new String[] {"-port", "not-a-number"}));
    }
}
