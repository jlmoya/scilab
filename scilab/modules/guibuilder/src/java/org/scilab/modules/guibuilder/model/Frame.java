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
import java.util.List;

/** A widget that contains other widgets. The figure's content is the root Frame. */
public final class Frame extends Node {

    private final List<Node> children = new ArrayList<>();

    public Frame(String tag, WidgetStyle style, SourceRange sourceRange) {
        super(tag, style, sourceRange);
    }

    public List<Node> children() {
        return Collections.unmodifiableList(children);
    }

    void addChild(Node child) {
        children.add(child);
        child.setParent(this);
    }
}
