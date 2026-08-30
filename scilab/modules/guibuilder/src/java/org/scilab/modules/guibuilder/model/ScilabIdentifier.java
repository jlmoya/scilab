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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates a widget tag.
 *
 * A tag is used twice in generated code: as a struct field (handles.okButton)
 * and in the widget's own "tag" property. Deliberately stricter than Scilab's
 * full identifier grammar -- Scilab tolerates % and # in some positions, but a
 * tag that needs explaining is a tag that will confuse someone later.
 */
public final class ScilabIdentifier {

    private static final Pattern SHAPE = Pattern.compile("[A-Za-z][A-Za-z0-9_]*");

    private static final Set<String> KEYWORDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "abort", "break", "case", "catch", "continue", "do", "else", "elseif",
        "end", "endfunction", "for", "function", "global", "if", "otherwise",
        "pause", "quit", "return", "select", "then", "try", "while")));

    private ScilabIdentifier() {
    }

    public static boolean isValid(String name) {
        return name != null && SHAPE.matcher(name).matches() && !KEYWORDS.contains(name);
    }

    public static void requireValid(String name) {
        if (!isValid(name)) {
            throw new IllegalArgumentException("not a usable Scilab tag: " + name);
        }
    }
}
