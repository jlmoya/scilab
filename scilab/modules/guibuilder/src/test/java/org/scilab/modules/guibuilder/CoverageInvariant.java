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

package org.scilab.modules.guibuilder;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;

import org.scilab.modules.guibuilder.model.Design;
import org.scilab.modules.guibuilder.model.Node;
import org.scilab.modules.guibuilder.model.UnmodelledRegion;
import org.scilab.modules.guibuilder.parse.ScilabGuiParser;
import org.scilab.modules.guibuilder.parse.ScilabTokenStream;
import org.scilab.modules.guibuilder.parse.Token;

/**
 * The coverage invariant, shared by {@code ScilabGuiParserTest} (synthetic,
 * hand-picked corners) and {@code CorpusRoundTripTest} (real files nobody
 * wrote for either suite): the union of every modelled node's source range
 * (the root included) and every unmodelled region accounts for every
 * significant token in the source. Task 5's writer refuses an edit only when
 * it overlaps a region it knows about, so a span inside neither a node nor a
 * region is a span it would happily overwrite -- this is the check that
 * stands between "the parser silently dropped something" and "the writer
 * corrupted a file it had no business touching."
 *
 * <p>Two exemptions, both narrow and both principled. A {@code ;} or
 * {@code ,} at top-level bracket depth that directly follows a call we
 * modelled terminates the statement that widget is, and the parser absorbs
 * it deliberately; a separator anywhere else -- inside an enclosing call's
 * argument list, or between matrix rows -- carries meaning of its own and is
 * not exempt. And a line continuation plus the rest of its line is ignored,
 * because Scilab itself ignores it.
 *
 * <p>Both rules are recomputed here from the token stream rather than asked
 * of the parser, so this stays an independent statement of the contract
 * rather than a mirror of whatever the parser happens to do -- a real defect
 * in the parser's own bookkeeping would still be caught.
 *
 * <p>This used to be two ~55-line copies, one per test class, that had to be
 * kept in sync by hand. There is exactly one algorithm here now; the two
 * overloads below differ only in how they describe a failure.
 */
public final class CoverageInvariant {

    private CoverageInvariant() {
    }

    /**
     * Used against synthetic, hand-picked input, where dumping the (short)
     * source itself into the failure message is more useful than a label.
     */
    public static void assertNothingIsUnaccountedFor(String src) {
        check(src, (t, d) -> "nothing accounts for " + t + " in <" + src + ">:" + reasons(d));
    }

    /**
     * Used against real corpus files, where the file name is a far more
     * useful failure label than dumping the whole file into the message.
     */
    public static void assertNothingIsUnaccountedFor(String label, String src) {
        check(src, (t, d) -> label + ": nothing accounts for " + t);
    }

    private static void check(String src, BiFunction<Token, Design, String> messageFor) {
        Design d = ScilabGuiParser.parse(src);
        Set<Integer> modelledEnds = new HashSet<>();
        modelledEnds.add(d.root().sourceRange().end());
        for (Node n : d.allNodes()) {
            modelledEnds.add(n.sourceRange().end());
        }

        List<Token> tokens = ScilabTokenStream.tokenize(src);
        int depth = 0;
        int previousEnd = -1;
        boolean ignoringContinuedLine = false;
        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            if (t.type() == Token.Type.EOF) {
                break;
            }
            if (ignoringContinuedLine) {
                ignoringContinuedLine = t.text().indexOf('\n') < 0 && t.text().indexOf('\r') < 0;
                continue;
            }
            if (t.type() == Token.Type.WHITESPACE || t.type() == Token.Type.COMMENT) {
                continue;
            }
            if (startsContinuation(tokens, i)) {
                ignoringContinuedLine = true;
                continue;
            }
            boolean separator = t.type() == Token.Type.PUNCTUATION
                                 && (";".equals(t.text()) || ",".equals(t.text()));
            if (!(separator && depth == 0 && modelledEnds.contains(previousEnd))) {
                boolean accounted = d.root().sourceRange().overlaps(t.range());
                for (Node n : d.allNodes()) {
                    accounted = accounted || n.sourceRange().overlaps(t.range());
                }
                for (UnmodelledRegion r : d.unmodelled()) {
                    accounted = accounted || r.range().overlaps(t.range());
                }
                assertTrue(accounted, messageFor.apply(t, d));
            }
            if (t.type() == Token.Type.PUNCTUATION) {
                if ("([{".contains(t.text())) {
                    depth++;
                } else if (")]}".contains(t.text())) {
                    depth = Math.max(0, depth - 1);
                }
            }
            previousEnd = t.range().end();
        }
    }

    /** Two or more adjacent "." operators: Scilab's line continuation. */
    private static boolean startsContinuation(List<Token> tokens, int i) {
        Token dot = tokens.get(i);
        if (dot.type() != Token.Type.OPERATOR || !".".equals(dot.text())) {
            return false;
        }
        Token next = i + 1 < tokens.size() ? tokens.get(i + 1) : null;
        return next != null && next.type() == Token.Type.OPERATOR && ".".equals(next.text())
               && next.range().start() == dot.range().end();
    }

    private static String reasons(Design d) {
        StringBuilder sb = new StringBuilder();
        for (UnmodelledRegion r : d.unmodelled()) {
            sb.append("\n  ").append(r.range()).append(' ').append(r.reason());
        }
        return sb.length() == 0 ? " (none)" : sb.toString();
    }
}
