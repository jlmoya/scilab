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

package org.scilab.modules.guibuilder.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** One .sce file, as far as we understand it. */
public final class Design {

    private final String source;
    private final Frame root;
    private final List<UnmodelledRegion> unmodelled = new ArrayList<>();
    private final Map<String, Node> byTag = new HashMap<>();

    public Design(String source, Frame root) {
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
        if (root == null) {
            throw new IllegalArgumentException("root must not be null");
        }
        this.source = source;
        this.root = root;
        byTag.put(root.tag(), root);
    }

    public String source() {
        return source;
    }

    public Frame root() {
        return root;
    }

    public void add(Frame parent, Node child) {
        ScilabIdentifier.requireValid(child.tag());
        if (byTag.containsKey(child.tag())) {
            throw new IllegalArgumentException("duplicate tag: " + child.tag());
        }
        parent.addChild(child);
        byTag.put(child.tag(), child);
    }

    public Node byTag(String tag) {
        return byTag.get(tag);
    }

    public void addUnmodelled(UnmodelledRegion region) {
        unmodelled.add(region);
        unmodelled.sort(Comparator.comparingInt(r -> r.range().start()));
    }

    /** In source order, which is the order the tab lists them. */
    public List<UnmodelledRegion> unmodelled() {
        return Collections.unmodifiableList(unmodelled);
    }

    /** Every node except the root, depth-first. */
    public List<Node> allNodes() {
        List<Node> out = new ArrayList<>();
        collect(root, out);
        return out;
    }

    private void collect(Frame frame, List<Node> out) {
        for (Node child : frame.children()) {
            out.add(child);
            if (child instanceof Frame) {
                collect((Frame) child, out);
            }
        }
    }
}
