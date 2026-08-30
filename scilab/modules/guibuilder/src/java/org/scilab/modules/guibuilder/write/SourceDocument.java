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
import java.util.Comparator;
import java.util.List;

import org.scilab.modules.guibuilder.model.SourceRange;

/**
 * Records replacements against an original text and renders the result.
 *
 * Everything not explicitly replaced is copied through byte for byte. That is
 * what makes saving a file we only partly understand safe: the parts we did
 * not touch cannot be reformatted, reordered, or lost.
 */
public final class SourceDocument {

    private static final class Edit {
        final SourceRange range;
        final String replacement;

        Edit(SourceRange range, String replacement) {
            this.range = range;
            this.replacement = replacement;
        }
    }

    private final String original;
    private final List<Edit> edits = new ArrayList<>();

    public SourceDocument(String original) {
        if (original == null) {
            throw new IllegalArgumentException("original must not be null");
        }
        this.original = original;
    }

    public String original() {
        return original;
    }

    public List<SourceRange> editedRanges() {
        List<SourceRange> out = new ArrayList<>();
        for (Edit e : edits) {
            out.add(e.range);
        }
        return out;
    }

    public boolean isEmpty() {
        return edits.isEmpty();
    }

    public void replace(SourceRange range, String replacement) {
        if (range.end() > original.length()) {
            throw new IllegalArgumentException("range " + range + " is outside the source");
        }
        for (Edit e : edits) {
            if (e.range.overlaps(range)) {
                throw new IllegalArgumentException("overlapping edits: " + e.range + " and " + range);
            }
        }
        edits.add(new Edit(range, replacement));
    }

    public String render() {
        List<Edit> ordered = new ArrayList<>(edits);
        // Sorting by start() alone leaves same-start edits in an
        // insertion-order tie -- fine when both ends also match, but wrong
        // when one of them is a zero-length insertion point sharing its
        // start with a wider edit: SourceRange(2,5).overlaps(SourceRange(2,2))
        // is false (half-open ranges do not consider a point at their own
        // start an overlap), so both are accepted by replace(), and without
        // this second key an insertion added AFTER the wider edit would sort
        // after it too, driving the cursor backwards. Breaking the tie by
        // end() ascending applies a zero-length insertion at offset N before
        // any wider edit that also starts at N, regardless of which order
        // they were added in -- restoring the order-independence this class
        // otherwise guarantees.
        ordered.sort(Comparator.comparingInt((Edit e) -> e.range.start())
                                .thenComparingInt(e -> e.range.end()));
        StringBuilder out = new StringBuilder(original.length());
        int cursor = 0;
        for (Edit e : ordered) {
            out.append(original, cursor, e.range.start());
            out.append(e.replacement);
            cursor = e.range.end();
        }
        out.append(original, cursor, original.length());
        return out.toString();
    }
}
