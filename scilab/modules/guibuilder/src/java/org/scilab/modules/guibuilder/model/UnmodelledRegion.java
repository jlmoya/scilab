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

/**
 * A span of the file the parser could not model.
 *
 * The reason is shown to the user verbatim, so write it as an explanation, not
 * as a diagnostic code.
 */
public final class UnmodelledRegion {

    private final SourceRange range;
    private final String reason;

    public UnmodelledRegion(SourceRange range, String reason) {
        if (range == null) {
            throw new IllegalArgumentException("range must not be null");
        }
        if (reason == null || reason.isEmpty()) {
            throw new IllegalArgumentException("every unmodelled region must carry a reason");
        }
        this.range = range;
        this.reason = reason;
    }

    public SourceRange range() {
        return range;
    }

    public String reason() {
        return reason;
    }
}
