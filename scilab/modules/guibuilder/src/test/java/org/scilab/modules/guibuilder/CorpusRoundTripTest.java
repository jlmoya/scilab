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
import org.scilab.modules.guibuilder.model.Node;
import org.scilab.modules.guibuilder.model.UnmodelledRegion;
import org.scilab.modules.guibuilder.model.WidgetStyle;
import org.scilab.modules.guibuilder.parse.ScilabGuiParser;
import org.scilab.modules.guibuilder.write.DesignWriter;
import org.scilab.modules.guibuilder.write.SourceDocument;
import org.scilab.modules.guibuilder.write.SourceValidator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Proves Tasks 1-5 against whole files rather than hand-picked lines: one
 * matching the shape the predecessor ATOMS {@code guibuilder} generator
 * emits, and two written the way people write Scilab GUIs by hand.
 *
 * <p><b>Provenance, stated rather than implied.</b> All three fixtures are
 * hand-written for this suite. {@code handwritten-simple.sce} was originally
 * a verbatim copy of {@code lsf_toolbox/macros/leastsqr.sci}, which is
 * GPL-3.0; Scilab is GPL v2.0 (not "or later") and the two are incompatible
 * for redistribution, so it was replaced by an original file measured to
 * produce the same parse shape -- 28 widgets, 6 unmodelled regions, 2
 * widgets with a locked property, a 12-property figure, CRLF throughout.
 * {@code atoms-generated.sce} matches what {@code guigencode.sci} emits,
 * read out of that generator rather than captured from a run.
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

    /**
     * The hand-written file has to come back as widgets, for the same reason
     * the generated one does: preserving a file we understood nothing about
     * is easy and useless, and the byte-identical test alone cannot tell the
     * two apart. This is also what stops a future replacement of this fixture
     * degrading it quietly -- the round-trip test would stay green over an
     * empty design.
     */
    @Test
    public void theHandwrittenFileIsUnderstoodNotJustPreserved() throws Exception {
        String src = read("handwritten-simple.sce");
        assertTrue(src.contains("\r\n"),
                   "this is the corpus's CRLF fixture; if that is gone, .gitattributes stopped working");
        Design d = ScilabGuiParser.parse(src);
        assertTrue(d.allNodes().size() >= 20,
                   "a function-wrapped hand-written GUI must open as widgets, got " + d.allNodes().size());
        assertTrue(d.allNodes().stream().anyMatch(n -> n.style() == WidgetStyle.FRAME),
                   "its frames must be modelled as frames");
        assertTrue(d.allNodes().stream().anyMatch(Node::isLocked),
                   "and its computed values must lock rather than be guessed at");
    }

    /**
     * The dynamic file exists to exercise the locking contract, so the
     * assertion names what must lock. The previous version accepted "any
     * region exists anywhere", which every corpus file satisfies for
     * unrelated reasons -- a comment block, a stray assignment -- so it could
     * not have failed if both locks had been lost.
     */
    @Test
    public void theDynamicFileLocksRatherThanFailing() throws Exception {
        String src = read("handwritten-dynamic.sce");
        Design d = ScilabGuiParser.parse(src);

        int inLoop = src.indexOf("uicontrol", src.indexOf("for i = 1:size"));
        assertTrue(inLoop > 0, "fixture changed: it no longer builds a widget inside a loop");
        assertTrue(d.unmodelled().stream().anyMatch(r -> r.range().contains(inLoop)),
                   "the uicontrol at " + inLoop + " runs once per shortcut, so it must be inside a "
                   + "region -- the writer refuses an edit only when it OVERLAPS one");

        Node status = d.byTag("status");
        assertNotNull(status, "the status label is an ordinary widget and must be modelled");
        assertTrue(status.properties().get("position").isLocked(),
                   "its position is computed from statusY, so that property must be locked");
        assertFalse(status.properties().get("string").isLocked(),
                    "and locking one property must not lock the rest");
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
