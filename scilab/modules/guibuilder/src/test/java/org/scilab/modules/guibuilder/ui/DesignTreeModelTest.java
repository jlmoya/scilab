/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 *
 */

package org.scilab.modules.guibuilder.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.scilab.modules.guibuilder.model.Design;
import org.scilab.modules.guibuilder.model.Node;
import org.scilab.modules.guibuilder.model.UnmodelledRegion;
import org.scilab.modules.guibuilder.parse.ScilabGuiParser;

import org.junit.jupiter.api.Test;

public class DesignTreeModelTest {

    private static final String SRC = ""
        + "f = figure(\"tag\", \"fig\");\n"
        + "ok = uicontrol(f, \"style\", \"pushbutton\", \"tag\", \"ok\", \"string\", \"OK\");\n"
        + "for k = 1:3\n"
        + "  uicontrol(f, \"style\", \"text\", \"tag\", \"t\" + string(k));\n"
        + "end\n";

    @Test
    public void theRootIsTheFigureAndWidgetsHangBeneathIt() {
        Design d = ScilabGuiParser.parse(SRC);
        DesignTreeModel m = new DesignTreeModel(d);
        assertEquals(d.root(), m.getRoot());
        assertTrue(m.getChildCount(d.root()) >= 1);
    }

    @Test
    public void lockedRegionsAppearInTheTreeSoTheyCannotBeMissed() {
        // The spec requires unmodelled code to be visible. If it is only in a
        // side panel it will be ignored; the tree is where users look.
        Design d = ScilabGuiParser.parse(SRC);
        DesignTreeModel m = new DesignTreeModel(d);
        assertTrue(m.lockedNodeCount() >= 1, "the loop should surface as a locked entry");
    }

    @Test
    public void everyLockedEntryCanExplainItself() {
        Design d = ScilabGuiParser.parse(SRC);
        DesignTreeModel m = new DesignTreeModel(d);
        // Without this the loop below is vacuous: a lockedReasons() that
        // returned an empty list -- having lost every reason -- would satisfy
        // "every element is non-blank" perfectly.
        assertFalse(m.lockedReasons().isEmpty(),
                    "SRC builds a widget in a loop, so there is something to explain");
        for (String reason : m.lockedReasons()) {
            assertTrue(reason != null && !reason.isBlank());
        }
    }

    // ------------------------------------------------------------------
    // Gaps beyond the brief's three tests.
    // ------------------------------------------------------------------

    /**
     * The property the other tests do not check directly: every unmodelled
     * region is not just counted, but actually reachable by walking the tree
     * from the root the way a {@code JTree} would. A model that counted
     * regions correctly but attached them to a frame nothing ever expands
     * into would still pass the two tests above while failing the actual
     * design requirement.
     */
    @Test
    public void everyUnmodelledRegionIsReachableByWalkingTheTreeFromTheRoot() {
        Design d = ScilabGuiParser.parse(SRC);
        DesignTreeModel m = new DesignTreeModel(d);

        List<Object> reachable = new ArrayList<>();
        Deque<Object> queue = new ArrayDeque<>();
        queue.add(m.getRoot());
        while (!queue.isEmpty()) {
            Object node = queue.remove();
            reachable.add(node);
            for (int i = 0; i < m.getChildCount(node); i++) {
                queue.add(m.getChild(node, i));
            }
        }

        long regionsSeen = reachable.stream().filter(o -> o instanceof UnmodelledRegion).count();
        assertEquals(d.unmodelled().size(), regionsSeen,
                     "every UnmodelledRegion must be reachable from the root, not just counted");
    }

    @Test
    public void rootChildrenAreItsModelledWidgetsFollowedByItsOwnedRegions() {
        Design d = ScilabGuiParser.parse(SRC);
        DesignTreeModel m = new DesignTreeModel(d);

        int childCount = m.getChildCount(d.root());
        int widgetCount = d.root().children().size();
        assertTrue(childCount >= widgetCount,
                   "the root must show at least its own modelled widgets");

        for (int i = 0; i < widgetCount; i++) {
            assertEquals(d.root().children().get(i), m.getChild(d.root(), i),
                         "modelled widgets must come first, in source order");
        }
        for (int i = widgetCount; i < childCount; i++) {
            assertTrue(m.getChild(d.root(), i) instanceof UnmodelledRegion,
                       "anything after the modelled widgets must be a locked region");
        }
    }

    @Test
    public void getIndexOfChildRoundTripsWithGetChildForEveryChildOfTheRoot() {
        Design d = ScilabGuiParser.parse(SRC);
        DesignTreeModel m = new DesignTreeModel(d);

        for (int i = 0; i < m.getChildCount(d.root()); i++) {
            Object child = m.getChild(d.root(), i);
            assertEquals(i, m.getIndexOfChild(d.root(), child));
        }
    }

    @Test
    public void aPlainWidgetAndAnUnmodelledRegionAreBothLeaves() {
        Design d = ScilabGuiParser.parse(SRC);
        DesignTreeModel m = new DesignTreeModel(d);

        Node widget = d.byTag("ok");
        assertNotNull(widget, "the corpus source must have produced the \"ok\" pushbutton");
        assertTrue(m.isLeaf(widget), "a plain uicontrol has no children of its own");

        boolean sawRegionLeaf = false;
        for (int i = 0; i < m.getChildCount(d.root()); i++) {
            Object child = m.getChild(d.root(), i);
            if (child instanceof UnmodelledRegion) {
                assertTrue(m.isLeaf(child), "an unmodelled region is never a container");
                sawRegionLeaf = true;
            }
        }
        assertTrue(sawRegionLeaf, "expected at least one unmodelled region among the root's children");
    }

    @Test
    public void theRootItselfIsNotALeafWhenItHasChildren() {
        Design d = ScilabGuiParser.parse(SRC);
        DesignTreeModel m = new DesignTreeModel(d);
        assertFalse(m.isLeaf(d.root()));
    }

    @Test
    public void anObjectThatIsNotInTheDesignHasNoPlaceInTheTree() {
        Design d = ScilabGuiParser.parse(SRC);
        DesignTreeModel m = new DesignTreeModel(d);
        Object stranger = new Object();

        assertEquals(0, m.getChildCount(stranger));
        assertTrue(m.isLeaf(stranger));
        assertEquals(-1, m.getIndexOfChild(stranger, d.root()));
        assertEquals(-1, m.getIndexOfChild(d.root(), stranger));
    }

    @Test
    public void anEmptyDesignHasAnEmptyLeafRootAndNothingLocked() {
        Design d = ScilabGuiParser.parse("");
        DesignTreeModel m = new DesignTreeModel(d);

        assertEquals(0, m.getChildCount(d.root()));
        assertTrue(m.isLeaf(d.root()));
        assertEquals(0, m.lockedNodeCount());
        assertTrue(m.lockedReasons().isEmpty());
    }

    @Test
    public void treeModelListenerMethodsAreSafeNoOpsInPhaseOne() {
        Design d = ScilabGuiParser.parse(SRC);
        DesignTreeModel m = new DesignTreeModel(d);

        assertDoesNotThrow(() -> {
            m.addTreeModelListener(null);
            m.removeTreeModelListener(null);
            m.valueForPathChanged(null, null);
        });
    }
}
