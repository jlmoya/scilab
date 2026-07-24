/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Scilab Enterprises
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 *
 */

package org.scilab.modules.completion;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.awt.Component;
import java.awt.Point;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyListener;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractListModel;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.text.JTextComponent;

import com.artenum.rosetta.interfaces.core.CompletionItem;
import com.artenum.rosetta.interfaces.core.InputParsingManager;
import com.artenum.rosetta.interfaces.ui.CompletionWindow;

/**
 * Hermetic tests for the OUTER {@link AbstractSciCompletionWindow} class.
 *
 * <p>Its {@code protected static} inner list model is already covered by
 * {@link AbstractSciCompletionWindowModelTest}. This class pins the rest of the
 * outer window that can be exercised WITHOUT a live display, an event loop, or
 * the native {@code scicompletion} library:</p>
 *
 * <ul>
 *   <li><b>Structural contract</b> (pure reflection, no instantiation): the
 *       class is public + abstract, implements {@link CompletionWindow} plus the
 *       four AWT listener interfaces, and declares the expected abstract /
 *       concrete method surface — mirroring the reflection style already used by
 *       {@link CompletionTest} for the SWIG facade.</li>
 *   <li><b>Behaviour</b>, driven through a tiny in-package concrete subclass
 *       that injects fakes into the {@code protected} collaborator fields (legal
 *       because the test lives in the same package). Only methods that touch
 *       neither a realised widget nor {@link CompletionJNI} are exercised:
 *       {@code setFocusOut}/{@code setInputParsingManager},
 *       {@code setVisible}/{@code isVisible}, {@code focusLost},
 *       {@code getCompletionResult}/{@code getCompletionResultType}, and the
 *       empty listener callbacks.</li>
 * </ul>
 *
 * <p>Deliberately left untested (GUI/native, exactly as the sibling model test
 * notes): {@code setGraphicalContext} (builds the whole Swing tree),
 * {@code addCompletedWord}/{@code keyPressed} (route into
 * {@link Completion#completelineforjava} → JNI), and the drag/resize mouse
 * handlers (need {@code getLocationOnScreen}).</p>
 *
 * <p>Swing usage here is limited to constructing lightweight, unrealised
 * {@link JPanel}/{@link JList}/{@link JComponent} instances and toggling their
 * data/visibility flags — the same headless-safe footprint that the passing
 * {@link SciCompletionItemListCellRendererTest} already relies on (it builds a
 * {@code JLabel}). No peer is realised and no event loop is started.</p>
 */
public class AbstractSciCompletionWindowTest {

    // ------------------------------------------------------------------
    // Test doubles
    // ------------------------------------------------------------------

    /** Concrete window whose abstract hooks are inert; records {@code show()}. */
    private static final class TestWindow extends AbstractSciCompletionWindow {
        int showCount = 0;
        List<CompletionItem> lastShowList;
        Point lastShowLocation;

        @Override public JTextComponent getTextComponent() {
            return null;
        }
        @Override public List<CompletionItem> getCompletionItems() {
            return new ArrayList<CompletionItem>();
        }
        @Override public void show(List<CompletionItem> list, Point location) {
            showCount++;
            lastShowList = list;
            lastShowLocation = location;
        }
    }

    /**
     * A {@link JComponent} whose {@code grabFocus()} is a pure counter. It
     * overrides the (non-final) method WITHOUT calling {@code super}, so no real
     * focus-subsystem traffic happens — we only observe that the window asked
     * the focus-out component to take focus.
     */
    private static final class GrabRecorder extends JComponent {
        private static final long serialVersionUID = 1L;
        int grabs = 0;
        @Override public void grabFocus() {
            grabs++;
        }
    }

    /** Minimal {@link CompletionItem} ordered by {@code methodProfile}. */
    private static final class FakeItem implements CompletionItem {
        private String type;
        private String methodProfile;
        private String returnValue;

        FakeItem(String methodProfile, String type, String returnValue) {
            this.methodProfile = methodProfile;
            this.type = type;
            this.returnValue = returnValue;
        }

        @Override public String getType() {
            return type;
        }
        @Override public String getMethodProfile() {
            return methodProfile;
        }
        @Override public String getReturnValue() {
            return returnValue;
        }
        @Override public String getHelp() {
            return "";
        }
        @Override public void setType(String t) {
            this.type = t;
        }
        @Override public void setMethodProfile(String m) {
            this.methodProfile = m;
        }
        @Override public void setReturnValue(String r) {
            this.returnValue = r;
        }
        @Override public void setHelp(String h) {
        }
        @Override public int compareTo(CompletionItem o) {
            return this.methodProfile.compareTo(o.getMethodProfile());
        }
    }

    /**
     * An {@link InputParsingManager} whose every method returns a default value
     * — built as a {@link Proxy} so the 12-method interface need not be spelled
     * out. Only its identity is used by the tests (never a method call).
     */
    private static InputParsingManager stubParsingManager() {
        return (InputParsingManager) Proxy.newProxyInstance(
                   InputParsingManager.class.getClassLoader(),
                   new Class<?>[] { InputParsingManager.class },
                   new InvocationHandler() {
                       @Override public Object invoke(Object proxy, Method method, Object[] args) {
                           return null;
                       }
                   });
    }

    // ------------------------------------------------------------------
    // Structural contract (reflection only — never instantiates the class)
    // ------------------------------------------------------------------

    @Test
    void classIsPublicAndAbstract() {
        int m = AbstractSciCompletionWindow.class.getModifiers();
        assertTrue(Modifier.isPublic(m));
        assertTrue(Modifier.isAbstract(m));
        assertFalse(Modifier.isInterface(m));
    }

    @Test
    void implementsCompletionWindowAndAllAwtListenerInterfaces() {
        assertTrue(CompletionWindow.class.isAssignableFrom(AbstractSciCompletionWindow.class),
                   "must implement CompletionWindow");
        assertTrue(KeyListener.class.isAssignableFrom(AbstractSciCompletionWindow.class),
                   "must be a KeyListener");
        assertTrue(FocusListener.class.isAssignableFrom(AbstractSciCompletionWindow.class),
                   "must be a FocusListener");
        assertTrue(MouseListener.class.isAssignableFrom(AbstractSciCompletionWindow.class),
                   "must be a MouseListener");
        assertTrue(MouseMotionListener.class.isAssignableFrom(AbstractSciCompletionWindow.class),
                   "must be a MouseMotionListener");
    }

    @Test
    void declaresTheThreeAbstractHooksWithExpectedReturnTypes() throws Exception {
        Method textComponent = AbstractSciCompletionWindow.class.getDeclaredMethod("getTextComponent");
        assertTrue(Modifier.isAbstract(textComponent.getModifiers()));
        assertEquals(JTextComponent.class, textComponent.getReturnType());

        Method completionItems = AbstractSciCompletionWindow.class.getDeclaredMethod("getCompletionItems");
        assertTrue(Modifier.isAbstract(completionItems.getModifiers()));
        assertEquals(List.class, completionItems.getReturnType());

        Method show = AbstractSciCompletionWindow.class.getDeclaredMethod("show", List.class, Point.class);
        assertTrue(Modifier.isAbstract(show.getModifiers()));
        assertEquals(void.class, show.getReturnType());
    }

    @Test
    void concreteCompletionWindowOverridesArePresentAndNonAbstract() throws Exception {
        Object[][] signatures = {
            { "setFocusOut", new Class<?>[] { JComponent.class } },
            { "setInputParsingManager", new Class<?>[] { InputParsingManager.class } },
            { "setGraphicalContext", new Class<?>[] { Component.class } },
            { "getCompletionResult", new Class<?>[] {} },
        };
        for (Object[] sig : signatures) {
            String name = (String) sig[0];
            Class<?>[] params = (Class<?>[]) sig[1];
            Method m = AbstractSciCompletionWindow.class.getDeclaredMethod(name, params);
            assertFalse(Modifier.isAbstract(m.getModifiers()), name + " must be concrete");
            assertTrue(Modifier.isPublic(m.getModifiers()), name + " must be public");
        }
    }

    @Test
    void getCompletionResultAndItsTypeVariantReturnStrings() throws Exception {
        assertEquals(String.class,
                     AbstractSciCompletionWindow.class.getDeclaredMethod("getCompletionResult").getReturnType());

        // getCompletionResultType() is a Scilab addition (not part of the
        // CompletionWindow interface); it must still be a public String getter.
        Method typeGetter = AbstractSciCompletionWindow.class.getDeclaredMethod("getCompletionResultType");
        assertEquals(String.class, typeGetter.getReturnType());
        assertTrue(Modifier.isPublic(typeGetter.getModifiers()));
        assertFalse(Modifier.isAbstract(typeGetter.getModifiers()));
    }

    @Test
    void bothAddCompletedWordOverloadsAreDeclaredAndReturnVoid() throws Exception {
        Method byPosition =
            AbstractSciCompletionWindow.class.getDeclaredMethod("addCompletedWord", int.class);
        Method byStringAndPosition =
            AbstractSciCompletionWindow.class.getDeclaredMethod("addCompletedWord", String.class, int.class);
        assertEquals(void.class, byPosition.getReturnType());
        assertEquals(void.class, byStringAndPosition.getReturnType());
    }

    @Test
    void innerListModelIsProtectedStaticExtendingAbstractListModel() throws Exception {
        Class<?> inner = Class.forName(
                             "org.scilab.modules.completion.AbstractSciCompletionWindow$CompletionItemListModel");
        int mod = inner.getModifiers();
        assertTrue(Modifier.isProtected(mod), "inner model should be protected");
        assertTrue(Modifier.isStatic(mod), "inner model should be static");
        assertEquals(AbstractListModel.class, inner.getSuperclass());
    }

    // ------------------------------------------------------------------
    // Behaviour (concrete subclass + injected fakes; no realised widgets)
    // ------------------------------------------------------------------

    @Test
    void freshWindowHasNoCollaboratorsAndZeroCaret() {
        TestWindow win = new TestWindow();
        // The no-arg constructor is empty: nothing is built, nothing is loaded.
        assertNull(win.window);
        assertNull(win.model);
        assertNull(win.listUI);
        assertNull(win.inputParsingManager);
        assertNull(win.focusOutComponent);
        assertEquals(0, win.currentCaretPosition);
    }

    @Test
    void setFocusOutStoresTheComponent() {
        TestWindow win = new TestWindow();
        GrabRecorder focusOut = new GrabRecorder();
        win.setFocusOut(focusOut);
        assertSame(focusOut, win.focusOutComponent);
    }

    @Test
    void setInputParsingManagerStoresTheManager() {
        TestWindow win = new TestWindow();
        InputParsingManager ipm = stubParsingManager();
        win.setInputParsingManager(ipm);
        assertSame(ipm, win.inputParsingManager);
    }

    @Test
    void setVisibleTrueShowsWindowWithoutGrabbingFocus() {
        TestWindow win = new TestWindow();
        JPanel panel = new JPanel();
        panel.setVisible(false);
        GrabRecorder focusOut = new GrabRecorder();
        win.window = panel;
        win.focusOutComponent = focusOut;

        win.setVisible(true);

        assertTrue(panel.isVisible(), "setVisible(true) must show the window");
        assertEquals(0, focusOut.grabs, "showing must NOT grab focus");
    }

    @Test
    void setVisibleFalseHidesWindowAndGrabsFocusExactlyOnce() {
        TestWindow win = new TestWindow();
        JPanel panel = new JPanel();
        GrabRecorder focusOut = new GrabRecorder();
        win.window = panel;
        win.focusOutComponent = focusOut;

        win.setVisible(false);

        assertFalse(panel.isVisible(), "setVisible(false) must hide the window");
        assertEquals(1, focusOut.grabs, "hiding must hand focus back exactly once");
    }

    @Test
    void isVisibleDelegatesToTheUnderlyingWindow() {
        TestWindow win = new TestWindow();
        JPanel panel = new JPanel();
        win.window = panel;

        panel.setVisible(true);
        assertTrue(win.isVisible());

        panel.setVisible(false);
        assertFalse(win.isVisible());
    }

    @Test
    void setVisibleFalseWithoutFocusOutComponentThrowsNPE() {
        // Defect-characterisation: hiding dereferences focusOutComponent, which
        // production code guarantees non-null via setGraphicalContext/setFocusOut
        // before any hide. The window is still toggled first (side effect), then
        // the null focus-out component blows up.
        TestWindow win = new TestWindow();
        win.window = new JPanel();
        win.focusOutComponent = null;
        assertThrows(NullPointerException.class, () -> win.setVisible(false));
    }

    @Test
    void focusLostAutoHidesTheWindowAndGrabsFocus() {
        TestWindow win = new TestWindow();
        JPanel panel = new JPanel();
        GrabRecorder focusOut = new GrabRecorder();
        win.window = panel;
        win.focusOutComponent = focusOut;

        win.focusLost(new FocusEvent(panel, FocusEvent.FOCUS_LOST));

        assertFalse(panel.isVisible(), "losing focus must hide the completion window");
        assertEquals(1, focusOut.grabs);
    }

    @Test
    void getCompletionResultReturnsSelectedItemsReturnValue() {
        TestWindow win = new TestWindow();
        FakeItem alpha = new FakeItem("alpha", "Function", "alpha()");
        FakeItem bravo = new FakeItem("bravo", "Variable", "bravo");
        win.listUI = new JList(new Object[] { alpha, bravo });
        win.listUI.setSelectedIndex(1);

        assertEquals("bravo", win.getCompletionResult());
    }

    @Test
    void getCompletionResultTypeReturnsSelectedItemsType() {
        TestWindow win = new TestWindow();
        FakeItem alpha = new FakeItem("alpha", "Function", "alpha()");
        FakeItem bravo = new FakeItem("bravo", "Variable", "bravo");
        win.listUI = new JList(new Object[] { alpha, bravo });
        win.listUI.setSelectedIndex(0);

        assertEquals("Function", win.getCompletionResultType());
    }

    @Test
    void getCompletionResultTracksTheSelectionIndex() {
        TestWindow win = new TestWindow();
        FakeItem alpha = new FakeItem("alpha", "Function", "alpha()");
        FakeItem bravo = new FakeItem("bravo", "Variable", "bravo");
        win.listUI = new JList(new Object[] { alpha, bravo });

        win.listUI.setSelectedIndex(0);
        assertEquals("alpha()", win.getCompletionResult());
        win.listUI.setSelectedIndex(1);
        assertEquals("bravo", win.getCompletionResult());
    }

    @Test
    void getCompletionResultWithNoSelectionThrowsNPE() {
        // getSelectedValue() is null when nothing is selected; the
        // (CompletionItem) cast of null succeeds, then getReturnValue()
        // dereferences null. Pins current (guard-free) behaviour.
        TestWindow win = new TestWindow();
        win.listUI = new JList(new Object[] { new FakeItem("x", "Function", "x") });
        win.listUI.clearSelection();
        assertThrows(NullPointerException.class, () -> win.getCompletionResult());
    }

    @Test
    void emptyListenerCallbacksAreNullSafeNoOps() {
        // These override methods are intentionally empty; none dereferences its
        // event, so passing null must not throw.
        TestWindow win = new TestWindow();
        assertDoesNotThrow(() -> {
            win.keyReleased(null);
            win.keyTyped(null);
            win.focusGained(null);
            win.mouseMoved(null);
            win.mouseEntered(null);
            win.mouseExited(null);
            win.mousePressed(null);
        });
    }

    @Test
    void mouseReleasedValidatesTheWindowWithoutThrowing() {
        // mouseReleased ignores its event entirely and calls window.validate();
        // on an unrealised JPanel that is a safe no-op.
        TestWindow win = new TestWindow();
        win.window = new JPanel();
        assertDoesNotThrow(() -> win.mouseReleased(null));
    }
}
