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

import org.scilab.modules.guibuilder.model.Design;
import org.scilab.modules.guibuilder.model.UnmodelledRegion;
import org.scilab.modules.guibuilder.parse.ScilabGuiParser;
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
     * {@link CoverageInvariant} is also exercised against from
     * {@code ScilabGuiParserTest}: the union of every modelled node's source
     * range and every unmodelled region accounts for every significant token
     * in the file. See {@link CoverageInvariant} for the two exemptions and
     * the reasoning behind them -- it is the single implementation both this
     * test and {@code ScilabGuiParserTest} call, so the exemptions cannot
     * drift out of sync between the synthetic corners and these real files.
     */
    @ParameterizedTest
    @ValueSource(strings = {"atoms-generated.sce", "handwritten-simple.sce", "handwritten-dynamic.sce"})
    public void everyCorpusFileIsFullyAccountedForByNodesAndRegions(String name) throws Exception {
        CoverageInvariant.assertNothingIsUnaccountedFor(name, read(name));
    }
}
