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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link CommandHistoryMouseListener}.
 *
 * Only the parts that need neither a live Scilab engine nor a display are
 * exercised:
 * <ul>
 *   <li>the pure static predicate {@link CommandHistoryMouseListener#isMacOsPopupTrigger},
 *       driven entirely by a synthetic {@link MouseEvent} plus two system
 *       properties ({@code os.name} and {@code java.specification.version});</li>
 *   <li>the four empty {@link MouseListener} callbacks;</li>
 *   <li>a benign {@code mouseClicked} path that takes no branch.</li>
 * </ul>
 * The right-click and double-left-click branches of {@code mouseClicked} reach
 * into {@code CommandHistory}'s static Swing tree and the action callbacks, so
 * they are intentionally out of scope for a hermetic test.
 *
 * <p>AWT-modifier note: {@code isMacOsPopupTrigger} relies on
 * {@link SwingUtilities#isLeftMouseButton} and {@link MouseEvent#isControlDown},
 * which only report correctly when the event is built with the constructor that
 * takes an explicit {@code button} argument — hence {@link #click}.
 */
class CommandHistoryMouseListenerTest {

    private String savedOsName;
    private String savedJavaSpec;
    private Component source;

    @BeforeEach
    void setUp() {
        savedOsName = System.getProperty("os.name");
        savedJavaSpec = System.getProperty("java.specification.version");
        // A Swing (lightweight) component is safe to instantiate headless and
        // is a valid, non-null MouseEvent source.
        source = new JPanel();
    }

    @AfterEach
    void tearDown() {
        restore("os.name", savedOsName);
        restore("java.specification.version", savedJavaSpec);
    }

    private static void restore(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    /**
     * Build a single click for the given AWT button ({@link MouseEvent#BUTTON1}
     * .. {@code BUTTON3}), optionally with CTRL held. The explicit-button
     * constructor is required so {@code isLeftMouseButton}/{@code isControlDown}
     * report as intended.
     */
    private MouseEvent click(int button, boolean ctrl) {
        int mods = 0;
        switch (button) {
            case MouseEvent.BUTTON1:
                mods |= InputEvent.BUTTON1_DOWN_MASK;
                break;
            case MouseEvent.BUTTON2:
                mods |= InputEvent.BUTTON2_DOWN_MASK;
                break;
            case MouseEvent.BUTTON3:
                mods |= InputEvent.BUTTON3_DOWN_MASK;
                break;
            default:
                break;
        }
        if (ctrl) {
            mods |= InputEvent.CTRL_DOWN_MASK;
        }
        return new MouseEvent(source, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(),
                              mods, 5, 5, 1, false, button);
    }

    @Test
    void isAMouseListener() {
        assertTrue(new CommandHistoryMouseListener() instanceof MouseListener);
    }

    @Test
    void popupTriggerWhenMacAndJava16AndLeftCtrlClick() {
        System.setProperty("os.name", "Mac OS X");
        System.setProperty("java.specification.version", "1.6");
        MouseEvent e = click(MouseEvent.BUTTON1, true);
        // Sanity: the synthetic event really is a left button + control click.
        assertTrue(SwingUtilities.isLeftMouseButton(e), "expected a left-button event");
        assertTrue(e.isControlDown(), "expected control to be down");
        assertTrue(CommandHistoryMouseListener.isMacOsPopupTrigger(e));
    }

    @Test
    void popupTriggerWhenMacAndJava15() {
        System.setProperty("os.name", "Mac OS X");
        System.setProperty("java.specification.version", "1.5");
        assertTrue(CommandHistoryMouseListener.isMacOsPopupTrigger(click(MouseEvent.BUTTON1, true)));
    }

    @Test
    void macMatchIsCaseInsensitiveSubstring() {
        // os.name is lower-cased and searched for the substring "mac", so any
        // spelling that contains it (here "MACINTOSH") must match.
        System.setProperty("os.name", "MACINTOSH");
        System.setProperty("java.specification.version", "1.6");
        assertTrue(CommandHistoryMouseListener.isMacOsPopupTrigger(click(MouseEvent.BUTTON1, true)));
    }

    @Test
    void noPopupTriggerOnModernJavaEvenOnMac() {
        // Defect characterization: the predicate can only ever fire under Java
        // 1.5/1.6, so on every currently-supported JVM it is effectively dead
        // code and returns false even when every mouse condition is satisfied.
        System.setProperty("os.name", "Mac OS X");
        System.setProperty("java.specification.version", "25");
        assertFalse(CommandHistoryMouseListener.isMacOsPopupTrigger(click(MouseEvent.BUTTON1, true)));
    }

    @Test
    void noPopupTriggerWhenNotMac() {
        System.setProperty("os.name", "Linux");
        System.setProperty("java.specification.version", "1.6");
        assertFalse(CommandHistoryMouseListener.isMacOsPopupTrigger(click(MouseEvent.BUTTON1, true)));
    }

    @Test
    void noPopupTriggerWithoutControl() {
        System.setProperty("os.name", "Mac OS X");
        System.setProperty("java.specification.version", "1.6");
        MouseEvent e = click(MouseEvent.BUTTON1, false);
        assertFalse(e.isControlDown(), "control must not be down for this case");
        assertFalse(CommandHistoryMouseListener.isMacOsPopupTrigger(e));
    }

    @Test
    void noPopupTriggerForRightButton() {
        System.setProperty("os.name", "Mac OS X");
        System.setProperty("java.specification.version", "1.6");
        MouseEvent e = click(MouseEvent.BUTTON3, true);
        assertFalse(SwingUtilities.isLeftMouseButton(e), "a right click is not a left click");
        assertFalse(CommandHistoryMouseListener.isMacOsPopupTrigger(e));
    }

    @Test
    void emptyCallbacksDoNotThrow() {
        CommandHistoryMouseListener l = new CommandHistoryMouseListener();
        MouseEvent e = click(MouseEvent.BUTTON1, false);
        assertDoesNotThrow(() -> {
            l.mouseEntered(e);
            l.mouseExited(e);
            l.mousePressed(e);
            l.mouseReleased(e);
        });
    }

    @Test
    void mouseClickedWithMiddleButtonTakesNoBranch() {
        // A single middle-button click matches neither the popup branch nor the
        // double-left-click branch of mouseClicked, so it must be a pure no-op:
        // it never reaches CommandHistory's tree or the action callbacks and so
        // never touches native or GUI state.
        CommandHistoryMouseListener l = new CommandHistoryMouseListener();
        MouseEvent e = click(MouseEvent.BUTTON2, false);
        assertDoesNotThrow(() -> l.mouseClicked(e));
    }
}
