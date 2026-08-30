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
 * One property of a widget, together with the exact source text it came from.
 *
 * COMPUTED means the parser could see the property but not its value -- the
 * value is an expression, a variable, a call. Such a property is LOCKED: it is
 * displayed, carried through untouched, and refused as an edit target. Locking
 * one property never locks the others.
 */
public final class PropertyValue {

    public enum Kind { LITERAL, COMPUTED }

    private final Kind kind;
    private final String sourceText;
    private final SourceRange range;
    private final Object value;
    private final String reason;

    private PropertyValue(Kind kind, String sourceText, SourceRange range, Object value, String reason) {
        this.kind = kind;
        this.sourceText = sourceText;
        this.range = range;
        this.value = value;
        this.reason = reason;
    }

    public static PropertyValue literal(String sourceText, SourceRange range, Object value) {
        return new PropertyValue(Kind.LITERAL, sourceText, range, value, null);
    }

    public static PropertyValue computed(String sourceText, SourceRange range, String reason) {
        return new PropertyValue(Kind.COMPUTED, sourceText, range, null, reason);
    }

    public Kind kind() {
        return kind;
    }

    public String sourceText() {
        return sourceText;
    }

    public SourceRange range() {
        return range;
    }

    /** The parsed value, or null when this property is computed. */
    public Object value() {
        return value;
    }

    /** Why this property is locked, or null when it is not. */
    public String reason() {
        return reason;
    }

    public boolean isLocked() {
        return kind == Kind.COMPUTED;
    }
}
