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
        ordered.sort(Comparator.comparingInt(e -> e.range.start()));
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
