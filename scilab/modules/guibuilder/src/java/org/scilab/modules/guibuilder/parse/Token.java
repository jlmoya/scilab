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

package org.scilab.modules.guibuilder.parse;

import org.scilab.modules.guibuilder.model.SourceRange;

/** One lexical token, carrying the exact span it occupies in the source. */
public final class Token {

    public enum Type { IDENTIFIER, STRING, NUMBER, OPERATOR, PUNCTUATION, COMMENT, WHITESPACE, EOF }

    private final Type type;
    private final String text;
    private final SourceRange range;

    public Token(Type type, String text, SourceRange range) {
        this.type = type;
        this.text = text;
        this.range = range;
    }

    public Type type() {
        return type;
    }

    public String text() {
        return text;
    }

    public SourceRange range() {
        return range;
    }

    @Override
    public String toString() {
        return type + "(" + text + ")" + range;
    }
}
