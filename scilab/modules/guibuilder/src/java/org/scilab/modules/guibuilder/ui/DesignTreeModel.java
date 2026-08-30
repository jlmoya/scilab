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

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

import org.scilab.modules.guibuilder.model.Design;
import org.scilab.modules.guibuilder.model.Frame;
import org.scilab.modules.guibuilder.model.Node;
import org.scilab.modules.guibuilder.model.PropertyValue;
import org.scilab.modules.guibuilder.model.SourceRange;
import org.scilab.modules.guibuilder.model.UnmodelledRegion;

/**
 * A read-only {@link TreeModel} over a {@link Design}.
 *
 * <p>The root is the design's own root {@link Frame}. A frame's children are
 * its modelled {@link Node}s -- in the order {@link Frame#children()} already
 * gives them, which is source order -- followed by every
 * {@link UnmodelledRegion} this model attaches to that frame (see
 * {@link #owningFrame}). Every other tree entry, a plain {@code Node} or an
 * {@code UnmodelledRegion}, is a leaf.
 *
 * <p>Putting unmodelled regions in the tree itself, rather than only in a
 * side panel, is the point of this class as much as the traversal is: a
 * locked span the user cannot see in the same view as the widgets is a span
 * they will not know to look for, which defeats the whole degradation
 * contract {@code DesignWriter} depends on (it refuses edits that touch one).
 *
 * <p>The model is built once, from an immutable {@link Design}, and never
 * changes afterwards -- phase 1 has no editing yet, so there is nothing that
 * would invalidate it.
 */
public final class DesignTreeModel implements TreeModel {

    private final Design design;

    /** Every frame's tree children, computed once in the constructor. */
    private final Map<Frame, List<Object>> childrenByFrame = new IdentityHashMap<>();

    public DesignTreeModel(Design design) {
        this.design = Objects.requireNonNull(design, "design must not be null");

        List<Frame> frames = new ArrayList<>();
        frames.add(design.root());
        for (Node node : design.allNodes()) {
            if (node instanceof Frame) {
                frames.add((Frame) node);
            }
        }

        Map<UnmodelledRegion, Frame> owner = new IdentityHashMap<>();
        for (UnmodelledRegion region : design.unmodelled()) {
            owner.put(region, owningFrame(region, frames));
        }

        for (Frame frame : frames) {
            List<Object> kids = new ArrayList<>(frame.children());
            for (UnmodelledRegion region : design.unmodelled()) {
                if (owner.get(region) == frame) {
                    kids.add(region);
                }
            }
            childrenByFrame.put(frame, kids);
        }
    }

    /**
     * The frame {@code region} nests under in the tree: the smallest frame
     * whose own {@link Node#sourceRange()} contains it, or the design's root
     * when no frame's range does.
     *
     * <p>Both halves matter. A region strictly inside a frame's own range is
     * real and specific -- an argument the parser could not read, inside a
     * call it otherwise modelled -- and belongs under that widget rather than
     * lost at the top. But a frame's range is only its own call, never the
     * statements around it, so nothing else -- a loop body, a second figure,
     * a gap between statements -- is ever "inside" any frame's range at all.
     * Those regions fall back to the root, which is what makes them
     * reachable in the tree at all: a loop body carried through unchanged is
     * exactly this common case, and it is the root that ends up showing it.
     */
    private Frame owningFrame(UnmodelledRegion region, List<Frame> frames) {
        Frame best = design.root();
        int bestLength = Integer.MAX_VALUE;
        for (Frame frame : frames) {
            SourceRange range = frame.sourceRange();
            if (contains(range, region.range()) && range.length() < bestLength) {
                best = frame;
                bestLength = range.length();
            }
        }
        return best;
    }

    private static boolean contains(SourceRange outer, SourceRange inner) {
        return inner.start() >= outer.start() && inner.end() <= outer.end();
    }

    @Override
    public Object getRoot() {
        return design.root();
    }

    @Override
    public int getChildCount(Object parent) {
        List<Object> kids = childrenByFrame.get(parent);
        return kids == null ? 0 : kids.size();
    }

    /** True for anything that is not a {@link Frame}, or a {@link Frame} with nothing beneath it. */
    @Override
    public boolean isLeaf(Object node) {
        return getChildCount(node) == 0;
    }

    @Override
    public Object getChild(Object parent, int index) {
        List<Object> kids = childrenByFrame.get(parent);
        return kids == null ? null : kids.get(index);
    }

    @Override
    public int getIndexOfChild(Object parent, Object child) {
        List<Object> kids = childrenByFrame.get(parent);
        return kids == null ? -1 : kids.indexOf(child);
    }

    /**
     * Nothing here is ever edited from the tree in phase 1 -- there is no
     * inline editing, only the read-only properties table and Save -- so a
     * value arriving from a {@code JTree} in-place editor has nowhere to go.
     */
    @Override
    public void valueForPathChanged(TreePath path, Object newValue) {
    }

    /**
     * No-op. A {@code Design} is parsed once and never mutated afterwards in
     * phase 1, so this model can never fire a change a listener would need
     * to hear about. Registering (and forgetting) the listener would be
     * dead code pretending otherwise; a future phase that does mutate the
     * design in place is the one that should add the listener list and
     * start firing into it.
     */
    @Override
    public void addTreeModelListener(TreeModelListener l) {
    }

    @Override
    public void removeTreeModelListener(TreeModelListener l) {
    }

    /** How many locked properties and unmodelled regions this design has. */
    public int lockedNodeCount() {
        return lockedReasons().size();
    }

    /**
     * Why each locked property and each unmodelled region is locked, one
     * entry per property or region, in no particular order.
     */
    public List<String> lockedReasons() {
        List<String> reasons = new ArrayList<>();
        collectLockedPropertyReasons(design.root(), reasons);
        for (Node node : design.allNodes()) {
            collectLockedPropertyReasons(node, reasons);
        }
        for (UnmodelledRegion region : design.unmodelled()) {
            reasons.add(region.reason());
        }
        return reasons;
    }

    private static void collectLockedPropertyReasons(Node node, List<String> out) {
        for (PropertyValue value : node.properties().values()) {
            if (value.isLocked()) {
                out.add(value.reason());
            }
        }
    }
}
