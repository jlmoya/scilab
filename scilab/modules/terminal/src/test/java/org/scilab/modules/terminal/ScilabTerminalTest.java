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

package org.scilab.modules.terminal;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hermetic contract tests for {@link ScilabTerminal}.
 *
 * <p><b>Why this is a shape test, not a behaviour test.</b> {@code ScilabTerminal}
 * cannot be <em>class-initialised</em> in a hermetic JVM: its static initialiser runs
 * {@code ScilabTabFactory.getInstance().addTabFactory(TerminalTabFactory.getInstance())},
 * and loading {@code ScilabTabFactory} eagerly evaluates its static field
 * {@code Messages.gettext("Empty tab")} -&gt; {@code MessagesJNI.gettext}, a
 * {@code native} method whose class-init does {@code System.loadLibrary("scilocalization")}.
 * With no native library on the path that native call throws {@link UnsatisfiedLinkError}.
 * Because <em>any</em> use of a static member (a method call, a non-constant field read)
 * triggers that initialiser, the whole {@code INSTANCES} lifecycle API
 * ({@code isTerminalOpen}, {@code terminalCount}, {@code getTerminal}, {@code isValidUUID},
 * {@code closeTerminal}, {@code closeAllTerminals}, {@code getLastError}, ...) is out of
 * reach of a unit test. On top of that every instance spins up a JNA PTY, a live login
 * shell and a JediTerm/FlexDock widget graph, so the constructor and the docking
 * choreography are non-hermetic too.
 *
 * <p><b>What IS hermetic</b> is the class's published <em>shape</em>: it is a
 * {@code final} Scilab dockable {@code SimpleTab}; instances are minted only through the
 * factory method (the sole constructor is private); and it exposes a specific static
 * lifecycle / introspection API that {@code etc/terminal.*} and the tab factory call
 * into. These are pinned reflectively with a <b>load-without-initialise</b>
 * ({@code Class.forName(name, false, cl)}), which never runs the static initialiser, and
 * via the compile-time {@code TITLE} constant (an inlined constant reference likewise
 * never loads the class). This mirrors the reflective idiom already used by
 * {@code TerminalAutoOpenTest} in this module.
 */
public class ScilabTerminalTest {

    private static final String FQN = "org.scilab.modules.terminal.ScilabTerminal";
    private static final String DOCKABLE = "org.scilab.modules.gui.bridge.tab.SwingScilabDockablePanel";
    private static final String SIMPLE_TAB = "org.scilab.modules.gui.tab.SimpleTab";

    /** Load the class WITHOUT running its (non-hermetic) static initialiser. */
    private static Class<?> load() throws ClassNotFoundException {
        return Class.forName(FQN, false, ScilabTerminalTest.class.getClassLoader());
    }

    private static void assertPublicStatic(Method m) {
        assertTrue(Modifier.isPublic(m.getModifiers()), m.getName() + " is public");
        assertTrue(Modifier.isStatic(m.getModifiers()), m.getName() + " is static");
    }

    /* --------------------------------------------------------- published constant */

    @Test
    public void titleConstantIsTerminal() {
        // A compile-time String constant: this reference is inlined by javac and never
        // loads/initialises ScilabTerminal, so the assertion stays hermetic even though
        // the class itself cannot be initialised here.
        assertEquals("Terminal", ScilabTerminal.TITLE,
                     "the tab title is the public 'Terminal' contract");
    }

    /* -------------------------------------------------------------- class identity */

    @Test
    public void theClassIsAFinalDockableSimpleTab() throws Exception {
        Class<?> c = load();
        assertTrue(Modifier.isPublic(c.getModifiers()), "public class");
        assertTrue(Modifier.isFinal(c.getModifiers()),
                   "final: the terminal tab is not meant to be subclassed");
        assertEquals(DOCKABLE, c.getSuperclass().getName(),
                     "it IS a Scilab dockable panel");
        boolean declaresSimpleTab = false;
        for (Class<?> i : c.getInterfaces()) {
            if (SIMPLE_TAB.equals(i.getName())) {
                declaresSimpleTab = true;
            }
        }
        assertTrue(declaresSimpleTab, "declares the SimpleTab contract");
    }

    @Test
    public void theSoleConstructorIsPrivateSoTabsComeOnlyFromTheFactory() throws Exception {
        Class<?> c = load();
        Constructor<?>[] ctors = c.getDeclaredConstructors();
        assertEquals(1, ctors.length, "exactly one constructor");
        assertEquals(1, ctors[0].getParameterCount(), "it takes the uuid");
        assertEquals(String.class, ctors[0].getParameterTypes()[0], "the uuid is a String");
        assertTrue(Modifier.isPrivate(ctors[0].getModifiers()),
                   "private: instances are minted only by createTerminalTab(uuid)");
    }

    /* ------------------------------------------------------------ static factory */

    @Test
    public void createTerminalTabIsTheStaticFactoryReturningADockablePanel() throws Exception {
        Method m = load().getDeclaredMethod("createTerminalTab", String.class);
        assertPublicStatic(m);
        assertEquals(DOCKABLE, m.getReturnType().getName(),
                     "the factory hands back the dockable panel it registered");
    }

    /* ----------------------------------------------------------- lifecycle hooks */

    @Test
    public void lifecycleEntryPointsAreStaticVoidHooks() throws Exception {
        Class<?> c = load();
        // Fire-and-forget entry points invoked from the terminal() macro / menus and
        // from etc/terminal.quit on shutdown.
        for (String name : new String[] {"openTerminal", "resetDesktop", "closeAllTerminals"}) {
            Method m = c.getDeclaredMethod(name);
            assertPublicStatic(m);
            assertEquals(void.class, m.getReturnType(), name + " returns void");
        }
        Method close = c.getDeclaredMethod("closeTerminal", String.class);
        assertPublicStatic(close);
        assertEquals(void.class, close.getReturnType(), "closeTerminal(uuid) returns void");
    }

    /* ----------------------------------------------------- introspection surface */

    @Test
    public void introspectionApiReportsTerminalState() throws Exception {
        Class<?> c = load();

        Method isOpen = c.getDeclaredMethod("isTerminalOpen");
        assertPublicStatic(isOpen);
        assertEquals(boolean.class, isOpen.getReturnType(), "isTerminalOpen -> boolean");

        Method count = c.getDeclaredMethod("terminalCount");
        assertPublicStatic(count);
        assertEquals(int.class, count.getReturnType(), "terminalCount -> int");

        Method lastError = c.getDeclaredMethod("getLastError");
        assertPublicStatic(lastError);
        assertEquals(String.class, lastError.getReturnType(), "getLastError -> String");

        Method valid = c.getDeclaredMethod("isValidUUID", String.class);
        assertPublicStatic(valid);
        assertEquals(boolean.class, valid.getReturnType(), "isValidUUID(uuid) -> boolean");

        Method get = c.getDeclaredMethod("getTerminal", String.class);
        assertPublicStatic(get);
        assertEquals(FQN, get.getReturnType().getName(),
                     "getTerminal(uuid) yields the ScilabTerminal itself");
    }

    @Test
    public void getAsSimpleTabIsTheInstanceViewRequiredBySimpleTab() throws Exception {
        Method m = load().getDeclaredMethod("getAsSimpleTab");
        assertTrue(Modifier.isPublic(m.getModifiers()), "getAsSimpleTab is public");
        assertFalse(Modifier.isStatic(m.getModifiers()), "getAsSimpleTab is an instance view");
        assertEquals(SIMPLE_TAB, m.getReturnType().getName(),
                     "the SimpleTab contract returns a SimpleTab");
    }
}
