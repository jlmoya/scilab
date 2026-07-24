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

package org.scilab.modules.gui.uielement;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;

import org.junit.jupiter.api.Test;
import org.scilab.modules.gui.menubar.MenuBar;
import org.scilab.modules.gui.textbox.TextBox;
import org.scilab.modules.gui.toolbar.ToolBar;
import org.scilab.modules.gui.utils.Layout;
import org.scilab.modules.gui.utils.Position;
import org.scilab.modules.gui.utils.Size;

/**
 * Hermetic unit tests for the abstract base {@link ScilabUIElement}.
 *
 * <p>{@code ScilabUIElement} is a pure state holder: six reference-typed
 * properties (three {@link Layout}s plus a {@link MenuBar}, {@link ToolBar}
 * and {@link TextBox} info bar), each with a trivial getter/setter, and one
 * abstract {@code draw()}. The tests drive a minimal concrete subclass and
 * verify exact-reference round-tripping, cross-property independence,
 * overwrite/clear semantics, and the abstract/interface contract. None of
 * this touches the GUI or native runtime.
 */
class ScilabUIElementTest {

    /** Smallest concrete {@link ScilabUIElement}: fills the abstract holes with no-ops. */
    private static final class FakeUIElement extends ScilabUIElement {
        private boolean drawn = false;

        @Override
        public void draw() {
            drawn = true;
        }

        @Override
        public Size getDims() {
            return null;
        }

        @Override
        public void setDims(Size newSize) {
        }

        @Override
        public Position getPosition() {
            return null;
        }

        @Override
        public void setPosition(Position newPosition) {
        }

        @Override
        public boolean isVisible() {
            return false;
        }

        @Override
        public void setVisible(boolean newVisibleState) {
        }
    }

    /** A distinct, non-null {@link Layout} instance (Layout is an empty marker interface). */
    private static Layout newLayout() {
        return new Layout() { };
    }

    /**
     * A non-null interface instance via dynamic proxy — lets us assert exact
     * reference identity for interfaces (MenuBar/ToolBar/TextBox) without
     * hand-implementing their (large) method surfaces. Object methods are
     * handled so a failure message can never NPE.
     */
    @SuppressWarnings("unchecked")
    private static <T> T newProxy(Class<T> iface) {
        return (T) Proxy.newProxyInstance(
                   iface.getClassLoader(),
                   new Class<?>[] { iface },
                   (proxy, method, args) -> {
                       switch (method.getName()) {
                           case "hashCode":
                               return System.identityHashCode(proxy);
                           case "equals":
                               return proxy == args[0];
                           case "toString":
                               return iface.getSimpleName() + "$proxy";
                           default:
                               return null;
                       }
                   });
    }

    // ------------------------------------------------------------------
    // Initial state
    // ------------------------------------------------------------------

    @Test
    void freshElementHasAllPropertiesNull() {
        ScilabUIElement e = new FakeUIElement();

        assertNull(e.getBackgroundLayout());
        assertNull(e.getForegroundLayout());
        assertNull(e.getTextLayout());
        assertNull(e.getMenuBar());
        assertNull(e.getToolBar());
        assertNull(e.getInfoBar());
    }

    // ------------------------------------------------------------------
    // Round-trip: setter stores the exact reference the getter returns
    // ------------------------------------------------------------------

    @Test
    void backgroundLayoutRoundTrips() {
        ScilabUIElement e = new FakeUIElement();
        Layout l = newLayout();

        e.setBackgroundLayout(l);

        assertSame(l, e.getBackgroundLayout());
    }

    @Test
    void foregroundLayoutRoundTrips() {
        ScilabUIElement e = new FakeUIElement();
        Layout l = newLayout();

        e.setForegroundLayout(l);

        assertSame(l, e.getForegroundLayout());
    }

    @Test
    void textLayoutRoundTrips() {
        ScilabUIElement e = new FakeUIElement();
        Layout l = newLayout();

        e.setTextLayout(l);

        assertSame(l, e.getTextLayout());
    }

    @Test
    void menuBarRoundTrips() {
        ScilabUIElement e = new FakeUIElement();
        MenuBar m = newProxy(MenuBar.class);

        e.addMenuBar(m);

        assertSame(m, e.getMenuBar());
    }

    @Test
    void toolBarRoundTrips() {
        ScilabUIElement e = new FakeUIElement();
        ToolBar t = newProxy(ToolBar.class);

        e.addToolBar(t);

        assertSame(t, e.getToolBar());
    }

    @Test
    void infoBarRoundTrips() {
        ScilabUIElement e = new FakeUIElement();
        TextBox info = newProxy(TextBox.class);

        e.addInfoBar(info);

        assertSame(info, e.getInfoBar());
    }

    // ------------------------------------------------------------------
    // Independence: no field aliasing / cross-talk between properties
    // ------------------------------------------------------------------

    @Test
    void theThreeLayoutsAreStoredIndependently() {
        ScilabUIElement e = new FakeUIElement();
        Layout bg = newLayout();
        Layout fg = newLayout();
        Layout text = newLayout();

        e.setBackgroundLayout(bg);
        e.setForegroundLayout(fg);
        e.setTextLayout(text);

        assertSame(bg, e.getBackgroundLayout());
        assertSame(fg, e.getForegroundLayout());
        assertSame(text, e.getTextLayout());
    }

    @Test
    void theThreeBarsAreStoredIndependently() {
        ScilabUIElement e = new FakeUIElement();
        MenuBar m = newProxy(MenuBar.class);
        ToolBar t = newProxy(ToolBar.class);
        TextBox info = newProxy(TextBox.class);

        e.addMenuBar(m);
        e.addToolBar(t);
        e.addInfoBar(info);

        assertSame(m, e.getMenuBar());
        assertSame(t, e.getToolBar());
        assertSame(info, e.getInfoBar());
    }

    // ------------------------------------------------------------------
    // Overwrite / clear semantics
    // ------------------------------------------------------------------

    @Test
    void setterOverwritesPreviousValue() {
        ScilabUIElement e = new FakeUIElement();
        Layout first = newLayout();
        Layout second = newLayout();

        e.setBackgroundLayout(first);
        e.setBackgroundLayout(second);

        assertSame(second, e.getBackgroundLayout());
    }

    @Test
    void settingNullClearsPreviousValue() {
        ScilabUIElement e = new FakeUIElement();

        e.addMenuBar(newProxy(MenuBar.class));
        e.addMenuBar(null);

        assertNull(e.getMenuBar());
    }

    // ------------------------------------------------------------------
    // Abstract / interface contract
    // ------------------------------------------------------------------

    @Test
    void drawIsDispatchedToTheSubclass() {
        FakeUIElement e = new FakeUIElement();

        e.draw();

        assertTrue(e.drawn);
    }

    @Test
    void baseClassIsAbstract() {
        assertTrue(Modifier.isAbstract(ScilabUIElement.class.getModifiers()));
    }

    @Test
    void isAUIElement() {
        assertTrue(UIElement.class.isAssignableFrom(ScilabUIElement.class));
        assertTrue(new FakeUIElement() instanceof UIElement);
    }
}
