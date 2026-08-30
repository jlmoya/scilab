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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.scilab.modules.guibuilder.model.Design;
import org.scilab.modules.guibuilder.model.Node;
import org.scilab.modules.guibuilder.model.UnmodelledRegion;
import org.scilab.modules.guibuilder.parse.ScilabGuiParser;
import org.scilab.modules.guibuilder.parse.ScilabTokenStream;
import org.scilab.modules.guibuilder.parse.Token;
import org.scilab.modules.guibuilder.write.DesignWriter;
import org.scilab.modules.guibuilder.write.SourceDocument;
import org.scilab.modules.guibuilder.write.SourceValidator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Proves Tasks 1-5 against files nobody wrote for these tests: one file the
 * predecessor ATOMS {@code guibuilder} shape would have produced, and two
 * real hand-written GUI scripts found in the toolbox tree.
 */
public class CorpusRoundTripTest {

    private static final SourceValidator ALWAYS_VALID = source -> true;

    private static String read(String name) throws IOException {
        try (InputStream in = CorpusRoundTripTest.class.getResourceAsStream("/corpus/" + name)) {
            assertNotNull(in, "corpus file missing: " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"atoms-generated.sce", "handwritten-simple.sce", "handwritten-dynamic.sce"})
    public void openingAndSavingAnyCorpusFileIsByteIdentical(String name) throws Exception {
        String src = read(name);
        Design d = ScilabGuiParser.parse(src);
        assertEquals(src, DesignWriter.write(d, new SourceDocument(src), ALWAYS_VALID),
                     name + " was disturbed by a no-op save");
    }

    @ParameterizedTest
    @ValueSource(strings = {"atoms-generated.sce", "handwritten-simple.sce", "handwritten-dynamic.sce"})
    public void everyUnmodelledRegionCarriesAReasonFitToShowAUser(String name) throws Exception {
        for (UnmodelledRegion r : ScilabGuiParser.parse(read(name)).unmodelled()) {
            assertFalse(r.reason().isBlank(), name + " has an unmodelled region with no reason");
        }
    }

    @Test
    public void theAtomsGeneratedFileIsUnderstoodNotJustPreserved() throws Exception {
        // Preserving a file we understood nothing about is easy and useless.
        // Its predecessor's own output must actually come back as widgets.
        Design d = ScilabGuiParser.parse(read("atoms-generated.sce"));
        assertTrue(d.allNodes().size() >= 3,
                   "expected at least the three controls the file creates, got " + d.allNodes().size());
    }

    @Test
    public void theDynamicFileLocksRatherThanFailing() throws Exception {
        Design d = ScilabGuiParser.parse(read("handwritten-dynamic.sce"));
        boolean somethingLocked = !d.unmodelled().isEmpty()
            || d.allNodes().stream().anyMatch(n -> n.isLocked());
        assertTrue(somethingLocked, "the dynamic corpus file should exercise the locking contract");
    }

    /**
     * The coverage invariant, proved here against files nobody wrote for this
     * suite rather than only against the synthetic corners
     * {@code ScilabGuiParserTest.assertNothingIsUnaccountedFor} already
     * covers: the union of every modelled node's source range (the root
     * included) and every unmodelled region accounts for every significant
     * token in the file. Task 5's writer refuses an edit only when it
     * overlaps a region it knows about, so a span inside neither a node nor a
     * region is a span it would happily overwrite -- this is the check that
     * stands between "the parser silently dropped something" and "the writer
     * corrupted a file it had no business touching."
     *
     * <p>Two narrow, principled exemptions, recomputed independently from the
     * token stream rather than asked of the parser -- the same two
     * {@code ScilabGuiParserTest} already establishes against hand-picked
     * input, applied here to real files instead. A {@code ;} or {@code ,} at
     * bracket depth 0 that directly follows a modelled node's own range is
     * the statement terminator the parser deliberately absorbs rather than
     * report as a stray region: without the exemption, every modelled line in
     * every one of these real files would leave its own separator behind as
     * "code we do not model". And a line continuation ({@code ..}/{@code ...})
     * plus the rest of its line is ignored, because Scilab itself ignores it.
     */
    @ParameterizedTest
    @ValueSource(strings = {"atoms-generated.sce", "handwritten-simple.sce", "handwritten-dynamic.sce"})
    public void everyCorpusFileIsFullyAccountedForByNodesAndRegions(String name) throws Exception {
        String src = read(name);
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
                assertTrue(accounted, name + ": nothing accounts for " + t);
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
}
