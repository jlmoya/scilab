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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

public class ScilabTokenStreamTest {

    @Test
    public void everyTokenRangeIndexesBackIntoTheSource() {
        // This is the property the whole writer depends on. If a token's range
        // does not slice its own text out of the source, every rewrite is wrong.
        String src = "h = uicontrol(f, \"style\", \"pushbutton\"); // make it\n";
        for (Token t : ScilabTokenStream.tokenize(src)) {
            if (t.type() != Token.Type.EOF) {
                assertEquals(t.text(), src.substring(t.range().start(), t.range().end()),
                             "token " + t.type() + " does not slice back to its own text");
            }
        }
    }

    @Test
    public void tokensCoverTheSourceWithNoGaps() {
        // Comments and whitespace are tokens too. A gap would mean bytes the
        // writer cannot account for, and formatting would be lost on save.
        String src = "a = 1;   // note\nb = 2;\n";
        List<Token> tokens = ScilabTokenStream.tokenize(src);
        int cursor = 0;
        for (Token t : tokens) {
            if (t.type() == Token.Type.EOF) {
                continue;
            }
            assertEquals(cursor, t.range().start(), "gap or overlap before " + t.text());
            cursor = t.range().end();
        }
        assertEquals(src.length(), cursor, "tokens do not reach the end of the source");
    }

    @Test
    public void stringsAndCommentsAreRecognisedAsSuch() {
        List<Token> tokens = ScilabTokenStream.tokenize("x = \"hi\"; // done\n");
        assertTrue(tokens.stream().anyMatch(t -> t.type() == Token.Type.STRING && t.text().equals("\"hi\"")));
        assertTrue(tokens.stream().anyMatch(t -> t.type() == Token.Type.COMMENT && t.text().startsWith("//")));
    }

    @Test
    public void anEmptySourceYieldsOnlyEof() {
        List<Token> tokens = ScilabTokenStream.tokenize("");
        assertEquals(1, tokens.size());
        assertEquals(Token.Type.EOF, tokens.get(0).type());
    }
}
