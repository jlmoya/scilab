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
 * A half-open character span [start, end) into a source file.
 *
 * Half-open matters: adjacent spans must not count as overlapping, or edits to
 * two neighbouring properties would be rejected as conflicting.
 */
public final class SourceRange {

    private final int start;
    private final int end;

    public SourceRange(int start, int end) {
        if (start < 0) {
            throw new IllegalArgumentException("start must not be negative: " + start);
        }
        if (end < start) {
            throw new IllegalArgumentException("end (" + end + ") must not precede start (" + start + ")");
        }
        this.start = start;
        this.end = end;
    }

    public int start() {
        return start;
    }

    public int end() {
        return end;
    }

    public int length() {
        return end - start;
    }

    public boolean contains(int offset) {
        return offset >= start && offset < end;
    }

    public boolean overlaps(SourceRange other) {
        return start < other.end && other.start < end;
    }

    @Override
    public String toString() {
        return "[" + start + "," + end + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof SourceRange)) {
            return false;
        }
        SourceRange r = (SourceRange) o;
        return start == r.start && end == r.end;
    }

    @Override
    public int hashCode() {
        return start * 31 + end;
    }
}
