/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Scilab
 *
 * Hermetic JUnit 6 unit tests for the gui module.
 * This file is hereby licensed under the terms of the GNU GPL v2.0.
 */

package org.scilab.modules.gui.bridge.groupmanager;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Enumeration;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.AbstractButton;
import javax.swing.JToggleButton;

import org.junit.jupiter.api.Test;

/**
 * Tests {@link GroupManager}, the process-wide singleton that maps a group name
 * to a Swing {@link javax.swing.ButtonGroup} so that Scilab radio/toggle
 * uicontrols behave as one mutually-exclusive family.
 *
 * <p>The manager is a singleton whose backing map is never cleared, so each test
 * mints a fresh, unique group name via {@link #uniqueName()} to stay
 * order-independent. {@link JToggleButton} is a headless-safe concrete
 * {@link AbstractButton} (and, being a {@code ToggleButtonModel}, it routes
 * selection through its group); nothing here needs a display or the Scilab
 * native runtime.</p>
 */
public class GroupManagerTest {

    private static final AtomicInteger COUNTER = new AtomicInteger();

    /** A group name unique across the whole shared-singleton JVM. */
    private static String uniqueName() {
        return "gm_test_group_" + COUNTER.incrementAndGet();
    }

    /** Drains an enumeration and returns how many elements it held. */
    private static int count(Enumeration<?> e) {
        int n = 0;
        while (e.hasMoreElements()) {
            e.nextElement();
            n++;
        }
        return n;
    }

    // ------------------------------------------------------------------
    // Singleton accessor
    // ------------------------------------------------------------------

    @Test
    public void getGroupManagerReturnsNonNullSingleton() {
        GroupManager first = GroupManager.getGroupManager();
        GroupManager second = GroupManager.getGroupManager();
        assertNotNull(first);
        assertSame(first, second, "getGroupManager() must always return the same instance");
    }

    // ------------------------------------------------------------------
    // addToGroup / getGroupElements
    // ------------------------------------------------------------------

    @Test
    public void addToGroupCreatesGroupAndMakesButtonEnumerable() {
        GroupManager gm = GroupManager.getGroupManager();
        String name = uniqueName();
        JToggleButton button = new JToggleButton();

        gm.addToGroup(name, button);

        Enumeration<AbstractButton> elements = gm.getGroupElements(name);
        assertNotNull(elements);
        assertTrue(elements.hasMoreElements());
        assertSame(button, elements.nextElement());
        assertFalse(elements.hasMoreElements(), "group should hold exactly the one button added");
    }

    @Test
    public void addToGroupSupportsMultipleButtons() {
        GroupManager gm = GroupManager.getGroupManager();
        String name = uniqueName();
        gm.addToGroup(name, new JToggleButton());
        gm.addToGroup(name, new JToggleButton());

        assertEquals(2, count(gm.getGroupElements(name)));
    }

    @Test
    public void addingSameButtonTwiceKeepsSingleMembership() {
        // addToGroup first removes the button from every group, so re-adding the
        // same instance to the same group cannot create a duplicate membership.
        GroupManager gm = GroupManager.getGroupManager();
        String name = uniqueName();
        JToggleButton button = new JToggleButton();

        gm.addToGroup(name, button);
        gm.addToGroup(name, button);

        assertEquals(1, count(gm.getGroupElements(name)));
    }

    @Test
    public void addToGroupMovesButtonOutOfItsPreviousGroup() {
        GroupManager gm = GroupManager.getGroupManager();
        String from = uniqueName();
        String to = uniqueName();
        JToggleButton button = new JToggleButton();

        gm.addToGroup(from, button);
        gm.addToGroup(to, button);

        assertEquals(0, count(gm.getGroupElements(from)), "button must leave its old group");
        Enumeration<AbstractButton> moved = gm.getGroupElements(to);
        assertTrue(moved.hasMoreElements());
        assertSame(button, moved.nextElement());
        assertFalse(moved.hasMoreElements());
    }

    // ------------------------------------------------------------------
    // removeFromGroup
    // ------------------------------------------------------------------

    @Test
    public void removeFromGroupRemovesTheButton() {
        GroupManager gm = GroupManager.getGroupManager();
        String name = uniqueName();
        JToggleButton button = new JToggleButton();

        gm.addToGroup(name, button);
        gm.removeFromGroup(button);

        assertEquals(0, count(gm.getGroupElements(name)));
    }

    @Test
    public void removeFromGroupOnlyAffectsTheGivenButton() {
        GroupManager gm = GroupManager.getGroupManager();
        String name = uniqueName();
        JToggleButton keep = new JToggleButton();
        JToggleButton drop = new JToggleButton();
        gm.addToGroup(name, keep);
        gm.addToGroup(name, drop);

        gm.removeFromGroup(drop);

        Enumeration<AbstractButton> remaining = gm.getGroupElements(name);
        assertTrue(remaining.hasMoreElements());
        assertSame(keep, remaining.nextElement());
        assertFalse(remaining.hasMoreElements(), "only the removed button should be gone");
    }

    @Test
    public void removeFromGroupOnUnknownButtonDoesNotThrow() {
        GroupManager gm = GroupManager.getGroupManager();
        // The button was never added to any group; removal must be a no-op.
        assertDoesNotThrow(() -> gm.removeFromGroup(new JToggleButton()));
    }

    // ------------------------------------------------------------------
    // Missing-group lookups have no null guard (defect characterization)
    // ------------------------------------------------------------------

    @Test
    public void getGroupElementsOnUnknownGroupThrowsNPE() {
        // No group was ever created under this name, so buttonGroup.get(..) is
        // null and getElements() is dereferenced on null.
        GroupManager gm = GroupManager.getGroupManager();
        assertThrows(NullPointerException.class, () -> gm.getGroupElements(uniqueName()));
    }

    @Test
    public void setSelectedOnUnknownGroupThrowsNPE() {
        GroupManager gm = GroupManager.getGroupManager();
        JToggleButton button = new JToggleButton();
        assertThrows(NullPointerException.class,
                     () -> gm.setSelected(button.getModel(), uniqueName(), true));
    }

    @Test
    public void isSelectedOnUnknownGroupThrowsNPE() {
        GroupManager gm = GroupManager.getGroupManager();
        assertThrows(NullPointerException.class, () -> gm.isSelected(uniqueName()));
    }

    // ------------------------------------------------------------------
    // setSelected / isSelected
    // ------------------------------------------------------------------

    @Test
    public void setSelectedSelectsGivenButtonAndKeepsGroupExclusive() {
        GroupManager gm = GroupManager.getGroupManager();
        String name = uniqueName();
        JToggleButton first = new JToggleButton();
        JToggleButton second = new JToggleButton();
        gm.addToGroup(name, first);
        gm.addToGroup(name, second);

        assertFalse(first.isSelected(), "precondition: nothing selected yet");
        assertFalse(second.isSelected(), "precondition: nothing selected yet");

        gm.setSelected(first.getModel(), name, true);
        assertTrue(first.isSelected());
        assertFalse(second.isSelected());

        // Selecting the second must deselect the first: a ButtonGroup is exclusive.
        gm.setSelected(second.getModel(), name, true);
        assertFalse(first.isSelected(), "selecting another button clears the previous one");
        assertTrue(second.isSelected());
    }

    @Test
    public void isSelectedAlwaysReturnsTrueForAnExistingGroup() {
        // Defect characterization: isSelected(name) evaluates
        //     group.isSelected(group.getSelection())
        // i.e. it compares the group's current selection to itself, so it returns
        // true unconditionally -- even for a brand-new group in which nothing is
        // selected (null == null). This pins the current, arguably-wrong behavior.
        GroupManager gm = GroupManager.getGroupManager();
        String name = uniqueName();
        JToggleButton button = new JToggleButton();
        gm.addToGroup(name, button);

        assertFalse(button.isSelected(), "precondition: nothing is selected yet");
        assertTrue(gm.isSelected(name), "isSelected currently returns true even with no selection");

        gm.setSelected(button.getModel(), name, true);
        assertTrue(gm.isSelected(name));
    }
}
