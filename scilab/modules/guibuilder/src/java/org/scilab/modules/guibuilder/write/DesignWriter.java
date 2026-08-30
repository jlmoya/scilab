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

import org.scilab.modules.guibuilder.model.Design;
import org.scilab.modules.guibuilder.model.SourceRange;
import org.scilab.modules.guibuilder.model.UnmodelledRegion;

/**
 * Turns a design plus a set of edits into new source text.
 *
 * Two refusals, both deliberate. An edit that touches an unmodelled region is
 * refused, because those bytes are code we did not understand and have no right
 * to rewrite. And a result that does not parse is refused outright -- leaving
 * the user with a broken file is the one outcome worse than refusing to save.
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
        }

        String rendered = document.render();

        if (!validator.isValidScilab(rendered)) {
            throw new WriteRefusedException(
                "refusing to write: the result does not parse as Scilab, so the file was left unchanged");
        }

        return rendered;
    }
}
