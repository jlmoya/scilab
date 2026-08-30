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

package org.scilab.modules.guibuilder.write;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.scilab.modules.guibuilder.model.Design;
import org.scilab.modules.guibuilder.model.Node;
import org.scilab.modules.guibuilder.model.PropertyValue;
import org.scilab.modules.guibuilder.model.SourceRange;
import org.scilab.modules.guibuilder.model.UnmodelledRegion;

/**
 * Turns a design plus a set of edits into new source text.
 *
 * Three refusals, all deliberate. An edit that touches an unmodelled region is
 * refused, because those bytes are code we did not understand and have no right
 * to rewrite. An edit that touches a LOCKED PROPERTY of a modelled widget is
 * refused for the same reason at finer grain: a computed property sits inside a
 * node's own range, so no unmodelled region covers it and the first check alone
 * would let it through -- {@link PropertyValue}'s javadoc has always said such a
 * property is "refused as an edit target", and this is where that is true. And a
 * result that does not parse is refused outright -- leaving the user with a
 * broken file is the one outcome worse than refusing to save.
 */
public final class DesignWriter {

    private DesignWriter() {
    }

    public static String write(Design design, SourceDocument document, SourceValidator validator)
            throws WriteRefusedException {

        for (SourceRange edited : document.editedRanges()) {
            for (UnmodelledRegion region : design.unmodelled()) {
                if (region.range().overlaps(edited)) {
                    throw new WriteRefusedException(
                        "refusing to write: edit at " + edited + " touches a locked region — "
                        + region.reason());
                }
            }
            for (Node node : everyNode(design)) {
                for (Map.Entry<String, PropertyValue> property : node.properties().entrySet()) {
                    PropertyValue value = property.getValue();
                    if (value.isLocked() && value.range() != null && value.range().overlaps(edited)) {
                        throw new WriteRefusedException(
                            "refusing to write: edit at " + edited + " touches the locked property \""
                            + property.getKey() + "\" of " + node.tag() + " — " + value.reason());
                    }
                }
            }
        }

        String rendered = document.render();

        if (!validator.isValidScilab(rendered)) {
            throw new WriteRefusedException(
                "refusing to write: the result does not parse as Scilab, so the file was left unchanged");
        }

        return rendered;
    }

    /**
     * Every widget whose properties can be locked, the root figure included.
     * {@link Design#allNodes()} deliberately excludes the root -- it is the
     * design's frame, not one of its children -- but the root is a {@link
     * Node} with properties of its own, and {@code figure("figure_name",
     * name)} locks one of them exactly as a uicontrol's would be locked.
     * Leaving it out would make the guarantee true for every widget except
     * the one every file has.
     */
    private static List<Node> everyNode(Design design) {
        List<Node> nodes = new ArrayList<>();
        nodes.add(design.root());
        nodes.addAll(design.allNodes());
        return nodes;
    }
}
